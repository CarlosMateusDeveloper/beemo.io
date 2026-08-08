from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

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
