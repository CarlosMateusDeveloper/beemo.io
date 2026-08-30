package br.com.clinica.service;

import br.com.clinica.dto.RecursoGlosaDocumentoRequest;
import br.com.clinica.dto.RecursoGlosaEnviarRequest;
import br.com.clinica.dto.RecursoGlosaRequest;
import br.com.clinica.dto.RecursoGlosaResultadoRequest;
import br.com.clinica.model.Glosa;
import br.com.clinica.model.RecursoGlosa;
import br.com.clinica.model.RecursoGlosaDocumento;
import br.com.clinica.repository.GlosaRepository;
import br.com.clinica.repository.RecursoGlosaDocumentoRepository;
import br.com.clinica.repository.RecursoGlosaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Escrita do recurso de glosa (spec seções 6-12). recurso_glosa.status e
// canal_envio são enums nativos do Postgres; escrita passa por SQL nativo
// com CAST explícito, mesmo motivo de GlosaEscritaService. Espelha o
// desfecho de volta em glosa.status (via GlosaEscritaService) a cada
// transição relevante — glosa.status fica sempre coerente com o recurso
// mais recente.
@Service
public class RecursoGlosaEscritaService {

    private static final Set<String> STATUS_EDITAVEIS = Set.of("rascunho", "em_preparacao");
    private static final Set<String> CANAIS_VALIDOS = Set.of("manual", "portal_convenio", "email");
    private static final Set<String> TIPOS_DOCUMENTO_VALIDOS = Set.of(
            "prontuario", "guia", "solicitacao_medica", "autorizacao", "laudo", "comprovante", "outro"
    );

    private final RecursoGlosaRepository repository;
    private final RecursoGlosaDocumentoRepository documentoRepository;
    private final GlosaRepository glosaRepository;
    private final GlosaEscritaService glosaEscritaService;
    private final EntityManager entityManager;

    public RecursoGlosaEscritaService(
            RecursoGlosaRepository repository, RecursoGlosaDocumentoRepository documentoRepository,
            GlosaRepository glosaRepository, GlosaEscritaService glosaEscritaService, EntityManager entityManager
    ) {
        this.repository = repository;
        this.documentoRepository = documentoRepository;
        this.glosaRepository = glosaRepository;
        this.glosaEscritaService = glosaEscritaService;
        this.entityManager = entityManager;
    }

