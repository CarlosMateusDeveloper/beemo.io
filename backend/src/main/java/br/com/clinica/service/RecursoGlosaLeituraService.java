package br.com.clinica.service;

import br.com.clinica.dto.RecursoGlosaDto;
import br.com.clinica.model.Glosa;
import br.com.clinica.model.RecursoGlosa;
import br.com.clinica.model.RecursoGlosaDocumento;
import br.com.clinica.repository.GlosaRepository;
import br.com.clinica.repository.RecursoGlosaDocumentoRepository;
import br.com.clinica.repository.RecursoGlosaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Monta RecursoGlosaDto (com documentos + checklist da seção 8) — usado
// tanto por GlosaDetalheService (recursoAtual) quanto por RecursoGlosaController
// (endpoints do próprio recurso).
@Service
public class RecursoGlosaLeituraService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RecursoGlosaRepository repository;
    private final RecursoGlosaDocumentoRepository documentoRepository;
    private final GlosaRepository glosaRepository;
    private final EntityManager entityManager;

    public RecursoGlosaLeituraService(
            RecursoGlosaRepository repository, RecursoGlosaDocumentoRepository documentoRepository,
            GlosaRepository glosaRepository, EntityManager entityManager
    ) {
        this.repository = repository;
        this.documentoRepository = documentoRepository;
        this.glosaRepository = glosaRepository;
        this.entityManager = entityManager;
    }

    public RecursoGlosaDto buscar(Integer id) {
        RecursoGlosa recurso = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado"));
        return paraDto(recurso);
    }

    public Optional<RecursoGlosaDto> atual(Integer idGlosa) {
        return repository.findFirstByIdGlosaOrderByCriadoEmDesc(idGlosa).map(this::paraDto);
    }

    RecursoGlosaDto paraDto(RecursoGlosa r) {
        List<RecursoGlosaDocumento> documentos = documentoRepository.findByIdRecurso(r.getId());
        List<RecursoGlosaDto.DocumentoDto> documentoDtos = documentos.stream()
                .map(d -> new RecursoGlosaDto.DocumentoDto(d.getId(), d.getTipo(), d.getIdDocumentoAnexo(), d.getDescricao()))
                .toList();

        Glosa glosa = glosaRepository.findById(r.getIdGlosa()).orElseThrow();
        RecursoGlosaDto.ChecklistDto checklist = montarChecklist(r, glosa, !documentos.isEmpty());

        Integer diasRestantes = PrazoClassificador.diasRestantes(r.getPrazoLimite());
        return new RecursoGlosaDto(
                r.getId(), r.getIdGlosa(), r.getStatus(), r.getJustificativa(),
                r.getPrazoLimite() == null ? "—" : r.getPrazoLimite().format(DIA_MES_ANO),
                diasRestantes, PrazoClassificador.cor(diasRestantes),
                nomeUsuario(r.getIdUsuarioResponsavel()), r.getCanalEnvio(), r.getProtocolo(),
                r.getEnviadoEm() == null ? null : r.getEnviadoEm().format(DIA_MES_ANO_HORA),
                r.getRespondidoEm() == null ? null : r.getRespondidoEm().format(DIA_MES_ANO_HORA),
                r.getValorRecuperado(), r.getValorNaoRecuperado(), r.getMotivoNegativa(), r.getDocumentoRespostaUrl(),
                documentoDtos, checklist
        );
    }

    private RecursoGlosaDto.ChecklistDto montarChecklist(RecursoGlosa r, Glosa glosa, boolean temDocumento) {
        boolean motivoAnalisado = glosa.getRecorribilidade() != null;
        boolean justificativaPreenchida = r.getJustificativa() != null && !r.getJustificativa().isBlank();
        boolean evidenciasConferidas = r.isEvidenciasConferidas();
        boolean responsavelDefinido = r.getIdUsuarioResponsavel() != null;
        Integer diasRestantes = PrazoClassificador.diasRestantes(r.getPrazoLimite());
        boolean prazoValido = r.getPrazoLimite() != null && diasRestantes != null && diasRestantes >= 0;
        boolean podeEnviar = motivoAnalisado && justificativaPreenchida && temDocumento
                && evidenciasConferidas && responsavelDefinido && prazoValido;

        List<String> pendencias = new ArrayList<>();
        if (!motivoAnalisado) pendencias.add("Motivo ainda não analisado/classificado");
        if (!justificativaPreenchida) pendencias.add("Justificativa não preenchida");
        if (!temDocumento) pendencias.add("Nenhum documento/evidência anexado");
        if (!evidenciasConferidas) pendencias.add("Evidências ainda não conferidas");
        if (!responsavelDefinido) pendencias.add("Responsável não definido");
        if (!prazoValido) pendencias.add("Prazo ausente ou expirado");

        return new RecursoGlosaDto.ChecklistDto(
                motivoAnalisado, justificativaPreenchida, temDocumento, evidenciasConferidas,
                responsavelDefinido, prazoValido, podeEnviar, pendencias
        );
    }

    @SuppressWarnings("unchecked")
    private String nomeUsuario(Integer idUsuario) {
        if (idUsuario == null) return null;
        List<Object> resultado = entityManager.createNativeQuery("SELECT nome FROM usuario WHERE id = :id")
                .setParameter("id", idUsuario)
                .getResultList();
        return resultado.isEmpty() ? null : (String) resultado.get(0);
    }
}
