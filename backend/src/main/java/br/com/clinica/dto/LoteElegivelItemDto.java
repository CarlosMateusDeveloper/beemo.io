package br.com.clinica.dto;

import java.math.BigDecimal;

// Passo 2 do wizard (revisão humana): um atendimento faturado, ainda sem
// lote, elegível pra entrar num novo lote deste convênio. statusAuditoria:
// null (nunca avaliado — atendimento anterior ao motor de auditoria
// existir), "atencao" (avaliado com ressalva — a pessoa decide se tira da
// seleção) ou "aprovado". "bloqueado" nunca aparece aqui — já é excluído
// na consulta (ver LoteSugestaoService).
public record LoteElegivelItemDto(
        Integer idFatura, String pacienteNome, String dataAtendimento, String tipo, BigDecimal valor,
        String statusAuditoria
) {
}
