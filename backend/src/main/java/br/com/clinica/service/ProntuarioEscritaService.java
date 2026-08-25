package br.com.clinica.service;

import br.com.clinica.dto.ProntuarioSalvarRequest;
import br.com.clinica.dto.ProntuarioSalvoDto;
import br.com.clinica.model.Consulta;
import br.com.clinica.model.Medico;
import br.com.clinica.model.Prontuario;
import br.com.clinica.model.TipoDiagnostico;
import br.com.clinica.repository.ProntuarioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

// Escrita de /prontuario (Novo atendimento / Continuar atendimento). Usa
// EntityManager.getReference em vez de aceitar a entidade Prontuario direto
// no @RequestBody — evita depender de como o Jackson desserializa a
// associação aninhada consulta/medicoResponsavel, e mantém a resposta plana
// (ver ProntuarioSalvoDto) porque consulta/medicoResponsavel são LAZY sem
// JOIN FETCH.
@Service
public class ProntuarioEscritaService {

    private final ProntuarioRepository repository;
    private final EntityManager entityManager;

    public ProntuarioEscritaService(ProntuarioRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ProntuarioSalvoDto criar(ProntuarioSalvarRequest request) {
        if (request.consultaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultaId é obrigatório");
        }
        Prontuario prontuario = new Prontuario();
        prontuario.setConsulta(entityManager.getReference(Consulta.class, request.consultaId()));
        aplicar(prontuario, request);
        return paraDto(repository.save(prontuario));
    }

    @Transactional
    public ProntuarioSalvoDto atualizar(Integer id, ProntuarioSalvarRequest request) {
        Prontuario existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Registro assinado é imutável (Resolução CFM 2.299/2021) — sem
        // fluxo de "reabrir" um atendimento já finalizado.
        if (existente.getAssinadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento já finalizado não pode ser editado");
        }
        aplicar(existente, request);
        return paraDto(repository.save(existente));
    }

    private void aplicar(Prontuario prontuario, ProntuarioSalvarRequest request) {
        prontuario.setQueixaPrincipal(request.queixaPrincipal());
        prontuario.setHistoriaDoencaAtual(request.historiaDoencaAtual());
        prontuario.setDescricao(request.descricao());
        prontuario.setExameFisico(request.exameFisico());
        prontuario.setHipoteseDiagnostica(request.hipoteseDiagnostica());
        prontuario.setDiagnostico(request.diagnostico());
        if (request.tipoDiagnostico() != null) {
            prontuario.setTipoDiagnostico(TipoDiagnostico.valueOf(request.tipoDiagnostico()));
        }
        prontuario.setPrescricao(request.prescricao());
        prontuario.setPlanoTerapeutico(request.planoTerapeutico());
        prontuario.setConduta(request.conduta());

        if (request.finalizar()) {
            if (vazio(request.descricao()) || vazio(request.diagnostico()) || vazio(request.prescricao())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Para finalizar, descrição, diagnóstico e prescrição são obrigatórios");
            }
            if (request.medicoResponsavelId() != null) {
                prontuario.setMedicoResponsavel(entityManager.getReference(Medico.class, request.medicoResponsavelId()));
            }
            prontuario.setAssinadoEm(OffsetDateTime.now());
        }
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private ProntuarioSalvoDto paraDto(Prontuario prontuario) {
        return new ProntuarioSalvoDto(prontuario.getId(), prontuario.getConsulta().getId(), prontuario.getAssinadoEm() != null);
    }
}
