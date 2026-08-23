// Dataset mock da tela /medicos. Sem endpoint de backend ainda — cada tela
// deste dashboard deve futuramente consumir GET /medicos?periodo=&status=&especialidades=
// e devolver os campos abaixo já calculados pelo servidor.

export const VOLUME_MINIMO_PADRAO = 10
export const TICKET_MEDIO = 300

export const ESPECIALIDADES = [
  'Cardiologia', 'Clínica Geral', 'Dermatologia', 'Endocrinologia',
  'Ginecologia', 'Ortopedia', 'Otorrinolaringologia', 'Pediatria',
]

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']

// Fator de escala por período + desvio determinístico por médico. Existe só
// para o mock parecer plausível ao trocar de período; em produção os
// números já vêm agregados do backend para o período pedido.
const FATORES_PERIODO = { Hoje: 0.05, '7 dias': 0.26, Mês: 1, Personalizado: 1.55 }

// Sem médicos cadastrados ainda — GET /medicos deve popular esta lista.
export const MEDICOS = []

export function brl(n) {
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 })
}

export function pct(n) {
  return Math.round(n) + '%'
}

export function horasFmt(n) {
  return (Number.isInteger(n) ? String(n) : n.toFixed(1).replace('.', ',')) + 'h'
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v))
}

export function escalarMedico(medico, periodo) {
  const f = FATORES_PERIODO[periodo] ?? 1
  if (f === 1) return medico
  const j = ((medico.id * 7) % 5) - 2
  const atendimentos = Math.max(0, Math.round(medico.atendimentos * f))
  return {
    ...medico,
    atendimentos,
    novos: Math.max(0, Math.round(medico.novos * f)),
    retornos: Math.max(0, Math.round(medico.retornos * f)),
    receitaLiquida: Math.round(medico.receitaLiquida * f),
    receitaBruta: Math.round(medico.receitaBruta * f),
    horariosUsados: Math.round(medico.horariosUsados * f),
    horariosAbertos: Math.round(medico.horariosAbertos * f),
    horasPerdidas: Math.round(medico.horasPerdidas * f * 10) / 10,
    noShow: atendimentos ? clamp(medico.noShow + j, 0, 40) : 0,
    atrasoMedio: atendimentos ? Math.max(0, medico.atrasoMedio + j) : 0,
    pontualidade: atendimentos ? clamp(medico.pontualidade - j, 0, 100) : 0,
    retorno: atendimentos ? clamp(medico.retorno + j, 0, 100) : 0,
  }
}
