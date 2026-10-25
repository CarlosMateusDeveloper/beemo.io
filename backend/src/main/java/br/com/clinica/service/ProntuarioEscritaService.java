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

import java.time.LocalDate;
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
    private final AuditoriaEngineService auditoriaEngineService;
    private final ClinicalTextCipher cipher;

    public ProntuarioEscritaService(
            ProntuarioRepository repository, EntityManager entityManager, AuditoriaEngineService auditoriaEngineService,
            ClinicalTextCipher cipher
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.auditoriaEngineService = auditoriaEngineService;
        this.cipher = cipher;
    }

    @Transactional
    public ProntuarioSalvoDto criar(ProntuarioSalvarRequest request) {
        if (request.consultaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consultaId é obrigatório");
        }
        Prontuario prontuario = new Prontuario();
        prontuario.setConsulta(entityManager.getReference(Consulta.class, request.consultaId()));
        // IDENTITY allocates the ID on insert. Required columns receive only empty
        // placeholders inside this transaction; clinical text is written after the ID exists.
        prontuario.setDescricao("");
        prontuario.setDiagnostico("");
        prontuario.setPrescricao("");
        Prontuario salvo = repository.save(prontuario);
        aplicar(salvo, request);
        repository.save(salvo);
        repository.flush();
        atualizarTipoDiagnostico(salvo.getId(), request.tipoDiagnostico());
        // Atendimento finalizado já na criação (fluxo de um passo só) —
        // dispara o motor de auditoria (docs/specs/convenios.md).
        if (request.finalizar()) auditoriaEngineService.avaliarAtendimento(request.consultaId());
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
        Integer idConsulta = existente.getConsulta().getId();
        aplicar(existente, request);
        repository.save(existente);
        repository.flush();
        atualizarTipoDiagnostico(id, request.tipoDiagnostico());
        // "Atendimento finalizado" é o evento real mais próximo de
        // consulta.status = 'Realizada' que existe no monólito Java — a
        // agenda (e esse status) é escrita pelo agenda-service (Go), fora
        // de alcance daqui. Dispara o motor de auditoria (só roda uma vez
        // por consulta: o guard de imutabilidade acima impede refinalizar).
        if (request.finalizar()) auditoriaEngineService.avaliarAtendimento(idConsulta);
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private void aplicar(Prontuario prontuario, ProntuarioSalvarRequest request) {
        int tenant = TenantContext.id();
        long id = prontuario.getId();
        prontuario.setQueixaPrincipal(cipher.encrypt(request.queixaPrincipal(), tenant, "prontuario", "queixa_principal", id));
        prontuario.setHistoriaDoencaAtual(cipher.encrypt(request.historiaDoencaAtual(), tenant, "prontuario", "historia_doenca_atual", id));
        prontuario.setDescricao(cipher.encrypt(requiredText(request.descricao()), tenant, "prontuario", "descricao", id));
        prontuario.setExameFisico(cipher.encrypt(request.exameFisico(), tenant, "prontuario", "exame_fisico", id));
        prontuario.setHipoteseDiagnostica(cipher.encrypt(request.hipoteseDiagnostica(), tenant, "prontuario", "hipotese_diagnostica", id));
        prontuario.setDiagnostico(cipher.encrypt(requiredText(request.diagnostico()), tenant, "prontuario", "diagnostico", id));
        // tipoDiagnostico não é setado aqui — ver atualizarTipoDiagnostico.
        prontuario.setPrescricao(cipher.encrypt(requiredText(request.prescricao()), tenant, "prontuario", "prescricao", id));
        prontuario.setPlanoTerapeutico(cipher.encrypt(request.planoTerapeutico(), tenant, "prontuario", "plano_terapeutico", id));
        prontuario.setConduta(cipher.encrypt(request.conduta(), tenant, "prontuario", "conduta", id));

        if (request.finalizar()) {
            if (vazio(request.descricao()) || vazio(request.diagnostico()) || vazio(request.prescricao())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Para finalizar, descrição, diagnóstico e prescrição são obrigatórios");
            }
            if (request.medicoResponsavelId() != null) {
                prontuario.setMedicoResponsavel(entityManager.getReference(Medico.class, request.medicoResponsavelId()));
            }
            // "Retorno em X dias" (docs/specs/retorno.md) só vira pendência real
            // quando o atendimento é finalizado — rascunho ainda pode mudar.
            prontuario.setRetornoSugeridoEm(request.retornoSugeridoDias() != null
                    ? LocalDate.now().plusDays(request.retornoSugeridoDias())
                    : null);
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

    private String requiredText(String value) {
        return value == null ? "" : value;
    }

    private ProntuarioSalvoDto paraDto(Prontuario prontuario) {
        return new ProntuarioSalvoDto(prontuario.getId(), prontuario.getConsulta().getId(), prontuario.getAssinadoEm() != null);
    }
}
