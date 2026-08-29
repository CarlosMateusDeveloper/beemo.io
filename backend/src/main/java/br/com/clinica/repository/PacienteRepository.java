package br.com.clinica.repository;

import br.com.clinica.dto.PacienteBuscaRow;
import br.com.clinica.model.Paciente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    Optional<Paciente> findByCpf(String cpf);

    // busca é opcional (issue #11: "listar paginado, com busca por
    // nome/CPF/telefone") — quando informada, casa contra nome (parcial,
    // case-insensitive) ou os dígitos de cpf/ddd+numero.
    @Query("SELECT new br.com.clinica.dto.PacienteBuscaRow(p.id, p.nome, p.cpf, p.ddd, p.numero, p.dataNascimento, c.nome) " +
            "FROM Paciente p LEFT JOIN p.convenio c " +
            "WHERE (:busca IS NULL OR :busca = '' " +
            "  OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :busca, '%')) " +
            "  OR (:buscaDigitos <> '' AND p.cpf LIKE CONCAT('%', :buscaDigitos, '%')) " +
            "  OR (:buscaDigitos <> '' AND CONCAT(p.ddd, p.numero) LIKE CONCAT('%', :buscaDigitos, '%')))")
    Page<PacienteBuscaRow> buscar(@Param("busca") String busca, @Param("buscaDigitos") String buscaDigitos, Pageable pageable);

    // convenio e LAZY (Paciente.java); sem o JOIN FETCH aqui, a serializacao
    // do GET/PUT /api/pacientes/{id} quebra com LazyInitializationException
    // (open-in-view: false fecha a sessao antes do Jackson acessar
    // paciente.convenio.*). Mesmo padrao ja usado em MedicoRepository.
    @Query("SELECT p FROM Paciente p LEFT JOIN FETCH p.convenio WHERE p.id = :id")
    @Override
    Optional<Paciente> findById(Integer id);

    // Mesmo motivo do findById acima — GET /api/pacientes (listar()) usa
    // findAll() puro e quebrava do mesmo jeito.
    @Query("SELECT p FROM Paciente p LEFT JOIN FETCH p.convenio")
    @Override
    List<Paciente> findAll();
}
