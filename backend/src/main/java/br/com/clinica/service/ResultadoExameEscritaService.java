package br.com.clinica.service;

import br.com.clinica.model.Exame;
import br.com.clinica.model.ResultadoExame;
import br.com.clinica.repository.ExameRepository;
import br.com.clinica.repository.ResultadoExameRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

// Issue #13: "registro de resultado vinculado ao exame". Só aceita registrar
// resultado quando o exame está "realizado" (não dá pra ter resultado de um
// exame que ainda não aconteceu) — ao registrar, o exame vira
// "resultado_disponivel" automaticamente (mesma ideia de PagamentoEscritaService
// atualizando o status da fatura).
@Service
public class ResultadoExameEscritaService {

    private final ResultadoExameRepository resultadoRepository;
    private final ExameRepository exameRepository;
    private final EntityManager entityManager;

    public ResultadoExameEscritaService(
            ResultadoExameRepository resultadoRepository, ExameRepository exameRepository, EntityManager entityManager
    ) {
        this.resultadoRepository = resultadoRepository;
        this.exameRepository = exameRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ResultadoExame registrar(Integer idExame, ResultadoExame dados) {
        Exame exame = exameRepository.findById(idExame)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exame não encontrado"));
        if (!"realizado".equals(exame.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Só é possível registrar resultado de um exame \"realizado\" (status atual: " + exame.getStatus() + ")"
            );
        }
        if (resultadoRepository.findByIdExame(idExame).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse exame já tem resultado registrado");
        }

        // Sem coluna enum em resultado_exame — repository.save() direto é seguro aqui.
        dados.setId(null);
        dados.setIdExame(idExame);
        if (dados.getRecebidoEm() == null) dados.setRecebidoEm(OffsetDateTime.now());
        ResultadoExame salvo = resultadoRepository.save(dados);

        entityManager.createNativeQuery(
                "UPDATE exame SET status = CAST('resultado_disponivel' AS status_exame) WHERE id_exame = :id"
        ).setParameter("id", idExame).executeUpdate();

        return salvo;
    }

    public ResultadoExame buscar(Integer idExame) {
        return resultadoRepository.findByIdExame(idExame)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exame ainda não tem resultado"));
    }
}
