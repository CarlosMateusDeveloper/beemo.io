package br.com.clinica.dto;

// Cartão da fila do dia (kanban /pacientes). "pendencia" (badge da coluna
// Concluído) não existe em nenhuma tabela do schema hoje, por isso não
// aparece aqui — o frontend já cai de volta pro badge de convênio quando
// não há pendência.
public record PacienteFilaItemDto(
        String coluna,
        String nome,
        String hora,
        String especialidade,
        Integer esperaMin,
        String convenio
) {
}
