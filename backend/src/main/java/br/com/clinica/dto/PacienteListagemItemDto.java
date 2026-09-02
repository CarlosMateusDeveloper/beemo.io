package br.com.clinica.dto;

// Linha da tabela /pacientes. "sexo" não existe no schema (paciente não tem
// essa coluna) e por isso não aparece aqui — removido também da UI.
// status: "ok" (última consulta não cancelada há <=6 meses), "risk" (6-12
// meses), "off" (nunca compareceu ou >12 meses), "inc" (sem documento_anexo,
// tem prioridade sobre os outros — mesma regra de PacienteKpiService).
public record PacienteListagemItemDto(
        Integer id,
        String nome,
        String cpfMascarado,
        String telefone,
        boolean whatsapp,
        String convenio,
        int idade,
        String ultimaData,
        String ultimaTxt,
        String ultimaEspecialidade,
        String proximaData,
        String proximaTxt,
        String status
) {
}
