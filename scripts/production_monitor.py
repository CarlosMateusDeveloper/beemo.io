"""Public, content-free Clinisis availability probe. Run from outside the app host.

Only fixed public routes are requested. Response bodies, cookies, tokens and patient
data are never included in state, stdout or alert payloads.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Finding:
    code: str
    severity: str
    detail: str


@dataclass(frozen=True)
class Config:
    frontend_url: str
    api_url: str
    webhook_url: str
    owner: str
    state_file: Path
    escalation_webhook_url: str | None = None
    latency_limit_ms: int = 1500
    tls_warn_days: int = 14
    timeout_seconds: float = 5.0
    disk_path: Path | None = None
    backup_marker: Path | None = None
    compose_file: Path | None = None
    compose_env_file: Path | None = None
    disk_warn_percent: int = 85
    disk_critical_percent: int = 95
    backup_warn_hours: int = 20
    backup_critical_hours: int = 26
    escalation_after_seconds: int = 300


def _https_origin(value: str, name: str) -> str:
    parsed = urlsplit(value)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/")):
        raise ValueError(f"{name} deve ser uma origem HTTPS sem caminho, query ou credenciais")
    return value.rstrip("/")


def _webhook_url(value: str, name: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
        raise ValueError(f"{name} deve ser HTTPS sem credenciais na URL")
    return value


def config_from_env() -> Config:
    def required(name: str) -> str:
        value = os.getenv(name, "").strip()
        if not value:
            raise ValueError(f"Defina {name}")
        return value

    webhook = _webhook_url(required("MONITOR_WEBHOOK_URL"), "MONITOR_WEBHOOK_URL")
    escalation = _webhook_url(required("MONITOR_ESCALATION_WEBHOOK_URL"), "MONITOR_ESCALATION_WEBHOOK_URL")
    return Config(
        frontend_url=_https_origin(required("MONITOR_FRONTEND_URL"), "MONITOR_FRONTEND_URL"),
        api_url=_https_origin(required("MONITOR_API_URL"), "MONITOR_API_URL"),
        webhook_url=webhook,
        escalation_webhook_url=escalation,
        owner=required("MONITOR_OWNER"),
        state_file=Path(required("MONITOR_STATE_FILE")),
        latency_limit_ms=int(os.getenv("MONITOR_LATENCY_LIMIT_MS", "1500")),
        tls_warn_days=int(os.getenv("MONITOR_TLS_WARN_DAYS", "14")),
        timeout_seconds=float(os.getenv("MONITOR_TIMEOUT_SECONDS", "5")),
        disk_path=Path(os.environ["MONITOR_DISK_PATH"]) if os.getenv("MONITOR_DISK_PATH") else None,
        backup_marker=Path(os.environ["MONITOR_BACKUP_MARKER"]) if os.getenv("MONITOR_BACKUP_MARKER") else None,
        compose_file=Path(os.environ["MONITOR_COMPOSE_FILE"]) if os.getenv("MONITOR_COMPOSE_FILE") else None,
        compose_env_file=Path(os.environ["MONITOR_COMPOSE_ENV_FILE"]) if os.getenv("MONITOR_COMPOSE_ENV_FILE") else None,
    )


def _http_probe(url: str, code: str, config: Config, json_list: bool = False) -> list[Finding]:
    started = time.monotonic()
    try:
        request = Request(url, headers={"User-Agent": "ClinisisMonitor/1", "Accept": "application/json" if json_list else "text/html"})
        with urlopen(request, timeout=config.timeout_seconds) as response:
            status = response.status
            final = urlsplit(response.url)
            expected = urlsplit(url)
            if ((final.scheme, final.hostname, final.port) != (expected.scheme, expected.hostname, expected.port)
                    or status != 200):
                return [Finding(code + "_unavailable", "critical", f"HTTP {status} ou redirecionamento inesperado")]
            if not json_list and "text/html" not in response.headers.get("Content-Type", "").lower():
                return [Finding(code + "_invalid", "critical", "Frontend não retornou HTML")]
            if json_list:
                try:
                    payload = json.loads(response.read(4096))
                except (ValueError, UnicodeError):
                    return [Finding(code + "_invalid", "critical", "Resposta pública da API não é JSON válido")]
                if not isinstance(payload, list):
                    return [Finding(code + "_invalid", "critical", "Resposta pública da API tem formato inesperado")]
    except Exception:
        return [Finding(code + "_unavailable", "critical", "Rota pública indisponível")]
    elapsed_ms = round((time.monotonic() - started) * 1000)
    if elapsed_ms > config.latency_limit_ms:
        return [Finding(code + "_slow", "warning", f"Latência {elapsed_ms} ms acima de {config.latency_limit_ms} ms")]
    return []


def _tls_probe(origin: str, code: str, config: Config) -> list[Finding]:
    parsed = urlsplit(origin)
    try:
        with socket.create_connection((parsed.hostname, parsed.port or 443), timeout=config.timeout_seconds) as raw:
            with ssl.create_default_context().wrap_socket(raw, server_hostname=parsed.hostname) as secured:
                expiry = ssl.cert_time_to_seconds(secured.getpeercert()["notAfter"])
    except Exception:
        return [Finding(code + "_tls_invalid", "critical", "TLS inválido ou certificado indisponível")]
    remaining_days = (expiry - time.time()) / 86400
    if remaining_days < config.tls_warn_days:
        return [Finding(code + "_tls_expiring", "warning", f"Certificado vence em {max(0, int(remaining_days))} dias")]
    return []


def collect(config: Config) -> list[Finding]:
    findings = _http_probe(config.frontend_url + "/", "frontend", config)
    findings += _http_probe(config.api_url + "/api/auth/providers", "api", config, json_list=True)
    for code, origin in (("frontend", config.frontend_url), ("api", config.api_url)):
        findings += _tls_probe(origin, code, config)
    return findings


def _compose_command(config: Config, *args: str) -> list[str]:
    if not config.compose_file or not config.compose_env_file:
        raise ValueError("Configure MONITOR_COMPOSE_FILE e MONITOR_COMPOSE_ENV_FILE para a checagem local")
    return ["docker", "compose", "--env-file", str(config.compose_env_file), "-f", str(config.compose_file), *args]


def _run(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, timeout=15, check=False)
    if result.returncode:
        raise RuntimeError("Comando de inspeção falhou")
    return result.stdout


def collect_host(config: Config) -> tuple[list[Finding], dict[str, int]]:
    """Inspect only numeric metadata; no container logs or patient rows are read."""
    findings: list[Finding] = []
    metrics: dict[str, int] = {}
    if config.disk_path is None or config.backup_marker is None:
        raise ValueError("Configure MONITOR_DISK_PATH e MONITOR_BACKUP_MARKER para a checagem local")
    try:
        disk = shutil.disk_usage(config.disk_path)
        used = round(100 * disk.used / disk.total)
        metrics["disk_used_percent"] = used
        if used >= config.disk_critical_percent:
            findings.append(Finding("disk_full", "critical", f"Disco em {used}% de uso"))
        elif used >= config.disk_warn_percent:
            findings.append(Finding("disk_high", "warning", f"Disco em {used}% de uso"))
    except OSError:
        findings.append(Finding("disk_unavailable", "critical", "Não foi possível medir o disco"))

    try:
        age_hours = max(0, round((time.time() - config.backup_marker.stat().st_mtime) / 3600))
        metrics["backup_age_hours"] = age_hours
        if age_hours >= config.backup_critical_hours:
            findings.append(Finding("backup_overdue", "critical", f"Último backup verificado há {age_hours} horas"))
        elif age_hours >= config.backup_warn_hours:
            findings.append(Finding("backup_aging", "warning", f"Último backup verificado há {age_hours} horas"))
    except OSError:
        findings.append(Finding("backup_missing", "critical", "Marcador de backup verificado ausente"))

    try:
        raw = _run(_compose_command(config, "ps", "--format", "json"))
        parsed = json.loads(raw) if raw.lstrip().startswith("[") else [json.loads(line) for line in raw.splitlines() if line.strip()]
        by_service = {item.get("Service"): item for item in parsed}
        for service in ("postgres", "backend", "agenda", "chatbot", "frontend", "proxy"):
            item = by_service.get(service)
            if not item or item.get("State") != "running" or item.get("Health") != "healthy":
                findings.append(Finding(f"service_{service}_down", "critical", f"Serviço {service} não está saudável"))
        ids = [item["ID"] for item in parsed if item.get("ID") and item.get("State") == "running"]
        if ids:
            stats = [json.loads(line) for line in _run(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids]).splitlines() if line.strip()]
            cpu = max((float(item["CPUPerc"].rstrip("%")) for item in stats), default=0)
            memory = max((float(item["MemPerc"].rstrip("%")) for item in stats), default=0)
            metrics["container_cpu_peak_percent"] = round(cpu)
            metrics["container_memory_peak_percent"] = round(memory)
            if cpu >= 90:
                findings.append(Finding("container_cpu_high", "warning", "CPU de contêiner acima de 90%"))
            if memory >= 90:
                findings.append(Finding("container_memory_high", "warning", "Memória de contêiner acima de 90%"))
        if by_service.get("postgres", {}).get("Health") == "healthy":
            query = ("SELECT (SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()),"
                     "(SELECT count(*) FROM mensageria_outbox WHERE status IN ('pendente','retentativa','processando')),")
            query += "(SELECT count(*) FROM mensageria_provedor_estado WHERE estado<>'fechado')"
            shell = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F , -c "$1"'
            values = _run(_compose_command(config, "exec", "-T", "postgres", "sh", "-c", shell, "sh", query)).strip().split(",")
            metrics["postgres_connections"], metrics["queue_pending"], metrics["integrations_open"] = map(int, values)
            if metrics["queue_pending"] >= 100:
                findings.append(Finding("queue_backlog", "warning", "Fila de mensageria com 100 ou mais itens"))
            if metrics["integrations_open"] > 0:
                findings.append(Finding("integration_open", "warning", "Há integração com circuito aberto"))
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
        findings.append(Finding("host_metrics_unavailable", "critical", "Métricas locais indisponíveis"))
    return findings, metrics


def _load_state(path: Path) -> dict[str, dict]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except FileNotFoundError:
        return {}
    except json.JSONDecodeError:
        return {}


def _save_state(path: Path, value: dict[str, dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".monitor-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            json.dump(value, output, sort_keys=True)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def _send(config: Config, event: str, finding: Finding) -> None:
    payload = {
        "text": f"Clinisis {event}: {finding.code} ({finding.severity}). Responsável: {config.owner}. {finding.detail}",
        "event": event,
        "code": finding.code,
        "severity": finding.severity,
        "owner": config.owner,
        "observed_at": datetime.now(timezone.utc).isoformat(),
    }
    request = Request(config.webhook_url, data=json.dumps(payload).encode("utf-8"),
                      headers={"Content-Type": "application/json"}, method="POST")
    with urlopen(request, timeout=config.timeout_seconds) as response:
        if not 200 <= response.status < 300:
            raise RuntimeError("Webhook recusou o alerta")


def monitor_once(config: Config, probe=collect, sender=_send) -> list[Finding]:
    findings = probe(config)
    previous = _load_state(config.state_file)
    now = time.time()
    current = {}
    for item in findings:
        prior = previous.get(item.code, {})
        if not isinstance(prior, dict):
            prior = {}
        if prior.get("severity") != item.severity:
            sender(config, "falha", item)
            current[item.code] = {"severity": item.severity, "first_seen": now, "escalated": False}
        else:
            current[item.code] = prior.copy()
            if (config.escalation_webhook_url and not prior.get("escalated")
                    and now - prior.get("first_seen", now) >= config.escalation_after_seconds):
                sender(replace(config, webhook_url=config.escalation_webhook_url), "escalacao", item)
                current[item.code]["escalated"] = True
    for code, old in previous.items():
        if code not in current:
            severity = old.get("severity", "warning") if isinstance(old, dict) else str(old)
            recovery = Finding(code, severity, "Condição voltou ao normal")
            sender(config, "recuperacao", recovery)
            if isinstance(old, dict) and old.get("escalated") and config.escalation_webhook_url:
                sender(replace(config, webhook_url=config.escalation_webhook_url), "recuperacao", recovery)
    _save_state(config.state_file, current)
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test-alert", action="store_true", help="Envia alerta sintético sem alterar o estado")
    parser.add_argument("--test-escalation", action="store_true", help="Envia teste ao contato de escalonamento")
    parser.add_argument("--host", action="store_true", help="Verifica disco, backup, contêineres, banco e fila na VPS")
    args = parser.parse_args()
    try:
        config = config_from_env()
        if args.test_alert:
            _send(config, "teste", Finding("monitor_test", "info", "Teste de entrega e resposta do plantão"))
            print('{"test_alert":"sent"}')
            return 0
        if args.test_escalation:
            _send(replace(config, webhook_url=config.escalation_webhook_url),
                  "teste_escalacao", Finding("monitor_test", "info", "Teste do substituto de plantão"))
            print('{"test_escalation":"sent"}')
            return 0
        if args.host:
            host_findings, metrics = collect_host(config)
            findings = monitor_once(config, lambda _config: host_findings)
        else:
            metrics = {}
            findings = monitor_once(config)
        print(json.dumps({"status": "degraded" if findings else "ok", "findings": [item.code for item in findings], "metrics": metrics}))
        return 1 if findings else 0
    except (ValueError, OSError, RuntimeError) as error:
        print(f"monitor_error: {type(error).__name__}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
