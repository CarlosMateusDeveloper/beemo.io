package br.com.clinica.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// origem "importada" vs "manual" (spec seção 3) — dataGlosa é opcional
// (default hoje), o resto é obrigatório.
public record GlosaCriarRequest(
        Integer idFatura,
        Integer idConvenio,
        String motivo,
        String codigoMotivo,
        BigDecimal valor,
        BigDecimal valorFaturado,
        LocalDate dataGlosa,
        LocalDate prazoRecurso,
        String origem
) {
}
