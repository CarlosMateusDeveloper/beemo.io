package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Cabeçalho de /retorno: "Deveriam ter voltado — N pacientes — R$ X em
// consultas não agendadas". valorEstimado é sempre estimativa (N pacientes x
// ticket médio real da especialidade, calculado a partir de fatura.valor —
// não é preço configurado, é média histórica de verdade).
public record RetornoResumoDto(int totalPendentes, BigDecimal valorEstimado, List<RetornoGrupoDto> grupos) {
}
