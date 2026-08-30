package br.com.clinica.service;

import br.com.clinica.dto.DocumentoClinicoDto;
import br.com.clinica.dto.DocumentoClinicoRequest;
import br.com.clinica.model.DocumentoClinico;
import br.com.clinica.model.TipoDocumentoClinico;
import br.com.clinica.repository.DocumentoClinicoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

// tipo é enum nativo do Postgres (tipo_documento_clinico), sem DEFAULT no
// banco — mesmo motivo/tratamento de AlergiaEscritaService.
@Service
public class DocumentoClinicoEscritaService {

    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DocumentoClinicoRepository repository;
    private final EntityManager entityManager;

    public DocumentoClinicoEscritaService(DocumentoClinicoRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public DocumentoClinicoDto criar(DocumentoClinicoRequest request) {
        validar(request);
        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO documento_clinico (id_prontuario, tipo, dias_afastamento, codigo_cid_relacionado, texto) " +
                        "VALUES (:idProntuario, CAST(:tipo AS tipo_documento_clinico), :diasAfastamento, :codigoCid, :texto) " +
                        "RETURNING id_documento_clinico"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("tipo", request.tipo())
                .setParameter("diasAfastamento", request.diasAfastamento())
                .setParameter("codigoCid", request.codigoCidRelacionado())
                .setParameter("texto", request.texto())
                .getSingleResult()).intValue();
        return paraDto(repository.findById(id).orElseThrow());
    }

    @Transactional
    public DocumentoClinicoDto atualizar(Integer id, DocumentoClinicoRequest request) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        validar(request);
        entityManager.createNativeQuery(
                "UPDATE documento_clinico SET id_prontuario = :idProntuario, tipo = CAST(:tipo AS tipo_documento_clinico), " +
                        "  dias_afastamento = :diasAfastamento, codigo_cid_relacionado = :codigoCid, texto = :texto " +
                        "WHERE id_documento_clinico = :id"
        )
                .setParameter("idProntuario", request.idProntuario())
                .setParameter("tipo", request.tipo())
                .setParameter("diasAfastamento", request.diasAfastamento())
                .setParameter("codigoCid", request.codigoCidRelacionado())
                .setParameter("texto", request.texto())
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private void validar(DocumentoClinicoRequest request) {
        if (request.idProntuario() == null || request.texto() == null || request.texto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProntuario e texto são obrigatórios");
        }
        try {
            TipoDocumentoClinico.valueOf(request.tipo());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo inválido: " + request.tipo());
        }
    }

    private DocumentoClinicoDto paraDto(DocumentoClinico d) {
        return new DocumentoClinicoDto(
                d.getId(), d.getProntuario().getId(), d.getTipo().name(), d.getDiasAfastamento(),
                d.getCodigoCidRelacionado(), d.getTexto(),
                d.getEmitidoEm() == null ? null : d.getEmitidoEm().format(DIA_MES_ANO_HORA)
        );
    }
}
