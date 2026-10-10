package br.com.clinica.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Dados administrativos mínimos de um atendimento para cobrança. */
public record PacienteCobrancaDto(
        Integer pacienteId,
        String paciente,
        Integer consultaId,
        LocalDate data,
        LocalTime hora,
        String procedimento,
        String convenio,
        BigDecimal valor,
        String situacaoPagamento
) {}