    @Transactional
    public RecursoGlosa criar(Integer idGlosa, RecursoGlosaRequest request) {
        Glosa glosa = glosaRepository.findById(idGlosa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Glosa não encontrada"));
        if ("nao_recorrivel".equals(glosa.getRecorribilidade())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Glosa classificada como não recorrível — não é possível criar recurso");
        }
        if (Set.of("confirmada", "recuperada", "recuperada_parcialmente", "negada").contains(glosa.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Glosa já concluída (" + glosa.getStatus() + ")");
        }

        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO recurso_glosa (id_glosa, status, justificativa, prazo_limite, id_usuario_responsavel) " +
                        "VALUES (:idGlosa, CAST('rascunho' AS status_recurso_glosa), :justificativa, :prazoLimite, :responsavel) " +
                        "RETURNING id_recurso"
        )
                .setParameter("idGlosa", idGlosa)
                .setParameter("justificativa", request.justificativa())
                .setParameter("prazoLimite", request.prazoLimite() != null ? request.prazoLimite() : glosa.getPrazoRecurso())
                .setParameter("responsavel", request.idUsuarioResponsavel())
                .getSingleResult()).intValue();

        glosaEscritaService.atualizarStatus(idGlosa, "recurso_preparacao");
        glosaEscritaService.registrarHistorico(idGlosa, "Recurso iniciado");
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public RecursoGlosa atualizar(Integer id, RecursoGlosaRequest request) {
        RecursoGlosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!STATUS_EDITAVEIS.contains(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurso em \"" + existente.getStatus() + "\" não pode mais ser editado");
        }

        existente.setJustificativa(request.justificativa());
        existente.setPrazoLimite(request.prazoLimite());
        existente.setIdUsuarioResponsavel(request.idUsuarioResponsavel());
        if (request.evidenciasConferidas() != null) existente.setEvidenciasConferidas(request.evidenciasConferidas());
        repository.save(existente);

        // rascunho -> em_preparacao assim que a justificativa é preenchida
        // pela primeira vez (spec: "Preparação do recurso" começa aqui).
        if ("rascunho".equals(existente.getStatus()) && request.justificativa() != null && !request.justificativa().isBlank()) {
            entityManager.createNativeQuery(
                    "UPDATE recurso_glosa SET status = CAST('em_preparacao' AS status_recurso_glosa) WHERE id_recurso = :id"
            ).setParameter("id", id).executeUpdate();
        }

        glosaEscritaService.registrarHistorico(existente.getIdGlosa(), "Recurso atualizado");
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public RecursoGlosaDocumento anexarDocumento(Integer idRecurso, RecursoGlosaDocumentoRequest request) {
        RecursoGlosa recurso = repository.findById(idRecurso)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado"));
        if (!STATUS_EDITAVEIS.contains(recurso.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurso em \"" + recurso.getStatus() + "\" não aceita novos documentos");
        }
        if (request.tipo() == null || !TIPOS_DOCUMENTO_VALIDOS.contains(request.tipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo de documento inválido: " + request.tipo());
        }

        RecursoGlosaDocumento documento = new RecursoGlosaDocumento();
        documento.setIdRecurso(idRecurso);
        documento.setTipo(request.tipo());
        documento.setIdDocumentoAnexo(request.idDocumentoAnexo());
        documento.setDescricao(request.descricao());
        RecursoGlosaDocumento salvo = documentoRepository.save(documento);

        glosaEscritaService.registrarHistorico(recurso.getIdGlosa(), "Documentação anexada ao recurso (" + request.tipo() + ")");
        return salvo;
    }

    @Transactional
    public RecursoGlosa enviar(Integer id, RecursoGlosaEnviarRequest request) {
        RecursoGlosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!STATUS_EDITAVEIS.contains(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurso em \"" + existente.getStatus() + "\" não pode ser enviado de novo");
        }

        List<String> pendencias = calcularPendencias(existente);
        if (!pendencias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível enviar: " + String.join("; ", pendencias));
        }
        if (request.canalEnvio() == null || !CANAIS_VALIDOS.contains(request.canalEnvio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canalEnvio inválido: " + request.canalEnvio());
        }

        entityManager.createNativeQuery(
                "UPDATE recurso_glosa SET status = CAST('enviado' AS status_recurso_glosa), " +
                        "  canal_envio = CAST(:canal AS canal_envio_recurso), protocolo = :protocolo, " +
                        "  enviado_em = now(), id_usuario_envio = :usuarioEnvio " +
                        "WHERE id_recurso = :id"
        )
                .setParameter("canal", request.canalEnvio())
                .setParameter("protocolo", request.protocolo())
                .setParameter("usuarioEnvio", existente.getIdUsuarioResponsavel())
                .setParameter("id", id)
                .executeUpdate();

        glosaEscritaService.atualizarStatus(existente.getIdGlosa(), "recurso_enviado");
        glosaEscritaService.registrarHistorico(existente.getIdGlosa(), "Recurso enviado");
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public RecursoGlosa registrarResultado(Integer id, RecursoGlosaResultadoRequest request) {
        RecursoGlosa existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Set.of("enviado", "aguardando_retorno", "em_analise_convenio").contains(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurso em \"" + existente.getStatus() + "\" não está aguardando resultado");
        }
        if (request.valorRecuperado() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valorRecuperado é obrigatório (0 se negado)");
        }

        Glosa glosa = glosaRepository.findById(existente.getIdGlosa()).orElseThrow();
        BigDecimal valorRecuperado = request.valorRecuperado();
        BigDecimal valorNaoRecuperado = request.valorNaoRecuperado() != null
                ? request.valorNaoRecuperado() : glosa.getValor().subtract(valorRecuperado).max(BigDecimal.ZERO);

        String statusRecurso;
        String statusGlosa;
        if (valorRecuperado.signum() <= 0) {
            statusRecurso = "negado";
            statusGlosa = "negada";
        } else if (valorRecuperado.compareTo(glosa.getValor()) >= 0) {
            statusRecurso = "recuperado";
            statusGlosa = "recuperada";
        } else {
            statusRecurso = "recuperado_parcialmente";
            statusGlosa = "recuperada_parcialmente";
        }

        entityManager.createNativeQuery(
                "UPDATE recurso_glosa SET status = CAST(:status AS status_recurso_glosa), " +
                        "  valor_recuperado = :valorRecuperado, valor_nao_recuperado = :valorNaoRecuperado, " +
                        "  motivo_negativa = :motivoNegativa, documento_resposta_url = :documentoRespostaUrl, " +
                        "  protocolo = COALESCE(:protocolo, protocolo), respondido_em = now() " +
                        "WHERE id_recurso = :id"
        )
                .setParameter("status", statusRecurso)
                .setParameter("valorRecuperado", valorRecuperado)
                .setParameter("valorNaoRecuperado", valorNaoRecuperado)
                .setParameter("motivoNegativa", request.motivoNegativa())
                .setParameter("documentoRespostaUrl", request.documentoRespostaUrl())
                .setParameter("protocolo", request.protocoloResposta())
                .setParameter("id", id)
                .executeUpdate();

        glosaEscritaService.atualizarStatus(existente.getIdGlosa(), statusGlosa);
        glosaEscritaService.registrarHistorico(
                existente.getIdGlosa(),
                "Retorno recebido — " + rotuloResultado(statusRecurso) + " (R$ " + valorRecuperado + " recuperados)"
        );
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    List<String> calcularPendencias(RecursoGlosa recurso) {
        Glosa glosa = glosaRepository.findById(recurso.getIdGlosa()).orElseThrow();
        List<String> pendencias = new ArrayList<>();
        if (glosa.getRecorribilidade() == null) pendencias.add("motivo ainda não analisado/classificado");
        if (recurso.getJustificativa() == null || recurso.getJustificativa().isBlank()) pendencias.add("justificativa não preenchida");
        boolean temDocumento = !documentoRepository.findByIdRecurso(recurso.getId()).isEmpty();
        if (!temDocumento) pendencias.add("nenhum documento/evidência anexado");
        if (!recurso.isEvidenciasConferidas()) pendencias.add("evidências ainda não conferidas");
        if (recurso.getIdUsuarioResponsavel() == null) pendencias.add("responsável não definido");
        Integer diasRestantes = PrazoClassificador.diasRestantes(recurso.getPrazoLimite());
        if (recurso.getPrazoLimite() == null || (diasRestantes != null && diasRestantes < 0)) pendencias.add("prazo ausente ou expirado");
        return pendencias;
    }

    private String rotuloResultado(String status) {
        return switch (status) {
            case "recuperado" -> "recuperação total";
            case "recuperado_parcialmente" -> "recuperação parcial";
            case "negado" -> "negado";
            default -> status;
        };
    }
}
