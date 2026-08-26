package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

public record CaixaResponse(TurnoDto turno, List<ReceberItemDto> receber, List<MovimentoItemDto> movimento) {

    public record TurnoDto(
            String dataLabel, String horaAbertura, String operador, String operadorCargo,
            BigDecimal totalHoje, int recebimentosHoje, BigDecimal dinheiro, BigDecimal cartao, BigDecimal pix
    ) {
    }

    public record ReceberItemDto(
            Integer id, String hora, String paciente, String procedimento, String convenio, BigDecimal valor, boolean pago
    ) {
    }

    public record MovimentoItemDto(
            Integer id, String hora, String desc, String sub, String metodo, String metodoLabel,
            BigDecimal valor, String tipo, String quem
    ) {
    }
}
