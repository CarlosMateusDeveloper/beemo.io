package br.com.clinica.service;

import br.com.clinica.dto.GlosaListagemItemDto;
import br.com.clinica.model.Glosa;
import br.com.clinica.repository.GlosaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Fila de glosas (spec seção 3), ordenada por prazo crescente por padrão —
// filtro/paginação em GlosaRepository.buscar (JPQL simples sobre a tabela
// glosa); os dados de exibição (paciente/convênio/procedimento/responsável/
// recurso atual) são enriquecidos numa segunda query em lote pra evitar N+1.
@Service
public class GlosaListagemService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GlosaRepository repository;
    private final EntityManager entityManager;

    public GlosaListagemService(GlosaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public Page<GlosaListagemItemDto> listar(
            String status, Integer idConvenio, Integer idUsuarioResponsavel, String recorribilidade,
            LocalDate prazoAte, Pageable pageable
    ) {
        Page<Glosa> pagina = repository.buscar(status, idConvenio, idUsuarioResponsavel, recorribilidade, prazoAte, pageable);
        Map<Integer, EnriquecimentoRow> enriquecimento = buscarEnriquecimento(pagina.getContent());

        return pagina.map(g -> {
            EnriquecimentoRow e = enriquecimento.get(g.getId());
            Integer diasRestantes = PrazoClassificador.diasRestantes(g.getPrazoRecurso());
            return new GlosaListagemItemDto(
                    g.getId(),
                    e == null ? null : e.pacienteNome,
                    e == null ? null : e.convenioNome,
                    e == null ? null : e.procedimento,
                    g.getValor(),
                    g.getDataGlosa().format(DIA_MES_ANO),
                    g.getPrazoRecurso() == null ? "—" : g.getPrazoRecurso().format(DIA_MES_ANO),
                    diasRestantes,
                    PrazoClassificador.cor(diasRestantes),
                    g.getStatus(),
                    e == null ? null : e.statusRecursoAtual,
                    e == null ? null : e.responsavelNome
            );
        });
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, EnriquecimentoRow> buscarEnriquecimento(List<Glosa> glosas) {
        Map<Integer, EnriquecimentoRow> resultado = new HashMap<>();
        if (glosas.isEmpty()) return resultado;

        List<Integer> ids = glosas.stream().map(Glosa::getId).toList();
        Query query = entityManager.createNativeQuery(
                "SELECT g.id_glosa, p.nome, cv.nome, c.tipo::text, u.nome, " +
                        "  (SELECT rg.status::text FROM recurso_glosa rg WHERE rg.id_glosa = g.id_glosa " +
                        "     ORDER BY rg.criado_em DESC LIMIT 1) " +
                        "FROM glosa g " +
                        "JOIN fatura f ON f.id_fatura = g.id_fatura " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "JOIN convenio cv ON cv.id_convenio = g.id_convenio " +
                        "LEFT JOIN usuario u ON u.id = g.id_usuario_responsavel " +
                        "WHERE g.id_glosa IN :ids"
        );
        query.setParameter("ids", ids);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            EnriquecimentoRow row = new EnriquecimentoRow();
            Integer idGlosa = ((Number) l[0]).intValue();
            row.pacienteNome = (String) l[1];
            row.convenioNome = (String) l[2];
            row.procedimento = (String) l[3];
            row.responsavelNome = (String) l[4];
            row.statusRecursoAtual = (String) l[5];
            resultado.put(idGlosa, row);
        }
        return resultado;
    }

    private static class EnriquecimentoRow {
        String pacienteNome;
        String convenioNome;
        String procedimento;
        String responsavelNome;
        String statusRecursoAtual;
    }
}
