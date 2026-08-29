package br.com.clinica.service;

import br.com.clinica.model.AutorizacaoConvenio;
import br.com.clinica.repository.AutorizacaoConvenioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

// Escreve em autorizacao_convenio.status (enum status_autorizacao) via SQL
// nativo com CAST explícito — mesma cautela de FaturaEscritaService (sem
// stringtype=unspecified na URL JDBC, repository.save() bindaria como
// varchar e o Postgres rejeitaria).
@Service
public class AutorizacaoConvenioEscritaService {

    private static final Set<String> STATUS_VALIDOS = Set.of("pendente", "autorizado", "negado", "expirado");

    private final AutorizacaoConvenioRepository repository;
    private final EntityManager entityManager;

    public AutorizacaoConvenioEscritaService(AutorizacaoConvenioRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AutorizacaoConvenio criar(AutorizacaoConvenio dados) {
        if (dados.getIdConsulta() == null || dados.getIdConvenio() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idConsulta e idConvenio são obrigatórios");
        }
        if (repository.findByIdConsulta(dados.getIdConsulta()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa consulta já tem autorização");
        }
        String status = validarStatus(dados.getStatus() == null ? "pendente" : dados.getStatus());

        entityManager.createNativeQuery(
                "INSERT INTO autorizacao_convenio (id_consulta, id_convenio, numero_guia, status) " +
                        "VALUES (:idConsulta, :idConvenio, :numeroGuia, CAST(:status AS status_autorizacao))"
        )
                .setParameter("idConsulta", dados.getIdConsulta())
                .setParameter("idConvenio", dados.getIdConvenio())
                .setParameter("numeroGuia", dados.getNumeroGuia())
                .setParameter("status", status)
                .executeUpdate();

        return repository.findByIdConsulta(dados.getIdConsulta()).orElseThrow();
    }

    @Transactional
    public AutorizacaoConvenio atualizar(Integer id, AutorizacaoConvenio dados) {
        AutorizacaoConvenio existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String status = validarStatus(dados.getStatus());

        // respondido_em só é setado na primeira vez que sai de "pendente" —
        // reenviar o mesmo status (ex.: corrigir numeroGuia) não o reescreve.
        boolean marcarRespondido = existente.getRespondidoEm() == null && !"pendente".equals(status);

        entityManager.createNativeQuery(
                "UPDATE autorizacao_convenio SET numero_guia = :numeroGuia, status = CAST(:status AS status_autorizacao)" +
                        (marcarRespondido ? ", respondido_em = now()" : "") +
                        " WHERE id_autorizacao = :id"
        )
                .setParameter("numeroGuia", dados.getNumeroGuia())
                .setParameter("status", status)
                .setParameter("id", id)
                .executeUpdate();

        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    private String validarStatus(String status) {
        if (status == null || !STATUS_VALIDOS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status inválido: " + status);
        }
        return status;
    }
}
