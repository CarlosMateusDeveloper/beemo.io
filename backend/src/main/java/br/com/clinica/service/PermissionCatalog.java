package br.com.clinica.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catálogo único de papéis do MVP e das permissões concedidas por padrão. */
public final class PermissionCatalog {
    public static final String RECEPCIONISTA = "recepcionista";
    public static final String MEDICO = "medico";
    public static final String FINANCEIRO = "financeiro";
    public static final String ADMINISTRADOR = "administrador";

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = build();
    private static final Set<String> ALL_PERMISSIONS = ROLE_PERMISSIONS.values().stream()
            .collect(LinkedHashSet::new, Set::addAll, Set::addAll);

    private PermissionCatalog() {}

    private static Map<String, Set<String>> build() {
        Map<String, Set<String>> roles = new LinkedHashMap<>();
        roles.put(RECEPCIONISTA, set(
                "dashboard.operacional.visualizar", "agenda.visualizar", "agenda.gerenciar",
                "paciente.visualizar", "paciente.cadastrar", "paciente.editar",
                "whatsapp.visualizar", "whatsapp.enviar", "medico.visualizar",
                "oportunidade.visualizar", "oportunidade.gerenciar", "tarefa.visualizar", "tarefa.gerenciar",
                "jornada.executar", "campanha.executar", "caixa.visualizar", "caixa.receber", "caixa.fechar"));
        roles.put(MEDICO, set(
                "dashboard.operacional.visualizar", "agenda.visualizar", "agenda.gerenciar",
                "paciente.visualizar", "prontuario.visualizar", "prontuario.editar", "medico.visualizar", "medico.indicadores.visualizar",
                "oportunidade.visualizar", "tarefa.visualizar", "tarefa.gerenciar", "repasse.proprio.visualizar"));
        roles.put(FINANCEIRO, set(
                "dashboard.financeiro.visualizar", "paciente.financeiro.visualizar",
                "medico.visualizar", "medico.indicadores.visualizar", "oportunidade.visualizar", "tarefa.visualizar", "tarefa.gerenciar",
                "caixa.visualizar", "caixa.receber", "caixa.fechar",
                "financeiro.dre.visualizar", "financeiro.fluxo.visualizar",
                "despesa.visualizar", "despesa.gerenciar", "convenio.gerenciar",
                "glosa.visualizar", "glosa.recorrer", "repasse.todos.visualizar", "auditoria.financeira.visualizar"));
        roles.put(ADMINISTRADOR, set(
                "dashboard.operacional.visualizar", "dashboard.financeiro.visualizar",
                "agenda.visualizar", "agenda.gerenciar", "paciente.visualizar", "paciente.cadastrar", "paciente.editar",
                "whatsapp.visualizar", "whatsapp.enviar", "whatsapp.configurar", "medico.visualizar", "medico.indicadores.visualizar", "medico.gerenciar",
                "oportunidade.visualizar", "oportunidade.gerenciar", "tarefa.visualizar", "tarefa.gerenciar",
                "jornada.executar", "jornada.configurar", "campanha.executar", "campanha.configurar",
                "caixa.visualizar", "caixa.receber", "caixa.fechar", "caixa.reabrir", "pagamento.estornar",
                "financeiro.dre.visualizar", "financeiro.fluxo.visualizar",
                "despesa.visualizar", "despesa.gerenciar", "despesa.aprovar",
                "convenio.gerenciar", "glosa.visualizar", "glosa.recorrer", "glosa.aceitar_perda",
                "repasse.todos.visualizar", "usuario.gerenciar", "permissao.gerenciar", "auditoria.visualizar"));
        return Map.copyOf(roles);
    }

    private static Set<String> set(String... values) { return Set.of(values); }
    public static List<String> roles() { return List.copyOf(ROLE_PERMISSIONS.keySet()); }
    public static Set<String> permissions() { return Set.copyOf(ALL_PERMISSIONS); }
    public static Set<String> defaultsFor(Set<String> roles) {
        Set<String> result = new LinkedHashSet<>();
        roles.forEach(role -> result.addAll(ROLE_PERMISSIONS.getOrDefault(role, Set.of())));
        return result;
    }
    public static boolean validRole(String role) { return ROLE_PERMISSIONS.containsKey(role); }
    public static boolean validPermission(String permission) { return ALL_PERMISSIONS.contains(permission); }
    public static Map<String, Set<String>> matrix() { return ROLE_PERMISSIONS; }
}
