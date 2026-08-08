from fastapi import APIRouter

router = APIRouter(prefix="/mensagens", tags=["mensagens"])


@router.get("/{telefone}")
async def listar_mensagens(telefone: str):
    """Lista o historico de mensagens de um contato.

    Estrutura inicial apenas - consulta ao banco ainda nao implementada.
    """
    raise NotImplementedError("Listagem de mensagens ainda nao implementada.")
