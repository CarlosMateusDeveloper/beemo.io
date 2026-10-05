from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import declarative_base, sessionmaker, Session
from chatbot.tenant import tenant_id

from chatbot.config import DATABASE_URL

# O schema do banco (database/schema_clinica.sql) nao pertence ao chatbot —
# este modulo so abre uma conexao com o banco ja existente do sistema.
engine = create_engine(DATABASE_URL) if DATABASE_URL else None
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    """Dependency do FastAPI: uma sessao de banco por requisicao."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@event.listens_for(Session, 'after_begin')
def apply_tenant(session, transaction, connection):
    if transaction.nested:
        return
    tenant = session.info.get('tenant_id') or tenant_id()
    session.info['tenant_id'] = tenant
    connection.execute(text("SELECT set_config('app.tenant_id', :tenant, true)"), {'tenant': str(tenant)})
