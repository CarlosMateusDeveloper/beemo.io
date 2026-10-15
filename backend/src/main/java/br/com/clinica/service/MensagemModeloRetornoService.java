package br.com.clinica.service;

import br.com.clinica.dto.ModeloMensagemDto;
import br.com.clinica.dto.ModeloMensagemRequest;
import br.com.clinica.dto.ComunicacaoDtos;
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
    private final ComunicacaoPolicyService comunicacao;

    public MensagemModeloRetornoService(MensagemModeloRetornoRepository repository,
                                        ComunicacaoPolicyService comunicacao) {
        this.repository = repository;
        this.comunicacao = comunicacao;
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
    public ModeloMensagemDto atualizar(String grupoStr, ModeloMensagemRequest request, int actor) {
        GrupoRetorno grupo = converter(grupoStr);
        MensagemModeloRetorno modelo = repository.findByGrupo(grupo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modelo não encontrado"));
        modelo.setTexto(request.texto());
        repository.save(modelo);
        comunicacao.criarModelo(new ComunicacaoDtos.Modelo(
                "retorno_" + grupo.name().toLowerCase(), "Retorno: " + grupo.name().toLowerCase().replace('_', ' '),
                "whatsapp", "pos_venda", "utilidade", "pt_BR", request.texto()), actor);
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
