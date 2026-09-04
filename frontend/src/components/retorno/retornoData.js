// Rótulos e formatters da tela /retorno. Dado real vem de GET /api/retorno/*
// (ver ./api.js) — nada aqui é mock.

export const GRUPOS = [
  { valor: 'tratamento_interrompido', rotulo: 'Tratamento interrompido' },
  { valor: 'retorno_medico', rotulo: 'Retorno pedido pelo médico' },
  { valor: 'exame_pendente', rotulo: 'Exame pedido e não feito' },
  { valor: 'ritmo_quebrado', rotulo: 'Ritmo próprio quebrado' },
]

export function rotuloGrupo(valor) {
  return GRUPOS.find((g) => g.valor === valor)?.rotulo ?? valor
}

export function brl(valor) {
  return Number(valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function iniciaisDe(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}
