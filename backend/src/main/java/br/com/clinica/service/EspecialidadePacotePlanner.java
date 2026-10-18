package br.com.clinica.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Calcula a aplicação sem efeitos colaterais, para que preview e persistência usem a mesma regra. */
public final class EspecialidadePacotePlanner {
    public record Instalado(String chave, Map<String,Object> base, Map<String,Object> local,
            int versaoOrigem, boolean personalizado, boolean ativo) {}
    public record Item(String chave, String tipo, String nome, String acao, String motivo,
            Map<String,Object> configuracao, Map<String,Object> configuracaoLocal) {}

    public List<Item> planejar(List<Map<String,Object>> componentes, Map<String,Instalado> instalados,
            Set<String> selecionados, int versaoDestino) {
        var resultado = new ArrayList<Item>();
        var chaves = new HashSet<String>();
        for (var componente : componentes) {
            String chave = texto(componente, "chave");
            String tipo = texto(componente, "tipo");
            String nome = texto(componente, "nome");
            if (!chaves.add(chave)) throw new IllegalArgumentException("O pacote contém componentes duplicados: " + chave);
            if (!selecionados.contains(chave)) continue;
            var instalado = instalados.get(chave);
            String acao;
            String motivo;
            Map<String,Object> local;
            if (instalado == null) {
                acao = "CRIAR"; motivo = "Novo componente"; local = componente;
            } else if (instalado.personalizado()) {
                acao = "PRESERVAR_PERSONALIZACAO";
                motivo = "A configuração local será mantida e a nova base ficará disponível para comparação";
                local = instalado.local();
            } else if (instalado.ativo() && instalado.versaoOrigem() == versaoDestino
                    && instalado.base().equals(componente)) {
                acao = "SEM_ALTERACAO"; motivo = "Já aplicado nesta versão"; local = instalado.local();
            } else {
                acao = "ATUALIZAR"; motivo = "Atualizar para a versão selecionada"; local = componente;
            }
            resultado.add(new Item(chave, tipo, nome, acao, motivo,
                    new LinkedHashMap<>(componente), new LinkedHashMap<>(local)));
        }
        return resultado;
    }

    private static String texto(Map<String,Object> value, String field) {
        Object raw = value.get(field);
        if (!(raw instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Componente sem " + field);
        return text.strip();
    }
}
