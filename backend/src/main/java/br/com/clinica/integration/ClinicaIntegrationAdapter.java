package br.com.clinica.integration;

import br.com.clinica.dto.IntegracaoEventoRequest;

/** Traduz o contrato de um fornecedor para o contrato canônico do Clinisis. */
public interface ClinicaIntegrationAdapter {
    String codigo();
    IntegracaoEventoRequest adaptar(IntegracaoEventoRequest evento);
}
