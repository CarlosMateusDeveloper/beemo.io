import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException
from sqlalchemy.exc import OperationalError

from chatbot import main


class HealthTest(unittest.TestCase):
    def test_health_requires_database(self):
        with patch.object(main, "engine", None):
            with self.assertRaises(HTTPException) as error:
                main.health()
        self.assertEqual(error.exception.status_code, 503)

    def test_health_detects_database_failure(self):
        engine = MagicMock()
        engine.connect.side_effect = OperationalError("SELECT 1", {}, Exception("database unavailable"))
        with patch.object(main, "engine", engine):
            with self.assertRaises(HTTPException) as error:
                main.health()
        self.assertEqual(error.exception.status_code, 503)

    def test_health_returns_ok_when_database_responds(self):
        engine = MagicMock()
        with patch.object(main, "engine", engine):
            self.assertEqual(main.health(), {"status": "ok"})
        engine.connect.return_value.__enter__.return_value.execute.assert_called_once()


if __name__ == "__main__":
    unittest.main()
