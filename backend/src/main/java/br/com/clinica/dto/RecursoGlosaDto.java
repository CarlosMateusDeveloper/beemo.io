package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecursoGlosaDto(
        Integer id,
        Integer idGlosa,
        String status,
        String justificativa,
        String prazoLimiteTxt,
        Integer diasRestantes,
        String corPrazo,
        String responsavelNome,
        String canalEnvio,
        String protocolo,
        String enviadoEmTxt,
        String respondidoEmTxt,
        BigDecimal valorRecuperado,
        BigDecimal valorNaoRecuperado,
        String motivoNegativa,
        String documentoRespostaUrl,
        List<DocumentoDto> documentos,
        ChecklistDto checklist
) {

    public record DocumentoDto(Integer id, String tipo, Integer idDocumentoAnexo, String descricao) {
    }

    // Seção 8 da spec — o que falta pra habilitar "Enviar recurso".
    public record ChecklistDto(
            boolean motivoAnalisado,
            boolean justificativaPreenchida,
            boolean documentacaoAnexada,
            boolean evidenciasConferidas,
            boolean responsavelDefinido,
            boolean prazoValido,
            boolean podeEnviar,
            List<String> pendencias
    ) {
    }
}
