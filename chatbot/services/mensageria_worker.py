"""Worker leve da outbox; cada item usa uma transacao curta por tenant."""

import asyncio
import logging

from sqlalchemy import text

from chatbot.db import SessionLocal, engine
from chatbot.services import mensageria_service
from chatbot.tenant import current_tenant

logger = logging.getLogger(__name__)


def _run_batch() -> int:
    if engine is None:
        return 0
    with engine.connect() as connection:
        tenants = connection.execute(text("SELECT id_clinica FROM clinica ORDER BY id_clinica")).scalars().all()
    processed = 0
    for tenant in tenants:
        marker = current_tenant.set(tenant)
        try:
            for _ in range(20):
                db = SessionLocal(info={"tenant_id": tenant})
                try:
                    if not mensageria_service.process_one(db):
                        break
                    processed += 1
                except Exception:
                    db.rollback()
                    logger.exception("Falha inesperada no worker de mensageria (tenant=%s)", tenant)
                    break
                finally:
                    db.close()
        finally:
            current_tenant.reset(marker)
    return processed


async def run_forever(stop: asyncio.Event) -> None:
    while not stop.is_set():
        processed = await asyncio.to_thread(_run_batch)
        try:
            await asyncio.wait_for(stop.wait(), timeout=0.2 if processed else 2.0)
        except asyncio.TimeoutError:
            pass
