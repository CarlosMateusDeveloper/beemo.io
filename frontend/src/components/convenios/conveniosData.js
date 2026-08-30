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
