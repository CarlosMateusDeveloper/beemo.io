package br.com.clinica.repository;

import br.com.clinica.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MedicoRepository extends JpaRepository<Medico, Integer> {

    // especialidade é LAZY (Medico.java); sem o JOIN FETCH aqui, a serialização
    // do controller quebra com LazyInitializationException (open-in-view: false
    // fecha a sessão antes do Jackson acessar medico.especialidade.nome).
    @Query("SELECT m FROM Medico m JOIN FETCH m.especialidade")
    @Override
    List<Medico> findAll();
}
