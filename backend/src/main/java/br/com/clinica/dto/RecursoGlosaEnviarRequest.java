package br.com.clinica.dto;

// canalEnvio: manual / portal_convenio / email (spec seção 10).
public record RecursoGlosaEnviarRequest(String canalEnvio, String protocolo) {
}
