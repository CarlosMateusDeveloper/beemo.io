import json
import os
import tempfile
import threading
import time
import unittest
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts import production_monitor as monitor


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.config = monitor.Config(
            frontend_url="https://app.example.test",
            api_url="https://api.example.test",
            webhook_url="https://alerts.example.test/path",
            owner="plantao-clinisis",
            state_file=Path(self.directory.name) / "state.json",
        )

    def test_failure_deduplicates_and_recovery_notifies(self):
        events = []
        sender = lambda _config, event, finding: events.append((event, finding.code))
        failure = lambda _config: [monitor.Finding("api_unavailable", "critical", "Rota pública indisponível")]
        monitor.monitor_once(self.config, failure, sender)
        monitor.monitor_once(self.config, failure, sender)
        monitor.monitor_once(self.config, lambda _config: [], sender)
        self.assertEqual(events, [("falha", "api_unavailable"), ("recuperacao", "api_unavailable")])
        self.assertEqual(json.loads(self.config.state_file.read_text()), {})

    def test_failed_delivery_is_retried(self):
        failure = lambda _config: [monitor.Finding("api_unavailable", "critical", "Rota pública indisponível")]
        def broken_sender(*_args):
            raise OSError("webhook unavailable")
        with self.assertRaises(OSError):
            monitor.monitor_once(self.config, failure, broken_sender)
        self.assertFalse(self.config.state_file.exists())
        events = []
        monitor.monitor_once(self.config, failure, lambda _config, event, item: events.append((event, item.code)))
        self.assertEqual(events, [("falha", "api_unavailable")])

    def test_alert_reaches_webhook_without_response_body_or_patient_data(self):
        received = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                received.append(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))
                self.send_response(204)
                self.end_headers()
            def log_message(self, *_args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        config = monitor.Config(self.config.frontend_url, self.config.api_url,
                                f"http://127.0.0.1:{server.server_port}/alert", self.config.owner,
                                self.config.state_file)
        monitor.monitor_once(config, lambda _config: [monitor.Finding("api_unavailable", "critical", "Rota pública indisponível")])
        self.assertEqual(received[0]["event"], "falha")
        self.assertEqual(received[0]["owner"], "plantao-clinisis")
        self.assertNotIn("token", json.dumps(received[0]).lower())
        self.assertNotIn("paciente", json.dumps(received[0]).lower())

    def test_configuration_rejects_public_probe_with_query(self):
        values = {
            "MONITOR_FRONTEND_URL": "https://app.example.test/?paciente=1",
            "MONITOR_API_URL": "https://api.example.test",
            "MONITOR_WEBHOOK_URL": "https://alerts.example.test/path",
            "MONITOR_ESCALATION_WEBHOOK_URL": "https://escalation.example.test/path",
            "MONITOR_OWNER": "plantao-clinisis",
            "MONITOR_STATE_FILE": str(self.config.state_file),
        }
        with patch.dict("os.environ", values):
            with self.assertRaises(ValueError):
                monitor.config_from_env()

    def test_public_probe_rejects_html_instead_of_api_json(self):
        class Response:
            status = 200
            url = "https://api.example.test/api/auth/providers"
            headers = {"Content-Type": "text/html"}
            def __enter__(self):
                return self
            def __exit__(self, *_args):
                pass
            def read(self, _limit):
                return b"<html>login</html>"
        with patch.object(monitor, "urlopen", return_value=Response()):
            findings = monitor._http_probe(Response.url, "api", self.config, json_list=True)
        self.assertEqual([item.code for item in findings], ["api_invalid"])

    def test_unresolved_failure_escalates_once_and_closes_both_channels(self):
        config = replace(self.config, escalation_webhook_url="https://escalation.example.test/path",
                         escalation_after_seconds=300)
        failure = lambda _config: [monitor.Finding("api_unavailable", "critical", "Rota pública indisponível")]
        events = []
        sender = lambda cfg, event, item: events.append((cfg.webhook_url, event, item.code))
        with patch.object(monitor.time, "time", return_value=1_000):
            monitor.monitor_once(config, failure, sender)
        with patch.object(monitor.time, "time", return_value=1_301):
            monitor.monitor_once(config, failure, sender)
            monitor.monitor_once(config, failure, sender)
            monitor.monitor_once(config, lambda _config: [], sender)
        self.assertEqual([event for _url, event, _code in events],
                         ["falha", "escalacao", "recuperacao", "recuperacao"])
        self.assertEqual(events[1][0], config.escalation_webhook_url)

    def test_host_metrics_detect_disk_backup_queue_and_integration(self):
        marker = Path(self.directory.name) / "verified-backup"
        marker.touch()
        old = time.time() - 30 * 3600
        os.utime(marker, (old, old))
        config = replace(self.config, disk_path=Path(self.directory.name), backup_marker=marker,
                         compose_file=Path("compose.yml"), compose_env_file=Path(".env"))
        services = "\n".join(json.dumps({"Service": service, "State": "running", "Health": "healthy", "ID": service})
                             for service in ("postgres", "backend", "agenda", "chatbot", "frontend", "proxy"))
        stats = json.dumps({"CPUPerc": "91.0%", "MemPerc": "92.0%"})
        with patch.object(monitor.shutil, "disk_usage", return_value=SimpleNamespace(total=100, used=96)), \
             patch.object(monitor, "_run", side_effect=[services, stats, "3,110,1"]):
            findings, metrics = monitor.collect_host(config)
        self.assertEqual({item.code for item in findings},
                         {"disk_full", "backup_overdue", "container_cpu_high", "container_memory_high",
                          "queue_backlog", "integration_open"})
        self.assertEqual(metrics["postgres_connections"], 3)
        self.assertEqual(metrics["queue_pending"], 110)


if __name__ == "__main__":
    unittest.main()
