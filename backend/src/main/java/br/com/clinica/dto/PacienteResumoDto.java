package br.com.clinica.dto;

import java.time.LocalDate;

// Linha de GET /api/pacientes (issue #11: "nunca expor CPF completo fora do
// necessário na resposta da API") — cpf mascarado aqui; GET /api/pacientes/{id}
// continua devolvendo o cadastro completo (necessário pra editar a ficha).
public record PacienteResumoDto(
        Integer id, String nome, String cpfMascarado, String telefone, LocalDate dataNascimento, String convenio
) {
}
