package br.com.clinica.dto;

// KPIs da tela /pacientes (ver docs/specs/pacientes.md, seção "Os 4 KPIs").
// "Novos" e "risco" usam a mesma definição de primeira/última consulta que
// o DashboardService; "incompletos" usa apenas ausência de documento_anexo,
// já que cpf/ddd/numero são NOT NULL no schema e id_convenio nulo é um
// estado válido (paciente particular), não um cadastro incompleto.
public record PacientesKpisResponse(
        int totalCadastros,
        int baseAtiva,
        int novos,
        double novosDeltaPct,
        double novosCanalWhatsappPct,
        int risco,
        int incompletos,
        int incompletosSemDocumento
) {
}
