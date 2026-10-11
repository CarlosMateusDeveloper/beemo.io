package br.com.clinica.integration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ClinicaIntegrationAdapters {
    private final Map<String, ClinicaIntegrationAdapter> adapters;

    public ClinicaIntegrationAdapters(List<ClinicaIntegrationAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                ClinicaIntegrationAdapter::codigo, Function.identity()));
    }

    public ClinicaIntegrationAdapter obter(String codigo) {
        var adapter = adapters.get(codigo);
        if (adapter == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Adaptador de integração não suportado");
        return adapter;
    }

    public List<String> codigos() { return adapters.keySet().stream().sorted().toList(); }
}
