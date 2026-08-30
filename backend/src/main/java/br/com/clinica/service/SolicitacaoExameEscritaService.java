package br.com.clinica.service;

import br.com.clinica.dto.SolicitacaoExameDto;
import br.com.clinica.dto.SolicitacaoExameRequest;
import br.com.clinica.model.SolicitacaoExame;
import br.com.clinica.model.StatusSolicitacaoExame;
import br.com.clinica.repository.SolicitacaoExameRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

// status é enum nativo do Postgres (status_solicitacao_exame) — mesmo
// tratamento de AlergiaEscritaService/DocumentoClinicoEscritaService.
@Service
public class SolicitacaoExameEscritaService {

    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SolicitacaoExameRepository repository;
    private final EntityManager entityManager;

    public SolicitacaoExameEscritaService(SolicitacaoExameRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public SolicitacaoExameDto criar(SolicitacaoExameRequest request) {
        String status = validar(request);
        boolean urgente = request.urgente() != null && request.urgente();
        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO solicitacao_exame (id_prontuario, exame, urgente, justificativa, status) " +
                        "VALUES (:idProntuario, :exame, :urgente, :justificativa, CAST(:status AS status_solicitacao_exame)) " +
                        "RETURNING id_solicitacao_exame"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("exame", request.exame())
                .setParameter("urgente", urgente)
                .setParameter("justificativa", request.justificativa())
                .setParameter("status", status)
                .getSingleResult()).intValue();
        return paraDto(repository.findById(id).orElseThrow());
    }

    @Transactional
    public SolicitacaoExameDto atualizar(Integer id, SolicitacaoExameRequest request) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String status = validar(request);
        boolean urgente = request.urgente() != null && request.urgente();
        entityManager.createNativeQuery(
                "UPDATE solicitacao_exame SET id_prontuario = :idProntuario, exame = :exame, urgente = :urgente, " +
                        "  justificativa = :justificativa, status = CAST(:status AS status_solicitacao_exame) " +
                        "WHERE id_solicitacao_exame = :id"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("exame", request.exame())
                .setParameter("urgente", urgente)
                .setParameter("justificativa", request.justificativa())
                .setParameter("status", status)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private String validar(SolicitacaoExameRequest request) {
        if (request.idProntuario() == null || request.exame() == null || request.exame().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProntuario e exame são obrigatórios");
        }
        String status = request.status() == null ? "SOLICITADO" : request.status();
        try {
            StatusSolicitacaoExame.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status inválido: " + status);
        }
        return status;
    }

    private SolicitacaoExameDto paraDto(SolicitacaoExame s) {
        return new SolicitacaoExameDto(
                s.getId(), s.getProntuario().getId(), s.getExame(), Boolean.TRUE.equals(s.getUrgente()),
                s.getJustificativa(), s.getStatus().name(),
                s.getSolicitadoEm() == null ? null : s.getSolicitadoEm().format(DIA_MES_ANO_HORA)
        );
    }
}
