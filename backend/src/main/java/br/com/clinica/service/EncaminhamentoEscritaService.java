package br.com.clinica.service;

import br.com.clinica.dto.EncaminhamentoDto;
import br.com.clinica.dto.EncaminhamentoRequest;
import br.com.clinica.model.Encaminhamento;
import br.com.clinica.model.PrioridadeEncaminhamento;
import br.com.clinica.repository.EncaminhamentoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

// prioridade é enum nativo do Postgres (prioridade_encaminhamento) — mesmo
// tratamento de AlergiaEscritaService/DocumentoClinicoEscritaService.
@Service
public class EncaminhamentoEscritaService {

    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EncaminhamentoRepository repository;
    private final EntityManager entityManager;

    public EncaminhamentoEscritaService(EncaminhamentoRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public EncaminhamentoDto criar(EncaminhamentoRequest request) {
        String prioridade = validar(request);
        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO encaminhamento (id_prontuario, id_especialidade_destino, motivo, prioridade) " +
                        "VALUES (:idProntuario, :idEspecialidade, :motivo, CAST(:prioridade AS prioridade_encaminhamento)) " +
                        "RETURNING id_encaminhamento"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("idEspecialidade", request.idEspecialidadeDestino())
                .setParameter("motivo", request.motivo())
                .setParameter("prioridade", prioridade)
                .getSingleResult()).intValue();
        return paraDto(repository.findById(id).orElseThrow());
    }

    @Transactional
    public EncaminhamentoDto atualizar(Integer id, EncaminhamentoRequest request) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String prioridade = validar(request);
        entityManager.createNativeQuery(
                "UPDATE encaminhamento SET id_prontuario = :idProntuario, id_especialidade_destino = :idEspecialidade, " +
                        "  motivo = :motivo, prioridade = CAST(:prioridade AS prioridade_encaminhamento) " +
                        "WHERE id_encaminhamento = :id"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("idEspecialidade", request.idEspecialidadeDestino())
                .setParameter("motivo", request.motivo())
                .setParameter("prioridade", prioridade)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private String validar(EncaminhamentoRequest request) {
        if (request.idProntuario() == null || request.idEspecialidadeDestino() == null
                || request.motivo() == null || request.motivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProntuario, idEspecialidadeDestino e motivo são obrigatórios");
        }
        String prioridade = request.prioridade() == null ? "ROTINA" : request.prioridade();
        try {
            PrioridadeEncaminhamento.valueOf(prioridade);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prioridade inválida: " + prioridade);
        }
        return prioridade;
    }

    private EncaminhamentoDto paraDto(Encaminhamento e) {
        return new EncaminhamentoDto(
                e.getId(), e.getProntuario().getId(), e.getEspecialidadeDestino().getId(), e.getMotivo(),
                e.getPrioridade().name(), e.getEmitidoEm() == null ? null : e.getEmitidoEm().format(DIA_MES_ANO_HORA)
        );
    }
}
