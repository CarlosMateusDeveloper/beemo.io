"""Schemas do webhook da WhatsApp Business Cloud API (Meta).

O payload da Meta e bem aninhado (entry > changes > value > messages) e
carrega eventos que nao interessam ao bot (status de entrega, por
exemplo). Os modelos abaixo descrevem so o que consumimos, e
`extrair_mensagens` achata isso na forma que o service espera.

Os modelos sao propositalmente tolerantes (quase tudo opcional): campo
novo ou tipo de mensagem desconhecido nao pode virar 422, senao a Meta
reenvia o evento em loop e acaba desativando o webhook.
"""

from pydantic import BaseModel, ConfigDict, Field

# Rotulo usado quando a mensagem nao tem texto proprio (audio, imagem sem
# legenda...). Vai para o historico como o que o atendente vera na tela.
ROTULOS_SEM_TEXTO = {
    "audio": "[áudio recebido]",
    "image": "[imagem recebida]",
    "video": "[vídeo recebido]",
    "sticker": "[figurinha recebida]",
    "document": "[documento recebido]",
    "location": "[localização recebida]",
    "contacts": "[contato recebido]",
}


class MensagemRecebida(BaseModel):
    """Uma mensagem de paciente, ja achatada e pronta para o service."""

    wamid: str
    telefone: str
    conteudo: str
    tipo_whatsapp: str = "text"
    nome_perfil: str | None = None


class _Texto(BaseModel):
    body: str = ""


class _Midia(BaseModel):
    caption: str | None = None
    filename: str | None = None


class _Resposta(BaseModel):
    """button_reply / list_reply de uma mensagem interativa."""

    id: str | None = None
    title: str | None = None


class _Interativo(BaseModel):
    type: str | None = None
    button_reply: _Resposta | None = None
    list_reply: _Resposta | None = None


class _Botao(BaseModel):
    text: str | None = None
    payload: str | None = None


class _Localizacao(BaseModel):
    latitude: float | None = None
    longitude: float | None = None
    name: str | None = None
    address: str | None = None


class _Mensagem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    # "from" e palavra reservada em Python — o alias mantem o nome do payload.
    remetente: str = Field(alias="from")
    timestamp: str | None = None
    type: str = "text"
    text: _Texto | None = None
    image: _Midia | None = None
    video: _Midia | None = None
    document: _Midia | None = None
    audio: _Midia | None = None
    interactive: _Interativo | None = None
    button: _Botao | None = None
    location: _Localizacao | None = None

    def conteudo(self) -> str:
        if self.type == "text" and self.text:
            return self.text.body
        if self.type == "interactive" and self.interactive:
            resposta = self.interactive.button_reply or self.interactive.list_reply
            if resposta and resposta.title:
                return resposta.title
        if self.type == "button" and self.button and self.button.text:
            return self.button.text
        if self.type == "document" and self.document and self.document.filename:
            return f"[documento: {self.document.filename}]"
        for midia in (self.image, self.video, self.audio):
            if midia and midia.caption:
                return midia.caption
        if self.type == "location" and self.location:
            local = self.location.name or self.location.address
            if local:
                return f"[localização: {local}]"
        return ROTULOS_SEM_TEXTO.get(self.type, f"[mensagem do tipo {self.type}]")


class _Perfil(BaseModel):
    name: str | None = None


class _Contato(BaseModel):
    wa_id: str | None = None
    profile: _Perfil | None = None


class _Valor(BaseModel):
    messaging_product: str | None = None
    contacts: list[_Contato] = []
    messages: list[_Mensagem] = []


class _Mudanca(BaseModel):
    field: str | None = None
    value: _Valor | None = None


class _Entrada(BaseModel):
    id: str | None = None
    changes: list[_Mudanca] = []


class WebhookPayload(BaseModel):
    """Envelope que a Meta envia em POST /webhook."""

    object: str | None = None
    entry: list[_Entrada] = []

    def extrair_mensagens(self) -> list[MensagemRecebida]:
        """So mensagens de paciente: eventos de status de entrega sao ignorados."""
        recebidas: list[MensagemRecebida] = []
        for entrada in self.entry:
            for mudanca in entrada.changes:
                valor = mudanca.value
                if valor is None:
                    continue
                nomes = {
                    contato.wa_id: contato.profile.name
                    for contato in valor.contacts
                    if contato.wa_id and contato.profile
                }
                for mensagem in valor.messages:
                    recebidas.append(MensagemRecebida(
                        wamid=mensagem.id,
                        telefone=mensagem.remetente,
                        conteudo=mensagem.conteudo(),
                        tipo_whatsapp=mensagem.type,
                        nome_perfil=nomes.get(mensagem.remetente),
                    ))
        return recebidas
