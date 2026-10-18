package br.com.clinica.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EspecialidadePacotePlannerTest {
    private final EspecialidadePacotePlanner planner = new EspecialidadePacotePlanner();
    private final Map<String,Object> consulta = Map.of("chave","consulta","tipo","MODELO_ATENDIMENTO","nome","Consulta","duracaoMinutos",30);
    private final Map<String,Object> fluxo = Map.of("chave","fluxo","tipo","FLUXO_ACOMPANHAMENTO","nome","Pós-atendimento");

    @Test void aplicaSomenteComponentesSelecionados() {
        var plan = planner.planejar(List.of(consulta,fluxo),Map.of(),Set.of("consulta"),1);
        assertEquals(1,plan.size());
        assertEquals("consulta",plan.getFirst().chave());
        assertEquals("CRIAR",plan.getFirst().acao());
    }

    @Test void reaplicarMesmaVersaoNaoDuplicaNemAltera() {
        var installed = new EspecialidadePacotePlanner.Instalado("consulta",consulta,consulta,1,false,true);
        var plan = planner.planejar(List.of(consulta),Map.of("consulta",installed),Set.of("consulta"),1);
        assertEquals("SEM_ALTERACAO",plan.getFirst().acao());
    }

    @Test void atualizacaoPreservaPersonalizacaoLocal() {
        var local = Map.<String,Object>of("chave","consulta","tipo","MODELO_ATENDIMENTO","nome","Consulta longa","duracaoMinutos",60);
        var installed = new EspecialidadePacotePlanner.Instalado("consulta",consulta,local,1,true,true);
        var next = Map.<String,Object>of("chave","consulta","tipo","MODELO_ATENDIMENTO","nome","Consulta","duracaoMinutos",45);
        var plan = planner.planejar(List.of(next),Map.of("consulta",installed),Set.of("consulta"),2);
        assertEquals("PRESERVAR_PERSONALIZACAO",plan.getFirst().acao());
        assertEquals(60,plan.getFirst().configuracaoLocal().get("duracaoMinutos"));
        assertEquals(45,plan.getFirst().configuracao().get("duracaoMinutos"));
    }

    @Test void novaVersaoAtualizaComponenteSemPersonalizacao() {
        var installed = new EspecialidadePacotePlanner.Instalado("consulta",consulta,consulta,1,false,true);
        var next = Map.<String,Object>of("chave","consulta","tipo","MODELO_ATENDIMENTO","nome","Consulta","duracaoMinutos",45);
        assertEquals("ATUALIZAR",planner.planejar(List.of(next),Map.of("consulta",installed),Set.of("consulta"),2).getFirst().acao());
    }
}
