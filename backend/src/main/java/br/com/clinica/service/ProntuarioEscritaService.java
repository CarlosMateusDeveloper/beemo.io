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
        Prontuario salvo = repository.save(prontuario);
        atualizarTipoDiagnostico(salvo.getId(), request.tipoDiagnostico());
        entityManager.clear();
        return paraDto(repository.findById(salvo.getId()).orElseThrow());
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
        repository.save(existente);
        atualizarTipoDiagnostico(id, request.tipoDiagnostico());
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private void aplicar(Prontuario prontuario, ProntuarioSalvarRequest request) {
        prontuario.setQueixaPrincipal(request.queixaPrincipal());
        prontuario.setHistoriaDoencaAtual(request.historiaDoencaAtual());
        prontuario.setDescricao(request.descricao());
        prontuario.setExameFisico(request.exameFisico());
        prontuario.setHipoteseDiagnostica(request.hipoteseDiagnostica());
        prontuario.setDiagnostico(request.diagnostico());
        // tipoDiagnostico não é setado aqui — ver atualizarTipoDiagnostico.
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

    // tipo_diagnostico é enum nativo do Postgres — coluna insertable/updatable
    // = false na entidade (ver Prontuario.java), escrita só passa por aqui.
    // Sem valor informado, a coluna fica com o DEFAULT do banco ('DEFINITIVO').
    private void atualizarTipoDiagnostico(Integer id, String tipoDiagnostico) {
        if (tipoDiagnostico == null) return;
        try {
            TipoDiagnostico.valueOf(tipoDiagnostico);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipoDiagnostico inválido: " + tipoDiagnostico);
        }
        entityManager.createNativeQuery(
                "UPDATE prontuario SET tipo_diagnostico = CAST(:tipo AS tipo_diagnostico) WHERE id_prontuario = :id"
        ).setParameter("tipo", tipoDiagnostico).setParameter("id", id).executeUpdate();
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private ProntuarioSalvoDto paraDto(Prontuario prontuario) {
        return new ProntuarioSalvoDto(prontuario.getId(), prontuario.getConsulta().getId(), prontuario.getAssinadoEm() != null);
    }
}
