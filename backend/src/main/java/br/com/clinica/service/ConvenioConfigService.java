package br.com.clinica.service;

import br.com.clinica.model.Convenio;
import br.com.clinica.model.ConvenioPlano;
import br.com.clinica.model.ConvenioProcedimento;
import br.com.clinica.model.DocumentoObrigatorioConvenio;
import br.com.clinica.model.RegraAuditoria;
import br.com.clinica.repository.ConvenioPlanoRepository;
import br.com.clinica.repository.ConvenioProcedimentoRepository;
import br.com.clinica.repository.ConvenioRepository;
import br.com.clinica.repository.DocumentoObrigatorioConvenioRepository;
import br.com.clinica.repository.RegraAuditoriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

// CRUD dos sub-recursos da aba Convênios (planos, procedimentos, regras de
// auditoria, documentos obrigatórios). Toda escrita toca
// convenio.atualizadoEm, que alimenta a coluna "Última atualização" da
// listagem — é o mesmo convênio sendo configurado, então qualquer mudança
// nos filhos conta como atualização dele.
@Service
public class ConvenioConfigService {

    private final ConvenioRepository convenioRepository;
    private final ConvenioPlanoRepository planoRepository;
    private final ConvenioProcedimentoRepository procedimentoRepository;
    private final RegraAuditoriaRepository regraRepository;
    private final DocumentoObrigatorioConvenioRepository documentoRepository;

    public ConvenioConfigService(
            ConvenioRepository convenioRepository, ConvenioPlanoRepository planoRepository,
            ConvenioProcedimentoRepository procedimentoRepository, RegraAuditoriaRepository regraRepository,
            DocumentoObrigatorioConvenioRepository documentoRepository
    ) {
        this.convenioRepository = convenioRepository;
        this.planoRepository = planoRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.regraRepository = regraRepository;
        this.documentoRepository = documentoRepository;
    }

    private Convenio buscarConvenio(Integer idConvenio) {
        return convenioRepository.findById(idConvenio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado"));
    }

    private void tocarAtualizacao(Convenio convenio) {
        convenio.setAtualizadoEm(OffsetDateTime.now());
        convenioRepository.save(convenio);
    }

    // --- Planos ---

    public List<ConvenioPlano> listarPlanos(Integer idConvenio) {
        return planoRepository.listarPorConvenio(idConvenio);
    }

    @Transactional
    public ConvenioPlano criarPlano(Integer idConvenio, ConvenioPlano dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        dados.setId(null);
        dados.setConvenio(convenio);
        ConvenioPlano salvo = planoRepository.save(dados);
        tocarAtualizacao(convenio);
        return salvo;
    }

    @Transactional
    public ConvenioPlano atualizarPlano(Integer idConvenio, Integer idPlano, ConvenioPlano dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        ConvenioPlano existente = planoRepository.findById(idPlano)
                .filter(p -> p.getConvenio().getId().equals(idConvenio))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        existente.setNome(dados.getNome());
        existente.setCodigo(dados.getCodigo());
        existente.setAtivo(dados.getAtivo());
        ConvenioPlano salvo = planoRepository.save(existente);
        tocarAtualizacao(convenio);
        return salvo;
    }

    // --- Procedimentos ---

    public List<ConvenioProcedimento> listarProcedimentos(Integer idConvenio) {
        return procedimentoRepository.listarPorConvenio(idConvenio);
    }

    @Transactional
    public ConvenioProcedimento criarProcedimento(Integer idConvenio, ConvenioProcedimento dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        dados.setId(null);
        dados.setConvenio(convenio);
        ConvenioProcedimento salvo = procedimentoRepository.save(dados);
        tocarAtualizacao(convenio);
        return salvo;
    }

    @Transactional
    public ConvenioProcedimento atualizarProcedimento(Integer idConvenio, Integer idProcedimento, ConvenioProcedimento dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        ConvenioProcedimento existente = procedimentoRepository.findById(idProcedimento)
                .filter(p -> p.getConvenio().getId().equals(idConvenio))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado"));
        existente.setPlano(dados.getPlano());
        existente.setCodigo(dados.getCodigo());
        existente.setDescricao(dados.getDescricao());
        existente.setValorNegociado(dados.getValorNegociado());
        existente.setCobertura(dados.getCobertura());
        existente.setExigeAutorizacao(dados.getExigeAutorizacao());
        ConvenioProcedimento salvo = procedimentoRepository.save(existente);
        tocarAtualizacao(convenio);
        return salvo;
    }

    // --- Regras de auditoria ---

    public List<RegraAuditoria> listarRegras(Integer idConvenio) {
        return regraRepository.listarPorConvenio(idConvenio);
    }

    @Transactional
    public RegraAuditoria criarRegra(Integer idConvenio, RegraAuditoria dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        dados.setId(null);
        dados.setConvenio(convenio);
        RegraAuditoria salvo = regraRepository.save(dados);
        tocarAtualizacao(convenio);
        return salvo;
    }

    @Transactional
    public RegraAuditoria atualizarRegra(Integer idConvenio, Integer idRegra, RegraAuditoria dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        RegraAuditoria existente = regraRepository.findById(idRegra)
                .filter(r -> r.getConvenio().getId().equals(idConvenio))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regra não encontrada"));
        existente.setProcedimento(dados.getProcedimento());
        existente.setTipo(dados.getTipo());
        existente.setSeveridade(dados.getSeveridade());
        existente.setDescricao(dados.getDescricao());
        existente.setParametros(dados.getParametros());
        existente.setAtivo(dados.getAtivo());
        RegraAuditoria salvo = regraRepository.save(existente);
        tocarAtualizacao(convenio);
        return salvo;
    }

    // --- Documentos obrigatórios ---

    public List<DocumentoObrigatorioConvenio> listarDocumentos(Integer idConvenio) {
        return documentoRepository.listarPorConvenio(idConvenio);
    }

    @Transactional
    public DocumentoObrigatorioConvenio criarDocumento(Integer idConvenio, DocumentoObrigatorioConvenio dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        dados.setId(null);
        dados.setConvenio(convenio);
        DocumentoObrigatorioConvenio salvo = documentoRepository.save(dados);
        tocarAtualizacao(convenio);
        return salvo;
    }

    @Transactional
    public DocumentoObrigatorioConvenio atualizarDocumento(Integer idConvenio, Integer idDocumento, DocumentoObrigatorioConvenio dados) {
        Convenio convenio = buscarConvenio(idConvenio);
        DocumentoObrigatorioConvenio existente = documentoRepository.findById(idDocumento)
                .filter(d -> d.getConvenio().getId().equals(idConvenio))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado"));
        existente.setProcedimento(dados.getProcedimento());
        existente.setNomeDocumento(dados.getNomeDocumento());
        existente.setObrigatorio(dados.getObrigatorio());
        DocumentoObrigatorioConvenio salvo = documentoRepository.save(existente);
        tocarAtualizacao(convenio);
        return salvo;
    }
}
