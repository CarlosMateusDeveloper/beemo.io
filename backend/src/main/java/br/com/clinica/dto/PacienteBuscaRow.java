package br.com.clinica.dto;

import java.time.LocalDate;

// Linha crua da query de busca (cpf/ddd/numero sem máscara/formatação) —
// PacienteBuscaService.buscar() transforma isso em PacienteResumoDto
// (cpf mascarado, telefone formatado) antes de devolver pro controller.
public record PacienteBuscaRow(
        Integer id, String nome, String cpf, String ddd, String numero, LocalDate dataNascimento, String convenio
) {
}
