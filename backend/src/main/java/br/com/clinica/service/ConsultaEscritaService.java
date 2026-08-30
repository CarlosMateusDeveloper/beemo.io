package br.com.clinica.service;

import br.com.clinica.dto.ConsultaDto;
import br.com.clinica.dto.ConsultaRequest;
import br.com.clinica.model.Consulta;
import br.com.clinica.model.Paciente;
import br.com.clinica.model.StatusConsulta;
import br.com.clinica.repository.ConsultaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// status_consulta é enum nativo do Postgres (status_consulta_enum), com
// DEFAULT ('Agendada') — mesmo tratamento de UsuarioEscritaService
// (insertable=false + UPDATE de acompanhamento). "Em Espera"/"Em Atendimento"
// têm espaço no banco mas não no enum Java (StatusConsulta.EmEspera/
// EmAtendimento) — StatusConsultaConverter faz essa ponte na leitura;
// aqui, na escrita, mapeamos manualmente pro literal certo antes do CAST.
//
// Nota: nenhum caller no frontend usa este endpoint hoje — consulta é
// escrita pelo agenda-service (Go), não por aqui. Mantido consistente com
// o resto da API mesmo assim.
@Service
public class ConsultaEscritaService {

    private final ConsultaRepository repository;
    private final EntityManager entityManager;

    public ConsultaEscritaService(ConsultaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ConsultaDto criar(ConsultaRequest request) {
        if (request.idPaciente() == null || request.idAgenda() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPaciente e idAgenda são obrigatórios");
        }
        Consulta consulta = new Consulta();
        consulta.setPaciente(entityManager.getReference(Paciente.class, request.idPaciente()));
        consulta.setIdAgenda(request.idAgenda());
        Consulta salva = repository.save(consulta);
        atualizarStatus(salva.getId(), request.statusConsulta());
        entityManager.clear();
        return paraDto(repository.findById(salva.getId()).orElseThrow());
    }

    @Transactional
    public ConsultaDto atualizar(Integer id, ConsultaRequest request) {
        Consulta existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.idPaciente() != null) existente.setPaciente(entityManager.getReference(Paciente.class, request.idPaciente()));
        if (request.idAgenda() != null) existente.setIdAgenda(request.idAgenda());
        repository.save(existente);
        atualizarStatus(id, request.statusConsulta());
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private void atualizarStatus(Integer id, String status) {
        if (status == null) return;
        StatusConsulta parsed;
        try {
            parsed = StatusConsulta.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statusConsulta inválido: " + status);
        }
        String literalBanco = switch (parsed) {
            case EmEspera -> "Em Espera";
            case EmAtendimento -> "Em Atendimento";
            default -> parsed.name();
        };
        entityManager.createNativeQuery(
                "UPDATE consulta SET status_consulta = CAST(:status AS status_consulta_enum) WHERE id_consulta = :id"
        ).setParameter("status", literalBanco).setParameter("id", id).executeUpdate();
    }

    private ConsultaDto paraDto(Consulta c) {
        return new ConsultaDto(
                c.getId(), c.getPaciente().getId(), c.getIdAgenda(),
                c.getStatusConsulta() == null ? null : c.getStatusConsulta().name()
        );
    }
}
