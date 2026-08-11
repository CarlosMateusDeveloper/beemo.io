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

export const MEDICOS = [
  { id: 1, nome: 'Ana Beatriz Rocha', iniciais: 'AR', especialidade: 'Cardiologia', status: 'ativo', crm: '128.446',
    atendimentos: 92, novos: 34, retornos: 58, receitaLiquida: 24180, receitaBruta: 40300, repasse: 40,
    horariosUsados: 92, horariosAbertos: 100, noShow: 9, atrasoMedio: 6, pontualidade: 94, retorno: 70,
    horasPerdidas: 1, proximoHorario: 'Hoje 15:20', proximoHorarioNota: 'consultório 4' },
  { id: 2, nome: 'Carlos Eduardo Nunes', iniciais: 'CN', especialidade: 'Ortopedia', status: 'ativo', crm: '96.201',
    atendimentos: 78, novos: 41, retornos: 37, receitaLiquida: 19740, receitaBruta: 32900, repasse: 40,
    horariosUsados: 84, horariosAbertos: 100, noShow: 14, atrasoMedio: 12, pontualidade: 86, retorno: 50,
    horasPerdidas: 2.5, proximoHorario: '12/08 09:40', proximoHorarioNota: 'consultório 2' },
  { id: 3, nome: 'Helena Prado Vasconcelos', iniciais: 'HV', especialidade: 'Dermatologia', status: 'ferias', crm: '143.882',
    atendimentos: 61, novos: 26, retornos: 35, receitaLiquida: 13900, receitaBruta: 25280, repasse: 45,
    horariosUsados: 65, horariosAbertos: 100, noShow: 11, atrasoMedio: 9, pontualidade: 88, retorno: 56,
    horasPerdidas: 3, proximoHorario: '25/08 08:00', proximoHorarioNota: 'retorna das férias' },
  { id: 4, nome: 'Marcelo Tavares Lima', iniciais: 'ML', especialidade: 'Clínica Geral', status: 'ativo', crm: '110.735',
    atendimentos: 74, novos: 48, retornos: 26, receitaLiquida: 12640, receitaBruta: 19450, repasse: 35,
    horariosUsados: 71, horariosAbertos: 100, noShow: 22, atrasoMedio: 20, pontualidade: 62, retorno: 27,
    horasPerdidas: 4.5, proximoHorario: 'Hoje 16:40', proximoHorarioNota: 'consultório 1' },
  { id: 5, nome: 'Priscila Amorim Sá', iniciais: 'PS', especialidade: 'Pediatria', status: 'ativo', crm: '87.019',
    atendimentos: 88, novos: 33, retornos: 55, receitaLiquida: 21300, receitaBruta: 36720, repasse: 42,
    horariosUsados: 88, horariosAbertos: 100, noShow: 11, atrasoMedio: 8, pontualidade: 90, retorno: 66,
    horasPerdidas: 1, proximoHorario: 'Amanhã 08:00', proximoHorarioNota: 'consultório 3' },
  { id: 6, nome: 'Rafael Okamoto Menezes', iniciais: 'RM', especialidade: 'Ginecologia', status: 'afastado', crm: '134.560',
    atendimentos: 66, novos: 29, retornos: 37, receitaLiquida: 16480, receitaBruta: 27470, repasse: 40,
    horariosUsados: 68, horariosAbertos: 100, noShow: 16, atrasoMedio: 16, pontualidade: 72, retorno: 55,
    horasPerdidas: 2, proximoHorario: null, proximoHorarioNota: 'licença médica' },
  { id: 7, nome: 'Sofia Nakamura', iniciais: 'SN', especialidade: 'Otorrinolaringologia', status: 'ativo', crm: '151.904',
    atendimentos: 7, novos: 6, retornos: 1, receitaLiquida: 1980, receitaBruta: 3300, repasse: 40,
    horariosUsados: 9, horariosAbertos: 40, noShow: 0, atrasoMedio: 2, pontualidade: 100, retorno: 0,
    horasPerdidas: 0, proximoHorario: '12/08 11:00', proximoHorarioNota: 'entrou em 28/07' },
  { id: 8, nome: 'Débora Sant’Anna', iniciais: 'DS', especialidade: 'Endocrinologia', status: 'desligado', crm: '102.318',
    atendimentos: 0, novos: 0, retornos: 0, receitaLiquida: 0, receitaBruta: 0, repasse: 0,
    horariosUsados: 0, horariosAbertos: 0, noShow: 0, atrasoMedio: 0, pontualidade: 0, retorno: 0,
    horasPerdidas: 0, proximoHorario: null, proximoHorarioNota: 'desligada em 30/06' },
]

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
