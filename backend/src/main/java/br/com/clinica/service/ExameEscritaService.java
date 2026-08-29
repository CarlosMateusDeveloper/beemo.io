package br.com.clinica.service;

import br.com.clinica.dto.ExameStatusRequest;
import br.com.clinica.model.Exame;
import br.com.clinica.repository.ExameRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

// Issue #13: transições de status de exame, documentadas e validadas aqui —
// nada além disso é aceito.
//
//   solicitado -> agendado | cancelado
//   agendado -> realizado | cancelado
//   realizado -> resultado_disponivel | cancelado
//   resultado_disponivel -> cancelado   (resultado emitido por engano, por ex.)
//   cancelado -> (nada — estado terminal)
//
// exame.status é enum nativo do Postgres (status_exame); escrita passa por
// SQL nativo com CAST explícito pelo mesmo motivo de Fatura/AutorizacaoConvenio
// (sem stringtype=unspecified na URL JDBC, repository.save() bindaria como
// varchar e o Postgres rejeitaria).
@Service
public class ExameEscritaService {

    private static final Map<String, Set<String>> TRANSICOES_VALIDAS = Map.of(
            "solicitado", Set.of("agendado", "cancelado"),
            "agendado", Set.of("realizado", "cancelado"),
            "realizado", Set.of("resultado_disponivel", "cancelado"),
            "resultado_disponivel", Set.of("cancelado"),
            "cancelado", Set.of()
    );

    private final ExameRepository repository;
    private final EntityManager entityManager;

    public ExameEscritaService(ExameRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Exame criar(Exame dados) {
        Integer id = ((Number) entityManager.createNativeQuery(
                "INSERT INTO exame (id_paciente, id_consulta, nome_exame, laboratorio, status) " +
                        "VALUES (:idPaciente, :idConsulta, :nomeExame, :laboratorio, CAST('solicitado' AS status_exame)) " +
                        "RETURNING id_exame"
        )
                .setParameter("idPaciente", dados.getIdPaciente())
                .setParameter("idConsulta", dados.getIdConsulta())
                .setParameter("nomeExame", dados.getNomeExame())
                .setParameter("laboratorio", dados.getLaboratorio())
                .getSingleResult()).intValue();

        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Exame atualizarDados(Integer id, Exame dados) {
        Exame existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("cancelado".equals(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exame cancelado não pode ser editado");
        }
        entityManager.createNativeQuery(
                "UPDATE exame SET nome_exame = :nomeExame, laboratorio = :laboratorio WHERE id_exame = :id"
        )
                .setParameter("nomeExame", dados.getNomeExame())
                .setParameter("laboratorio", dados.getLaboratorio())
                .setParameter("id", id)
                .executeUpdate();

        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Exame atualizarStatus(Integer id, ExameStatusRequest request) {
        Exame existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String novoStatus = request.status();
        Set<String> permitidos = TRANSICOES_VALIDAS.get(existente.getStatus());
        if (permitidos == null || !permitidos.contains(novoStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transição inválida: " + existente.getStatus() + " -> " + novoStatus
            );
        }

        entityManager.createNativeQuery(
                "UPDATE exame SET status = CAST(:status AS status_exame) WHERE id_exame = :id"
        ).setParameter("status", novoStatus).setParameter("id", id).executeUpdate();

        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }
}
