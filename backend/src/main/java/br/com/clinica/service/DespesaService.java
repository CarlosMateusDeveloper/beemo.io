package br.com.clinica.service;

import br.com.clinica.dto.DespesaDto;
import br.com.clinica.dto.DespesaResumoDto;
import br.com.clinica.model.Despesa;
import br.com.clinica.repository.DespesaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

// Despesas/contas a pagar (/caixa/despesas) — primeiro dado de custo
// operacional do sistema além do repasse médico. Alimenta CaixaDreService
// (competência) e CaixaFluxoConsolidadoService (caixa, via pago_em).
//
// status/categoria são VARCHAR simples (ver Despesa.java) — sem o bug de
// enum nativo que pegou recurso_glosa/regra_auditoria, então repository.save()
// funciona direto, sem SQL nativo com CAST.
@Service
public class DespesaService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> CATEGORIAS_VALIDAS = Set.of(
            "aluguel", "folha", "fornecedores", "insumos", "impostos", "marketing", "manutencao", "servicos", "outros"
    );

    private final DespesaRepository repository;
    private final EntityManager entityManager;

    public DespesaService(DespesaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public Page<DespesaDto> listar(String status, String categoria, String periodo, Pageable pageable) {
        LocalDate vencimentoDe = periodo == null ? null : resolverInicio(periodo);
        return repository.buscar(status, categoria, vencimentoDe, null, pageable).map(this::paraDto);
    }

    public DespesaDto buscar(Integer id) {
        return paraDto(buscarEntidade(id));
    }

    // Soma agregada direto no banco em vez de paginar tudo pra cliente — mesmo
    // princípio de GlosaIndicadoresService/ConveniosKpiService.
    public DespesaResumoDto resumo(String periodo) {
        LocalDate inicio = resolverInicio(periodo);

        Object[] linha = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                        "  COALESCE(SUM(valor) FILTER (WHERE vencimento >= :inicio), 0), " +
                        "  COALESCE(SUM(valor) FILTER (WHERE status = 'pago' AND pago_em >= :inicio), 0), " +
                        "  COALESCE(SUM(valor) FILTER (WHERE status = 'pendente' AND vencimento >= :inicio), 0), " +
                        "  COALESCE(SUM(valor) FILTER (WHERE status = 'pendente' AND vencimento < CURRENT_DATE), 0), " +
                        "  COUNT(*) FILTER (WHERE status = 'pendente' AND vencimento < CURRENT_DATE) " +
                        "FROM despesa WHERE id_clinica = :idClinica"
        ).setParameter("inicio", inicio).setParameter("idClinica", TenantContext.id()).getSingleResult();

        return new DespesaResumoDto(
                (BigDecimal) linha[0], (BigDecimal) linha[1], (BigDecimal) linha[2], (BigDecimal) linha[3],
                ((Number) linha[4]).longValue()
        );
    }

    @Transactional
    public DespesaDto criar(Despesa dados) {
        validarCategoria(dados.getCategoria());
        dados.setId(null);
        dados.setIdClinica(TenantContext.id());
        dados.setStatus("pendente");
        dados.setPagoEm(null);
        dados.setCriadoEm(OffsetDateTime.now());
        return paraDto(repository.save(dados));
    }

    @Transactional
    public DespesaDto atualizar(Integer id, Despesa dados) {
        Despesa existente = buscarEntidade(id);
        if (!"pendente".equals(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível editar despesas pendentes");
        }
        validarCategoria(dados.getCategoria());
        existente.setDescricao(dados.getDescricao());
        existente.setCategoria(dados.getCategoria());
        existente.setValor(dados.getValor());
        existente.setVencimento(dados.getVencimento());
        existente.setFornecedor(dados.getFornecedor());
        existente.setObservacoes(dados.getObservacoes());
        return paraDto(repository.save(existente));
    }

    @Transactional
    public DespesaDto marcarPaga(Integer id, LocalDate dataPagamento) {
        Despesa existente = buscarEntidade(id);
        if (!"pendente".equals(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Despesa não está pendente");
        }
        existente.setStatus("pago");
        existente.setPagoEm(dataPagamento == null ? LocalDate.now() : dataPagamento);
        return paraDto(repository.save(existente));
    }

    @Transactional
    public DespesaDto cancelar(Integer id) {
        Despesa existente = buscarEntidade(id);
        if (!"pendente".equals(existente.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível cancelar despesas pendentes");
        }
        existente.setStatus("cancelado");
        return paraDto(repository.save(existente));
    }

    private Despesa buscarEntidade(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada"));
    }

    private void validarCategoria(String categoria) {
        if (categoria == null || !CATEGORIAS_VALIDAS.contains(categoria)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria inválida: " + categoria);
        }
    }

    private DespesaDto paraDto(Despesa d) {
        boolean atrasado = "pendente".equals(d.getStatus()) && d.getVencimento().isBefore(LocalDate.now());
        return new DespesaDto(
                d.getId(), d.getDescricao(), d.getCategoria(), d.getValor(),
                d.getVencimento().format(DIA_MES_ANO), d.getPagoEm() == null ? null : d.getPagoEm().format(DIA_MES_ANO),
                d.getStatus(), atrasado, d.getFornecedor(), d.getObservacoes()
        );
    }

    // Mesmas strings de período de ConveniosKpiService/CaixaDreService — consistência entre módulos.
    private LocalDate resolverInicio(String periodo) {
        LocalDate hoje = LocalDate.now();
        return switch (periodo == null ? "" : periodo) {
            case "Hoje" -> hoje;
            case "7 dias", "Últimos 7 dias" -> hoje.minusDays(6);
            case "90 dias", "Últimos 90 dias" -> hoje.minusDays(89);
            default -> hoje.minusDays(29); // "Últimos 30 dias"
        };
    }
}
