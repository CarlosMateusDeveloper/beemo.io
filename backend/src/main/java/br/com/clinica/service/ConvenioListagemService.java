package br.com.clinica.service;

import br.com.clinica.dto.ConvenioListagemItemDto;
import br.com.clinica.model.Convenio;
import br.com.clinica.repository.ConvenioProcedimentoRepository;
import br.com.clinica.repository.ConvenioRepository;
import br.com.clinica.repository.RegraAuditoriaRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConvenioListagemService {

    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConvenioRepository convenioRepository;
    private final ConvenioProcedimentoRepository procedimentoRepository;
    private final RegraAuditoriaRepository regraRepository;

    public ConvenioListagemService(
            ConvenioRepository convenioRepository, ConvenioProcedimentoRepository procedimentoRepository,
            RegraAuditoriaRepository regraRepository
    ) {
        this.convenioRepository = convenioRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.regraRepository = regraRepository;
    }

    public List<ConvenioListagemItemDto> listar() {
        List<Convenio> convenios = convenioRepository.findAll();
        return convenios.stream().map(c -> new ConvenioListagemItemDto(
                c.getId(), c.getNome(), Boolean.TRUE.equals(c.getAtivo()),
                procedimentoRepository.countByConvenioId(c.getId()),
                regraRepository.countByConvenioId(c.getId()),
                c.getAtualizadoEm() == null ? "—" : c.getAtualizadoEm().format(DATA_FMT)
        )).collect(Collectors.toList());
    }
}
