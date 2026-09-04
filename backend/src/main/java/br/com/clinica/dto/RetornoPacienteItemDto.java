package br.com.clinica.dto;

import java.math.BigDecimal;

// Linha da lista expandida de um grupo. "contexto" varia por grupo ("4
// sessões seguidas sem retorno agendado", "retorno vencido há 45 dias",
// "exame pedido em 12/06") — texto já pronto, montado no service que sabe
// qual grupo é.
public record RetornoPacienteItemDto(
        Integer idPaciente, String nome, int idade,
        String ultimaConsultaData, String ultimaConsultaEspecialidade,
        String contexto, String medico, BigDecimal valorEstimado,
        String telefone, boolean whatsapp
) {
}
