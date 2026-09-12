"""Descoberta do contato (tabela `paciente`) a partir do telefone do WhatsApp.

A Cloud API entrega o telefone como uma string unica de digitos com codigo
do pais (ex: 5584999999999); o banco guarda `ddd` e `numero` separados. A
traducao entre os dois formatos mora aqui, e nao em cada service que
precisa dela.
"""

import re

from sqlalchemy.orm import Session

from chatbot.models.contato import Contato


def buscar_por_telefone(db: Session, telefone: str) -> Contato | None:
    partes = _ddd_e_numero(telefone)
    if partes is None:
        return None
    ddd, numeros = partes
    return (
        db.query(Contato)
        .filter(Contato.ddd == ddd, Contato.numero.in_(numeros))
        .first()
    )


def _ddd_e_numero(telefone: str) -> tuple[str, list[str]] | None:
    """Devolve (ddd, variacoes do numero) ou None se nao parecer telefone BR.

    Sao varias variacoes porque o nono digito de celular e uma armadilha
    conhecida: o mesmo contato aparece ora como 84999999999, ora como
    8499999999, dependendo de quando o cadastro foi feito. Buscar so pela
    forma exata faria o bot tratar paciente antigo como desconhecido.
    """
    digitos = re.sub(r"\D", "", telefone or "")
    # 12 ou 13 digitos = ja vem com o codigo do pais (55 + ddd + numero).
    # Abaixo disso, um "55" inicial e o DDD 55 (Santa Maria/RS), nao o pais.
    if len(digitos) in (12, 13) and digitos.startswith("55"):
        digitos = digitos[2:]
    if len(digitos) not in (10, 11):
        return None

    ddd, numero = digitos[:2], digitos[2:]
    variacoes = {numero}
    if len(numero) == 8:
        variacoes.add(f"9{numero}")
    elif numero.startswith("9"):
        variacoes.add(numero[1:])
    return ddd, sorted(variacoes)
