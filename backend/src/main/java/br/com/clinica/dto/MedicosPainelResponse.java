package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Painel da tela /medicos: uma linha por médico, já com o período aplicado.
// Filtro por status/especialidade e agregação dos KPIs ficam no frontend
// (mesmo padrão de /pacientes) — por isso os campos de contagem bruta
// (pacientesTotal/pacientesRetorno, horariosUsados/horariosAbertos,
// consultasComInicio/consultasPontuais) vêm crus, não só o percentual, pra
// dar pra agregar corretamente um subconjunto filtrado sem reabrir o banco.
//
// receitaLiquida/atrasoMedioMin/pontualidadePct/horasPerdidas vêm nulos (ou
// com amostra zero) enquanto repasse_percentual e consulta.iniciado_em/
// cancelado_em não tiverem dado real — essas colunas só passam a ser
// preenchidas pelo agenda-service a partir de agora (issue #17).
public record MedicosPainelResponse(
        boolean empty,
        List<MedicoLinhaDto> medicos
) {

    public record MedicoLinhaDto(
            Integer id, String nome, String crm, Integer especialidadeId, String especialidade, String status,
            int atendimentos, int novos, int retornos,
            BigDecimal receitaBruta,
            BigDecimal repassePercentual, BigDecimal receitaLiquida,
            int horariosUsados, int horariosAbertos,
            double noShowPct,
            int pacientesTotal, int pacientesRetorno,
            int consultasComInicio, int consultasPontuais, double atrasoMedioMin,
            double horasPerdidas,
            String proximoHorarioTxt, String proximoTipo
    ) {
    }
}
