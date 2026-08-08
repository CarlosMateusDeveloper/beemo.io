from fastapi import APIRouter

router = APIRouter(prefix="/contatos", tags=["contatos"])


@router.get("/{telefone}")
async def buscar_contato(telefone: str):
    """Busca um contato pelo telefone.

    Estrutura inicial apenas - consulta ao banco ainda nao implementada.
    """
    raise NotImplementedError("Busca de contato ainda nao implementada.")
