package br.com.clinica.service;

import br.com.clinica.model.Fatura;
import br.com.clinica.repository.FaturaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

// Escreve em fatura.status (enum status_pagamento) via SQL nativo com CAST
// explícito — repository.save() bindaria "status" como varchar e o Postgres
// rejeitaria (sem stringtype=unspecified na URL JDBC). Mesma cautela de
// PagamentoEscritaService/CaixaService.registrarPagamento.
@Service
public class FaturaEscritaService {

    private static final Set<String> STATUS_VALIDOS = Set.of("pendente", "pago", "atrasado", "cancelado", "estornado");

    private final FaturaRepository repository;
    private final EntityManager entityManager;

    public FaturaEscritaService(FaturaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Fatura criar(Fatura dados) {
        if (dados.getIdConsulta() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idConsulta é obrigatório");
        }
        if (repository.findByIdConsulta(dados.getIdConsulta()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa consulta já tem fatura");
        }
        String status = validarStatus(dados.getStatus() == null ? "pendente" : dados.getStatus());

        entityManager.createNativeQuery(
                "INSERT INTO fatura (id_consulta, valor, status, vencimento) " +
                        "VALUES (:idConsulta, :valor, CAST(:status AS status_pagamento), :vencimento)"
        )
                .setParameter("idConsulta", dados.getIdConsulta())
                .setParameter("valor", dados.getValor())
                .setParameter("status", status)
                .setParameter("vencimento", dados.getVencimento())
                .executeUpdate();

        return repository.findByIdConsulta(dados.getIdConsulta()).orElseThrow();
    }

    @Transactional
    public Fatura atualizar(Integer id, Fatura dados) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String status = validarStatus(dados.getStatus());

        entityManager.createNativeQuery(
                "UPDATE fatura SET valor = :valor, status = CAST(:status AS status_pagamento), vencimento = :vencimento " +
                        "WHERE id_fatura = :id"
        )
                .setParameter("valor", dados.getValor())
                .setParameter("status", status)
                .setParameter("vencimento", dados.getVencimento())
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
