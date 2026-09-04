package br.com.clinica.service;

import br.com.clinica.dto.ModeloMensagemDto;
import br.com.clinica.dto.ModeloMensagemRequest;
import br.com.clinica.model.GrupoRetorno;
import br.com.clinica.model.MensagemModeloRetorno;
import br.com.clinica.repository.MensagemModeloRetornoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MensagemModeloRetornoService {

    private final MensagemModeloRetornoRepository repository;

    public MensagemModeloRetornoService(MensagemModeloRetornoRepository repository) {
        this.repository = repository;
    }

    public List<ModeloMensagemDto> listar() {
        return repository.findAll().stream()
                .map(m -> new ModeloMensagemDto(m.getGrupo().name(), m.getTexto()))
                .toList();
    }

    public String buscarTexto(GrupoRetorno grupo) {
        return repository.findByGrupo(grupo)
                .map(MensagemModeloRetorno::getTexto)
                .orElse("");
    }

    @Transactional
    public ModeloMensagemDto atualizar(String grupoStr, ModeloMensagemRequest request) {
        GrupoRetorno grupo = converter(grupoStr);
        MensagemModeloRetorno modelo = repository.findByGrupo(grupo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modelo não encontrado"));
        modelo.setTexto(request.texto());
        repository.save(modelo);
        return new ModeloMensagemDto(grupo.name(), modelo.getTexto());
    }

    private GrupoRetorno converter(String grupo) {
        try {
            return GrupoRetorno.valueOf(grupo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo inválido: " + grupo);
        }
    }
}
