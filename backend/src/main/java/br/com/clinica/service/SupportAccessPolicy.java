package br.com.clinica.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Escopos pequenos e explícitos que podem compor uma concessão temporária. */
public final class SupportAccessPolicy {
    public static final String LEITURA_OPERACIONAL = "LEITURA_OPERACIONAL";
    public static final String DIAGNOSTICO_MENSAGENS = "DIAGNOSTICO_MENSAGENS";
    public static final String ALTERACAO_OPERACIONAL = "ALTERACAO_OPERACIONAL";
    public static final String LEITURA_FINANCEIRA = "LEITURA_FINANCEIRA";
    public static final String EXPORTACAO = "EXPORTACAO";

    private static final Map<String,Set<String>> SCOPES = build();
    private SupportAccessPolicy() {}

    private static Map<String,Set<String>> build() {
        var scopes = new LinkedHashMap<String,Set<String>>();
        scopes.put(LEITURA_OPERACIONAL, Set.of(
                "dashboard.operacional.visualizar","agenda.visualizar","paciente.visualizar",
                "medico.visualizar","oportunidade.visualizar","tarefa.visualizar",
                "suporte.ajuda.visualizar","suporte.chamado.visualizar","suporte.chamado.gerenciar"));
        scopes.put(DIAGNOSTICO_MENSAGENS, Set.of("whatsapp.visualizar","mensageria.visualizar"));
        scopes.put(ALTERACAO_OPERACIONAL, Set.of("agenda.gerenciar","paciente.editar","tarefa.gerenciar"));
        scopes.put(LEITURA_FINANCEIRA, Set.of("dashboard.financeiro.visualizar","caixa.visualizar",
                "financeiro.dre.visualizar","financeiro.fluxo.visualizar","despesa.visualizar",
                "auditoria.financeira.visualizar"));
        scopes.put(EXPORTACAO, Set.of("suporte.exportar"));
        return Map.copyOf(scopes);
    }

    public static boolean valid(String scope) { return SCOPES.containsKey(scope); }
    public static Set<String> scopes() { return SCOPES.keySet(); }
    public static Set<String> permissions(Set<String> scopes) {
        var result = new LinkedHashSet<String>();
        scopes.forEach(scope -> result.addAll(SCOPES.getOrDefault(scope,Set.of())));
        return Set.copyOf(result);
    }
    public static Map<String,Set<String>> matrix() { return SCOPES; }
}
