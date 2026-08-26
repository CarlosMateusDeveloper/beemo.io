from datetime import datetime, timedelta, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

from chatbot.config import WHATSAPP_API_TOKEN, WHATSAPP_API_URL
from chatbot.models.capacidade_bot import CapacidadeBotConfig
# Import necessario mesmo sem uso direto: registra a tabela `clinica` no
# metadata do SQLAlchemy, senao as FKs de Conversa/CapacidadeBotConfig/etc
# pra clinica.id_clinica nao resolvem na hora do flush/commit.
from chatbot.models.clinica import Clinica  # noqa: F401
from chatbot.models.conversa import Conversa, EstadoConversaBot
from chatbot.models.mensagem import DirecaoMensagem, Mensagem, RemetenteMensagem, TipoMensagem
from chatbot.models.mensagem_template import MensagemTemplateBot
from chatbot.models.regra_atendimento import RegraAtendimentoBot
from chatbot.models.usuario import Usuario  # noqa: F401 — mesmo motivo do import de Clinica acima

# Sem sessao/login real ainda (Fase 0 do roadmap "multiempresa" nao
# implementada) — mono-clinica por enquanto, mas o schema ja esta pronto
# pra multi-tenant (ver database/schema_clinica.sql, Fase 10).
ID_CLINICA_ATUAL = 1

# Copy de produto (nome, texto de impacto ao desligar), nao dado de linha —
# mesmo catalogo que estava hardcoded no mock do frontend.
CATALOGO_CAPACIDADES = [
    {"id": "confirmar_presenca", "nome": "Confirmar presença",
     "impactoDesligada": "Pacientes não recebem mais o lembrete automático na véspera — a régua de confirmação para de disparar."},
    {"id": "consultar_horario", "nome": "Consultar meu horário",
     "impactoDesligada": "Pacientes deixam de conseguir consultar o próprio horário pelo WhatsApp; toda dúvida vira contato manual."},
    {"id": "marcar_consulta", "nome": "Marcar consulta",
     "impactoDesligada": "O assistente para de fechar agendamentos pelo WhatsApp."},
    {"id": "remarcar_cancelar", "nome": "Remarcar e cancelar",
     "impactoDesligada": "Pacientes não conseguem mais remarcar ou cancelar sozinhos — toda mudança vira ligação para a recepção."},
    {"id": "vaga_aberta", "nome": "Oferecer vaga aberta",
     "impactoDesligada": "Vagas abertas por cancelamento deixam de ser oferecidas automaticamente à fila de espera."},
    {"id": "cadastro_novo", "nome": "Cadastro de paciente novo",
     "impactoDesligada": "Pacientes novos voltam a precisar ligar ou ir à recepção para se cadastrar."},
    {"id": "aviso_exame", "nome": "Avisar resultado de exame",
     "impactoDesligada": "Pacientes não são avisados quando um resultado de exame fica pronto."},
]

PERIODO_DIAS = {"Hoje": 1, "7 dias": 7, "Últimos 30 dias": 30}

# strftime("%b") depende do locale do servidor (vira inglês em ambiente
# "C") — mapa fixo evita isso.
MESES_PT = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]


def _hora(dt: datetime) -> str:
    return dt.strftime("%H:%M")


def _data_curta(d) -> str:
    return d.strftime("%d/%m")


def obter_status() -> dict:
    """conectado=True só quando as credenciais do provedor de WhatsApp
    estiverem configuradas — hoje não estão, então isso é False de verdade,
    não mock."""
    return {"conectado": bool(WHATSAPP_API_URL and WHATSAPP_API_TOKEN), "numero": None}


