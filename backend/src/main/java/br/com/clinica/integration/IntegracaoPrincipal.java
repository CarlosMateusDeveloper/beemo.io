package br.com.clinica.integration;

import java.util.Set;

public record IntegracaoPrincipal(long credencialId, int tenantId, long fonteId, Set<String> escopos) {}
