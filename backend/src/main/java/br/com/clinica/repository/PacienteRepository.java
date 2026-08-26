package br.com.clinica.repository;

import br.com.clinica.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    Optional<Paciente> findByCpf(String cpf);

    // convenio e LAZY (Paciente.java); sem o JOIN FETCH aqui, a serializacao
    // do GET/PUT /api/pacientes/{id} quebra com LazyInitializationException
    // (open-in-view: false fecha a sessao antes do Jackson acessar
    // paciente.convenio.*). Mesmo padrao ja usado em MedicoRepository.
    @Query("SELECT p FROM Paciente p LEFT JOIN FETCH p.convenio WHERE p.id = :id")
    @Override
    Optional<Paciente> findById(Integer id);
}
