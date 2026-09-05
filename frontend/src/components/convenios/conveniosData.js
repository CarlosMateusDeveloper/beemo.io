// Constantes e helpers compartilhados da tela /convenios.

// Alinhado ao que ConveniosKpiService.resolverInicio aceita no backend.
export const PERIODOS = [
  { valor: 'Últimos 30 dias', rotulo: 'Últimos 30 dias' },
  { valor: 'Hoje', rotulo: 'Hoje' },
  { valor: '7 dias', rotulo: 'Últimos 7 dias' },
  { valor: '90 dias', rotulo: 'Últimos 90 dias' },
]

export function brl(valor) {
  return Number(valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

// Segue os 9 tipos de regra_auditoria.tipo do schema (Fase 14) — usado nos
// selects do CRUD de regras e, futuramente, na aba Auditoria real.
export const TIPOS_REGRA = [
  { valor: 'autorizacao_obrigatoria', rotulo: 'Autorização obrigatória' },
  { valor: 'documento_obrigatorio', rotulo: 'Documento obrigatório' },
  { valor: 'codigo_incompativel', rotulo: 'Código incompatível' },
  { valor: 'procedimento_nao_coberto', rotulo: 'Procedimento não coberto' },
  { valor: 'paciente_inelegivel', rotulo: 'Paciente inelegível' },
  { valor: 'quantidade_acima_permitido', rotulo: 'Quantidade acima do permitido' },
  { valor: 'prazo_faturamento_excedido', rotulo: 'Prazo de faturamento excedido' },
  { valor: 'profissional_nao_habilitado', rotulo: 'Profissional não habilitado' },
  { valor: 'divergencia_atendimento_faturamento', rotulo: 'Divergência atendimento x faturamento' },
]

export const SEVERIDADES = [
  { valor: 'critica', rotulo: 'Crítica', cls: 'st-perdida' },
  { valor: 'alta', rotulo: 'Alta', cls: 'st-warn' },
  { valor: 'media', rotulo: 'Média', cls: 'st-info' },
  { valor: 'baixa', rotulo: 'Baixa', cls: 'st-neutro' },
]

export function severidadeMeta(valor) {
  return SEVERIDADES.find((s) => s.valor === valor) ?? SEVERIDADES[SEVERIDADES.length - 1]
}

// glosa.status (status_glosa) — GlosaEscritaService.STATUS_TERMINAIS = confirmada/
// recuperada/recuperada_parcialmente/negada.
export const STATUS_GLOSA = [
  { valor: 'nova', rotulo: 'Nova', cls: 'st-info' },
  { valor: 'em_analise', rotulo: 'Em análise', cls: 'st-info' },
  { valor: 'recurso_preparacao', rotulo: 'Em recurso', cls: 'st-warn' },
  { valor: 'recurso_enviado', rotulo: 'Recorrida', cls: 'st-info-tracejado' },
  { valor: 'recuperada', rotulo: 'Revertida', cls: 'st-ok' },
  { valor: 'recuperada_parcialmente', rotulo: 'Revertida parcial', cls: 'st-ok' },
  { valor: 'negada', rotulo: 'Perdida', cls: 'st-perdida' },
  { valor: 'confirmada', rotulo: 'Perdida', cls: 'st-perdida' },
]

export function statusGlosaMeta(valor) {
  return STATUS_GLOSA.find((s) => s.valor === valor) ?? { valor, rotulo: valor, cls: 'st-neutro' }
}

export const STATUS_GLOSA_TERMINAIS = new Set(['confirmada', 'recuperada', 'recuperada_parcialmente', 'negada'])

export const RECORRIBILIDADES = [
  { valor: 'recorrivel', rotulo: 'Recorrível' },
  { valor: 'nao_recorrivel', rotulo: 'Não recorrível' },
  { valor: 'necessita_analise', rotulo: 'Necessita análise' },
]

export const CATEGORIAS_MOTIVO = [
  { valor: 'autorizacao', rotulo: 'Autorização' },
  { valor: 'documentacao', rotulo: 'Documentação' },
  { valor: 'codigo_procedimento', rotulo: 'Código/procedimento' },
  { valor: 'elegibilidade', rotulo: 'Elegibilidade' },
  { valor: 'cobertura', rotulo: 'Cobertura' },
  { valor: 'cobranca', rotulo: 'Cobrança' },
  { valor: 'prazo', rotulo: 'Prazo' },
  { valor: 'duplicidade', rotulo: 'Duplicidade' },
  { valor: 'outros', rotulo: 'Outros' },
]

// recurso_glosa.status (status_recurso_glosa).
export const STATUS_RECURSO = [
  { valor: 'rascunho', rotulo: 'Rascunho', cls: 'st-neutro' },
  { valor: 'em_preparacao', rotulo: 'Em preparação', cls: 'st-warn' },
  { valor: 'enviado', rotulo: 'Enviado', cls: 'st-info' },
  { valor: 'aguardando_retorno', rotulo: 'Aguardando retorno', cls: 'st-info' },
  { valor: 'em_analise_convenio', rotulo: 'Em análise pelo convênio', cls: 'st-info' },
  { valor: 'recuperado', rotulo: 'Recuperado', cls: 'st-ok' },
  { valor: 'recuperado_parcialmente', rotulo: 'Recuperado parcialmente', cls: 'st-ok' },
  { valor: 'negado', rotulo: 'Negado', cls: 'st-perdida' },
]

export function statusRecursoMeta(valor) {
  return STATUS_RECURSO.find((s) => s.valor === valor) ?? { valor, rotulo: valor, cls: 'st-neutro' }
}

export const STATUS_RECURSO_EDITAVEIS = new Set(['rascunho', 'em_preparacao'])

export const TIPOS_DOCUMENTO_RECURSO = [
  { valor: 'prontuario', rotulo: 'Prontuário' },
  { valor: 'guia', rotulo: 'Guia' },
  { valor: 'solicitacao_medica', rotulo: 'Solicitação médica' },
  { valor: 'autorizacao', rotulo: 'Autorização' },
  { valor: 'laudo', rotulo: 'Laudo' },
  { valor: 'comprovante', rotulo: 'Comprovante' },
  { valor: 'outro', rotulo: 'Outro' },
]

// corPrazo do backend (GlosaListagemItemDto/GlosaDetalheDto/RecursoGlosaDto):
// verde (>7d) / amarelo (3-7d) / vermelho (<3d) / preto (expirado) / null.
const CORES_PRAZO = { vermelho: 'p-red', amarelo: 'p-amber', verde: 'p-neutral', preto: 'p-off' }

export function corPrazoClasse(cor) {
  return CORES_PRAZO[cor] ?? 'p-off'
}

export const CANAIS_ENVIO = [
  { valor: 'manual', rotulo: 'Manual' },
  { valor: 'portal_convenio', rotulo: 'Portal do convênio' },
  { valor: 'email', rotulo: 'E-mail' },
]

// auditoria_atendimento.status.
export const STATUS_AUDITORIA = [
  { valor: 'aprovado', rotulo: 'Aprovado', cls: 'st-ok' },
  { valor: 'atencao', rotulo: 'Atenção', cls: 'st-warn' },
  { valor: 'bloqueado', rotulo: 'Bloqueado', cls: 'st-perdida' },
]

export function statusAuditoriaMeta(valor) {
  return STATUS_AUDITORIA.find((s) => s.valor === valor) ?? { valor, rotulo: valor, cls: 'st-neutro' }
}

// lote_faturamento.status (status_lote_faturamento).
export const STATUS_LOTE = [
  { valor: 'rascunho', rotulo: 'Rascunho', cls: 'st-neutro' },
  { valor: 'pronto_envio', rotulo: 'Pronto para envio', cls: 'st-info-tracejado' },
  { valor: 'enviado', rotulo: 'Enviado', cls: 'st-info' },
  { valor: 'processando', rotulo: 'Processando', cls: 'st-warn' },
  { valor: 'pago_parcial', rotulo: 'Pago parcialmente', cls: 'st-warn' },
  { valor: 'pago', rotulo: 'Pago', cls: 'st-ok' },
  { valor: 'com_glosas', rotulo: 'Com glosas', cls: 'st-perdida' },
]

export function statusLoteMeta(valor) {
  return STATUS_LOTE.find((s) => s.valor === valor) ?? { valor, rotulo: valor, cls: 'st-neutro' }
}

// Mesmas transições válidas de LoteEscritaService.TRANSICOES (backend) —
// duplicado aqui só pra desenhar os botões certos na tela; a validação de
// verdade continua no backend.
export const TRANSICOES_LOTE = {
  rascunho: [{ valor: 'pronto_envio', rotulo: 'Marcar pronto para envio' }],
  pronto_envio: [
    { valor: 'rascunho', rotulo: 'Voltar para rascunho' },
    { valor: 'enviado', rotulo: 'Marcar como enviado' },
  ],
  enviado: [{ valor: 'processando', rotulo: 'Marcar como processando' }],
  processando: [
    { valor: 'pago', rotulo: 'Marcar como pago' },
    { valor: 'pago_parcial', rotulo: 'Marcar como pago parcialmente' },
    { valor: 'com_glosas', rotulo: 'Marcar com glosas' },
  ],
}