def listar_conversas(db: Session) -> list[dict]:
    conversas = (
        db.query(Conversa)
        .filter(Conversa.id_clinica == ID_CLINICA_ATUAL)
        .order_by(Conversa.atualizado_em.desc())
        .all()
    )
    resultado = []
    for c in conversas:
        paciente_nome = None
        if c.id_paciente:
            linha = db.execute(
                text("SELECT nome FROM paciente WHERE id_paciente = :id"), {"id": c.id_paciente}
            ).first()
            paciente_nome = linha[0] if linha else None

        ultima = (
            db.query(Mensagem)
            .filter(Mensagem.telefone == c.telefone)
            .order_by(Mensagem.criado_em.desc())
            .first()
        )

        espera_min = None
        if c.estado == EstadoConversaBot.aguardando:
            ultima_entrada = (
                db.query(Mensagem)
                .filter(Mensagem.telefone == c.telefone, Mensagem.direcao == DirecaoMensagem.entrada)
                .order_by(Mensagem.criado_em.desc())
                .first()
            )
            if ultima_entrada:
                delta = datetime.now(timezone.utc) - ultima_entrada.criado_em
                espera_min = max(0, int(delta.total_seconds() // 60))

        resultado.append({
            "id": c.id_conversa,
            "paciente": paciente_nome,
            "telefone": c.telefone,
            "estado": c.estado.value,
            "agente": c.agente_nome,
            "esperaMin": espera_min,
            "horario": _hora(ultima.criado_em) if ultima else None,
            "ultimaMensagem": ultima.conteudo if ultima else None,
            "nota": None,
        })
    return resultado


def _buscar_conversa(db: Session, conversa_id: int) -> Conversa | None:
    conversa = db.get(Conversa, conversa_id)
    if conversa is None or conversa.id_clinica != ID_CLINICA_ATUAL:
        return None
    return conversa


def listar_mensagens_conversa(db: Session, conversa_id: int) -> list[dict] | None:
    conversa = _buscar_conversa(db, conversa_id)
    if conversa is None:
        return None
    linhas = (
        db.query(Mensagem)
        .filter(Mensagem.telefone == conversa.telefone)
        .order_by(Mensagem.criado_em.asc())
        .all()
    )
    resultado = []
    for m in linhas:
        if m.remetente is not None:
            remetente = m.remetente.value
        else:
            remetente = "paciente" if m.direcao == DirecaoMensagem.entrada else "bot"
        resultado.append({
            "id": m.id_mensagem,
            "tipo": "mensagem",
            "remetente": remetente,
            "agente": conversa.agente_nome if remetente == "agente" else None,
            "texto": m.conteudo,
            "horario": _hora(m.criado_em),
        })
    return resultado


def obter_contexto_conversa(db: Session, conversa_id: int) -> dict | None:
    """None = "sem contexto" (telefone não cadastrado como paciente) — o
    componente já trata esse estado. Campos que dependeriam do motor do bot
    (pendencias, assistente) ficam vazios/nulos: não existe execução de bot
    ainda pra reportar."""
    conversa = _buscar_conversa(db, conversa_id)
    if conversa is None or conversa.id_paciente is None:
        return None

    paciente = db.execute(
        text("""
            SELECT p.nome, p.data_nascimento, cv.nome AS convenio
            FROM paciente p
            LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio
            WHERE p.id_paciente = :id
        """),
        {"id": conversa.id_paciente},
    ).mappings().first()
    if paciente is None:
        return None

    hoje = datetime.now().date()
    nascimento = paciente["data_nascimento"]
    idade = hoje.year - nascimento.year - ((hoje.month, hoje.day) < (nascimento.month, nascimento.day))

    proxima = db.execute(
        text("""
            SELECT a.data_slot, a.hora_slot, e.nome AS especialidade, m.nome AS profissional
            FROM consulta co
            JOIN agenda a ON a.id_agenda = co.id_agenda
            JOIN medico m ON m.id_medico = a.id_medico
            JOIN especialidade e ON e.id_especialidade = m.id_especialidade
            WHERE co.id_paciente = :id
              AND a.data_slot >= CURRENT_DATE
              AND co.status_consulta IN ('Agendada', 'Confirmada')
            ORDER BY a.data_slot, a.hora_slot
            LIMIT 1
        """),
        {"id": conversa.id_paciente},
    ).mappings().first()

    visitas = db.execute(
        text("""
            SELECT a.data_slot, e.nome AS especialidade, m.nome AS profissional
            FROM consulta co
            JOIN agenda a ON a.id_agenda = co.id_agenda
            JOIN medico m ON m.id_medico = a.id_medico
            JOIN especialidade e ON e.id_especialidade = m.id_especialidade
            WHERE co.id_paciente = :id AND co.status_consulta = 'Realizada'
            ORDER BY a.data_slot DESC
            LIMIT 3
        """),
        {"id": conversa.id_paciente},
    ).mappings().all()

    return {
        "paciente": {
            "nome": paciente["nome"],
            "idade": idade,
            "convenio": paciente["convenio"] or "particular",
            # Nao ha data de cadastro no schema hoje.
            "clienteDesde": None,
        },
        "proximaConsulta": {
            "data": _data_curta(proxima["data_slot"]),
            "hora": proxima["hora_slot"].strftime("%H:%M"),
            "especialidade": proxima["especialidade"],
            "profissional": proxima["profissional"],
            # Nao ha rastreio de "confirmacao enviada" no schema ainda —
            # suprime o alerta em vez de acusar algo que nao sabemos.
            "confirmacaoEnviada": True,
        } if proxima else None,
        "ultimasVisitas": [
            {"especialidade": v["especialidade"], "profissional": v["profissional"], "data": _data_curta(v["data_slot"])}
            for v in visitas
        ],
        "pendencias": [],
        "assistente": None,
    }


def assumir_conversa(db: Session, conversa_id: int, agente_nome: str) -> Conversa | None:
    conversa = _buscar_conversa(db, conversa_id)
    if conversa is None:
        return None
    conversa.estado = EstadoConversaBot.com_agente
    conversa.agente_nome = agente_nome
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()
    db.refresh(conversa)
    return conversa


def devolver_conversa(db: Session, conversa_id: int) -> Conversa | None:
    conversa = _buscar_conversa(db, conversa_id)
    if conversa is None:
        return None
    conversa.estado = EstadoConversaBot.bot
    conversa.agente_nome = None
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()
    db.refresh(conversa)
    return conversa


def enviar_mensagem_agente(db: Session, conversa_id: int, texto: str) -> dict | None:
    """Persiste a resposta do atendente no historico real. Não chama a API
    do WhatsApp de fato — isso depende do provedor escolhido
    (whatsapp_service.send_message, ainda NotImplementedError)."""
    conversa = _buscar_conversa(db, conversa_id)
    if conversa is None:
        return None
    nova = Mensagem(
        id_paciente=conversa.id_paciente,
        telefone=conversa.telefone,
        direcao=DirecaoMensagem.saida,
        tipo=TipoMensagem.texto,
        conteudo=texto,
        remetente=RemetenteMensagem.agente,
    )
    db.add(nova)
    conversa.atualizado_em = datetime.now(timezone.utc)
    db.commit()
    db.refresh(nova)
    return {
        "id": nova.id_mensagem,
        "tipo": "mensagem",
        "remetente": "agente",
        "agente": conversa.agente_nome,
        "texto": nova.conteudo,
        "horario": _hora(nova.criado_em),
    }


def obter_assistente(db: Session) -> dict:
    ativos = {
        row.capacidade_id: row.ativo
        for row in db.query(CapacidadeBotConfig).filter(CapacidadeBotConfig.id_clinica == ID_CLINICA_ATUAL)
    }
    capacidades = [
        {**catalogo, "ativo": ativos.get(catalogo["id"], True)}
        for catalogo in CATALOGO_CAPACIDADES
    ]

    mensagens: dict[str, dict[str, str]] = {}
    for row in db.query(MensagemTemplateBot).filter(MensagemTemplateBot.id_clinica == ID_CLINICA_ATUAL):
        mensagens.setdefault(row.capacidade_id, {})[row.campo] = row.texto

    regra_row = db.get(RegraAtendimentoBot, ID_CLINICA_ATUAL)
    regras = regra_row.regras if regra_row else {}

    return {"capacidades": capacidades, "mensagens": mensagens, "regras": regras}


def alternar_capacidade(db: Session, capacidade_id: str, ativo: bool) -> None:
    row = db.get(CapacidadeBotConfig, (ID_CLINICA_ATUAL, capacidade_id))
    if row is None:
        row = CapacidadeBotConfig(id_clinica=ID_CLINICA_ATUAL, capacidade_id=capacidade_id, ativo=ativo)
        db.add(row)
    else:
        row.ativo = ativo
        row.atualizado_em = datetime.now(timezone.utc)
    db.commit()


def atualizar_mensagens(db: Session, capacidade_id: str, campos: dict[str, str]) -> None:
    for campo, texto in campos.items():
        row = db.get(MensagemTemplateBot, (ID_CLINICA_ATUAL, capacidade_id, campo))
        if row is None:
            row = MensagemTemplateBot(id_clinica=ID_CLINICA_ATUAL, capacidade_id=capacidade_id, campo=campo, texto=texto)
            db.add(row)
        else:
            row.texto = texto
            row.atualizado_em = datetime.now(timezone.utc)
    db.commit()


def atualizar_regras(db: Session, regras: dict) -> None:
    row = db.get(RegraAtendimentoBot, ID_CLINICA_ATUAL)
    if row is None:
        row = RegraAtendimentoBot(id_clinica=ID_CLINICA_ATUAL, regras=regras)
        db.add(row)
    else:
        row.regras = regras
        row.atualizado_em = datetime.now(timezone.utc)
    db.commit()


def calcular_desempenho(db: Session, periodo: str) -> dict:
    dias = PERIODO_DIAS.get(periodo, 30)
    desde = datetime.now(timezone.utc) - timedelta(days=dias)

    # Janela de 6 meses fixa pra evolução mensal — independente do período
    # selecionado nos KPIs acima (mesmo comportamento do mock anterior).
    seis_meses_atras = datetime.now(timezone.utc) - timedelta(days=186)
    linhas_6m = db.execute(
        text("""
            SELECT telefone, direcao, remetente, criado_em
            FROM mensagem
            WHERE criado_em >= :desde
            ORDER BY criado_em
        """),
        {"desde": seis_meses_atras},
    ).mappings().all()

    linhas = [m for m in linhas_6m if m["criado_em"] >= desde]

    por_telefone: dict[str, list] = {}
    for linha in linhas:
        por_telefone.setdefault(linha["telefone"], []).append(linha)

    total = len(por_telefone)
    humano = sum(1 for msgs in por_telefone.values() if any(m["remetente"] == "agente" for m in msgs))
    bot = total - humano
    share_pct = (bot / total * 100) if total else 0.0

    tempos_bot = []
    tempos_agente = []
    for msgs in por_telefone.values():
        primeira_entrada = next((m for m in msgs if m["direcao"] == "entrada"), None)
        if not primeira_entrada:
            continue
        primeira_bot = next((m for m in msgs if m["remetente"] == "bot" and m["criado_em"] > primeira_entrada["criado_em"]), None)
        if primeira_bot:
            tempos_bot.append((primeira_bot["criado_em"] - primeira_entrada["criado_em"]).total_seconds())
        primeira_agente = next((m for m in msgs if m["remetente"] == "agente" and m["criado_em"] > primeira_entrada["criado_em"]), None)
        if primeira_agente:
            tempos_agente.append((primeira_agente["criado_em"] - primeira_entrada["criado_em"]).total_seconds())

    tempo_bot_seg = int(sum(tempos_bot) / len(tempos_bot)) if tempos_bot else 0
    tempo_agente_seg = int(sum(tempos_agente) / len(tempos_agente)) if tempos_agente else 0

    by_hour = []
    for hora in range(7, 20):
        linhas_hora = [m for m in linhas if m["direcao"] == "saida" and m["criado_em"].hour == hora]
        by_hour.append({
            "hora": f"{hora}h",
            "bot": sum(1 for m in linhas_hora if m["remetente"] == "bot"),
            "humano": sum(1 for m in linhas_hora if m["remetente"] == "agente"),
        })
    volume_total = sum(h["bot"] + h["humano"] for h in by_hour)
    if by_hour:
        pico = max(by_hour, key=lambda h: h["bot"] + h["humano"])
        peak_pct = ((pico["bot"] + pico["humano"]) / volume_total * 100) if volume_total else 0.0
        peak_label = f"pico {pico['hora']}"
    else:
        peak_pct, peak_label = 0.0, "sem pico"

    hoje = datetime.now(timezone.utc)
    ancora = hoje.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    meses_inicio = []
    cursor = ancora
    for _ in range(6):
        meses_inicio.append(cursor)
        cursor = (cursor - timedelta(days=1)).replace(day=1)
    meses_inicio.reverse()

    by_month = []
    for inicio_mes in meses_inicio:
        fim_mes = (inicio_mes.replace(day=28) + timedelta(days=4)).replace(day=1)
        linhas_mes = [m for m in linhas_6m if inicio_mes <= m["criado_em"] < fim_mes]
        telefones_mes: dict[str, list] = {}
        for m in linhas_mes:
            telefones_mes.setdefault(m["telefone"], []).append(m)
        total_mes = len(telefones_mes)
        bot_mes = sum(1 for msgs in telefones_mes.values() if not any(m["remetente"] == "agente" for m in msgs))
        pct_mes = (bot_mes / total_mes * 100) if total_mes else 0.0
        by_month.append({"mes": MESES_PT[inicio_mes.month - 1], "pct": round(pct_mes, 1)})

    aguardando_agora = (
        db.query(Conversa)
        .filter(Conversa.id_clinica == ID_CLINICA_ATUAL, Conversa.estado == EstadoConversaBot.aguardando)
        .count()
    )
    aguardando_rows = (
        db.query(Conversa)
        .filter(Conversa.id_clinica == ID_CLINICA_ATUAL, Conversa.estado == EstadoConversaBot.aguardando)
        .all()
    )
    esperas_hoje = []
    for c in aguardando_rows:
        ultima_entrada = (
            db.query(Mensagem)
            .filter(Mensagem.telefone == c.telefone, Mensagem.direcao == DirecaoMensagem.entrada)
            .order_by(Mensagem.criado_em.desc())
            .first()
        )
        if ultima_entrada:
            esperas_hoje.append((datetime.now(timezone.utc) - ultima_entrada.criado_em).total_seconds() / 60)
    espera_media_hoje = round(sum(esperas_hoje) / len(esperas_hoje)) if esperas_hoje else 0

    return {
        "resolvidasSemAtendente": bot,
        "total": total,
        "sharePct": round(share_pct, 1),
        "deltaPct": 0,
        "escalonamento": {"total": humano, "motivos": []},
        "tempoPrimeiraRespostaSeg": tempo_bot_seg,
        "tempoPrimeiraRespostaAtendenteSeg": tempo_agente_seg,
        "acoes": {"total": 0, "porTipo": [
            {"tipo": "Agendamentos", "qtd": 0},
            {"tipo": "Confirmações", "qtd": 0},
            {"tipo": "Remarcações", "qtd": 0},
            {"tipo": "Cancelamentos", "qtd": 0},
        ]},
        "byHour": by_hour,
        "peakLabel": peak_label,
        "peakPct": round(peak_pct, 1),
        "byMonth": by_month,
        "aguardandoAgora": aguardando_agora,
        "esperaMediaHojeMin": espera_media_hoje,
    }
