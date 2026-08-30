package br.com.clinica.service;

import br.com.clinica.dto.AlergiaDto;
import br.com.clinica.dto.AlergiaRequest;
import br.com.clinica.model.Alergia;
import br.com.clinica.model.GravidadeAlergia;
import br.com.clinica.model.TipoAlergia;
import br.com.clinica.repository.AlergiaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

// tipo/gravidade são enums nativos do Postgres (tipo_alergia/gravidade_alergia),
// sem DEFAULT no banco — ao contrário de Prontuario.tipoDiagnostico
// (issue relacionada, já corrigida), aqui não dá pra só marcar
// insertable=false e deixar o banco aplicar um default, porque não existe
// um. Por isso o INSERT inteiro passa por SQL nativo com CAST, não só um
// UPDATE de acompanhamento.
@Service
public class AlergiaEscritaService {

    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AlergiaRepository repository;
    private final EntityManager entityManager;

    public AlergiaEscritaService(AlergiaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AlergiaDto criar(AlergiaRequest request) {
        validar(request);
        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO alergia (id_paciente, tipo, substancia, gravidade, observacao) " +
                        "VALUES (:idPaciente, CAST(:tipo AS tipo_alergia), :substancia, CAST(:gravidade AS gravidade_alergia), :observacao) " +
                        "RETURNING id_alergia"
        )
                .setParameter("idPaciente", request.idPaciente())
                .setParameter("tipo", request.tipo())
                .setParameter("substancia", request.substancia())
                .setParameter("gravidade", request.gravidade())
                .setParameter("observacao", request.observacao())
                .getSingleResult()).intValue();
        return paraDto(repository.findById(id).orElseThrow());
    }

    @Transactional
    public AlergiaDto atualizar(Integer id, AlergiaRequest request) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        validar(request);
        entityManager.createNativeQuery(
                "UPDATE alergia SET id_paciente = :idPaciente, tipo = CAST(:tipo AS tipo_alergia), " +
                        "  substancia = :substancia, gravidade = CAST(:gravidade AS gravidade_alergia), observacao = :observacao " +
                        "WHERE id_alergia = :id"
        )
                .setParameter("idPaciente", request.idPaciente())
                .setParameter("tipo", request.tipo())
                .setParameter("substancia", request.substancia())
                .setParameter("gravidade", request.gravidade())
                .setParameter("observacao", request.observacao())
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return paraDto(repository.findById(id).orElseThrow());
    }

    private void validar(AlergiaRequest request) {
        if (request.idPaciente() == null || request.substancia() == null || request.substancia().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idPaciente e substancia são obrigatórios");
        }
        try {
            TipoAlergia.valueOf(request.tipo());
            GravidadeAlergia.valueOf(request.gravidade());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo ou gravidade inválidos");
        }
    }

    private AlergiaDto paraDto(Alergia a) {
        return new AlergiaDto(
                a.getId(), a.getPaciente().getId(), a.getTipo().name(), a.getSubstancia(), a.getGravidade().name(),
                a.getObservacao(), a.getRegistradoEm() == null ? null : a.getRegistradoEm().format(DIA_MES_ANO_HORA)
        );
    }
}
