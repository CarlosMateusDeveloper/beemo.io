package br.com.clinica.service;

import br.com.clinica.dto.ReguaRetornoDto;
import br.com.clinica.dto.ReguaRetornoRequest;
import br.com.clinica.model.ReguaRetorno;
import br.com.clinica.repository.ReguaRetornoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReguaRetornoService {

    private final ReguaRetornoRepository repository;

    public ReguaRetornoService(ReguaRetornoRepository repository) {
        this.repository = repository;
    }

    public List<ReguaRetornoDto> listar() {
        return repository.findAllByOrderByGrupo().stream().map(this::paraDto).toList();
    }

    @Transactional
    public ReguaRetornoDto atualizar(Integer id, ReguaRetornoRequest request) {
        ReguaRetorno regua = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Régua não encontrada"));
        if (request.prazoDias() != null) regua.setPrazoDias(request.prazoDias().shortValue());
        if (request.ativa() != null) regua.setAtiva(request.ativa());
        return paraDto(repository.save(regua));
    }

    private ReguaRetornoDto paraDto(ReguaRetorno r) {
        return new ReguaRetornoDto(r.getId(), r.getGrupo().name(), r.getPrazoDias(), r.getAtiva());
    }
}
