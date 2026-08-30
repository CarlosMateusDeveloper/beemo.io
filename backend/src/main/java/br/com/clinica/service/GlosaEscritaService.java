package br.com.clinica.service;

import br.com.clinica.dto.GlosaClassificarRequest;
import br.com.clinica.dto.GlosaCriarRequest;
import br.com.clinica.dto.GlosaResponsavelRequest;
import br.com.clinica.model.Glosa;
import br.com.clinica.model.GlosaHistorico;
import br.com.clinica.repository.GlosaHistoricoRepository;
import br.com.clinica.repository.GlosaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

// Escrita de glosa (spec seções 3-5). glosa.status é enum nativo do Postgres
// (status_glosa); escrita passa por SQL nativo com CAST explícito, mesmo
// motivo de Fatura/Exame/AutorizacaoConvenio (sem stringtype=unspecified na
// URL JDBC). recorribilidade/categoriaMotivo/origem são VARCHAR+CHECK, não
// enum — repository.save() é seguro pra esses.
@Service
public class GlosaEscritaService {

    private static final Set<String> STATUS_TERMINAIS = Set.of("confirmada", "recuperada", "recuperada_parcialmente", "negada");
    private static final Set<String> RECORRIBILIDADES_VALIDAS = Set.of("recorrivel", "nao_recorrivel", "necessita_analise");
    private static final Set<String> CATEGORIAS_VALIDAS = Set.of(
            "autorizacao", "documentacao", "codigo_procedimento", "elegibilidade",
            "cobertura", "cobranca", "prazo", "duplicidade", "outros"
    );

    private final GlosaRepository repository;
    private final GlosaHistoricoRepository historicoRepository;
    private final EntityManager entityManager;

    public GlosaEscritaService(
            GlosaRepository repository, GlosaHistoricoRepository historicoRepository, EntityManager entityManager
    ) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Glosa criar(GlosaCriarRequest request) {
        if (request.idFatura() == null || request.idConvenio() == null
                || request.motivo() == null || request.motivo().isBlank() || request.valor() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idFatura, idConvenio, motivo e valor são obrigatórios");
        }
        String origem = request.origem() == null ? "manual" : request.origem();
        if (!Set.of("importada", "manual").contains(origem)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "origem inválida: " + origem);
        }
        LocalDate dataGlosa = request.dataGlosa() == null ? LocalDate.now() : request.dataGlosa();

        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO glosa (id_fatura, id_convenio, motivo, codigo_motivo, valor, valor_faturado, " +
                        "  data_glosa, prazo_recurso, origem, status) " +
                        "VALUES (:idFatura, :idConvenio, :motivo, :codigoMotivo, :valor, :valorFaturado, " +
                        "  :dataGlosa, :prazoRecurso, :origem, CAST('nova' AS status_glosa)) " +
                        "RETURNING id_glosa"
        )
                .setParameter("idFatura", request.idFatura())
                .setParameter("idConvenio", request.idConvenio())
                .setParameter("motivo", request.motivo())
                .setParameter("codigoMotivo", request.codigoMotivo())
                .setParameter("valor", request.valor())
                .setParameter("valorFaturado", request.valorFaturado())
                .setParameter("dataGlosa", dataGlosa)
                .setParameter("prazoRecurso", request.prazoRecurso())
                .setParameter("origem", origem)
                .getSingleResult()).intValue();

        registrarHistorico(id, "importada".equals(origem) ? "Glosa importada do retorno do convênio" : "Glosa criada manualmente");
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Glosa classificar(Integer id, GlosaClassificarRequest request) {
        Glosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (STATUS_TERMINAIS.contains(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Glosa já concluída (" + existente.getStatus() + ") não pode ser reclassificada");
        }
        if (request.recorribilidade() != null && !RECORRIBILIDADES_VALIDAS.contains(request.recorribilidade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recorribilidade inválida: " + request.recorribilidade());
        }
        if (request.categoriaMotivo() != null && !CATEGORIAS_VALIDAS.contains(request.categoriaMotivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoriaMotivo inválida: " + request.categoriaMotivo());
        }

        existente.setRecorribilidade(request.recorribilidade());
        existente.setCategoriaMotivo(request.categoriaMotivo());
        boolean primeiraClassificacao = "nova".equals(existente.getStatus());
        repository.save(existente);

        if (primeiraClassificacao) {
            atualizarStatus(id, "em_analise");
        }
        registrarHistorico(id, "Classificada como " + rotuloRecorribilidade(request.recorribilidade()));
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Glosa aceitar(Integer id) {
        Glosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (STATUS_TERMINAIS.contains(existente.getStatus()) || "recurso_enviado".equals(existente.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Glosa em \"" + existente.getStatus() + "\" não pode ser aceita — já concluída ou com recurso em andamento"
            );
        }
        atualizarStatus(id, "confirmada");
        registrarHistorico(id, "Glosa aceita — sem recurso");
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Glosa alterarResponsavel(Integer id, GlosaResponsavelRequest request) {
        Glosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existente.setIdUsuarioResponsavel(request.idUsuarioResponsavel());
        repository.save(existente);
        registrarHistorico(id, "Responsável alterado");
        return existente;
    }

    // Usado também por RecursoGlosaEscritaService pra espelhar o desfecho do
    // recurso de volta na glosa (criar recurso -> recurso_preparacao, enviar
    // -> recurso_enviado, resultado -> recuperada/recuperada_parcialmente/negada).
    @Transactional
    public void atualizarStatus(Integer id, String novoStatus) {
        entityManager.createNativeQuery(
                "UPDATE glosa SET status = CAST(:status AS status_glosa) WHERE id_glosa = :id"
        ).setParameter("status", novoStatus).setParameter("id", id).executeUpdate();
    }

    void registrarHistorico(Integer idGlosa, String evento) {
        GlosaHistorico historico = new GlosaHistorico();
        historico.setIdGlosa(idGlosa);
        historico.setEvento(evento);
        historico.setIdUsuario(usuarioAtualId());
        historico.setCriadoEm(OffsetDateTime.now());
        historicoRepository.save(historico);
    }

    private Integer usuarioAtualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer id)) return null;
        return id;
    }

    private String rotuloRecorribilidade(String valor) {
        if (valor == null) return "não classificada";
        return switch (valor) {
            case "recorrivel" -> "recorrível";
            case "nao_recorrivel" -> "não recorrível";
            case "necessita_analise" -> "necessita análise";
            default -> valor;
        };
    }
}
