package br.com.clinica.service;

import br.com.clinica.dto.AuditoriaDetalheDto;
import br.com.clinica.dto.AuditoriaListagemItemDto;
import br.com.clinica.dto.AuditoriaResumoDto;
import br.com.clinica.model.AuditoriaAtendimento;
import br.com.clinica.model.AuditoriaItem;
import br.com.clinica.repository.AuditoriaAtendimentoRepository;
import br.com.clinica.repository.AuditoriaItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Leitura da aba Auditoria: lista paginada, resumo do topo e detalhe de um
// atendimento — mesmo padrão de enriquecimento em lote de GlosaListagemService
// (evita N+1) e de contexto via SQL nativo de GlosaDetalheService.
@Service
public class AuditoriaListagemService {

    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuditoriaAtendimentoRepository repository;
    private final AuditoriaItemRepository itemRepository;
    private final EntityManager entityManager;

    public AuditoriaListagemService(
            AuditoriaAtendimentoRepository repository, AuditoriaItemRepository itemRepository, EntityManager entityManager
    ) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.entityManager = entityManager;
    }

    public Page<AuditoriaListagemItemDto> listar(String status, Pageable pageable) {
        Page<AuditoriaAtendimento> pagina = repository.buscar(status, pageable);
        Map<Integer, Object[]> enriquecimento = buscarEnriquecimento(pagina.getContent());

        return pagina.map(a -> {
            Object[] e = enriquecimento.get(a.getId());
            return new AuditoriaListagemItemDto(
                    a.getId(),
                    e == null ? null : (String) e[0],
                    e == null ? null : (String) e[1],
                    e == null ? null : (String) e[2],
                    a.getStatus(),
                    a.getValorEmRisco(),
                    a.getAvaliadoEm() == null ? "—" : a.getAvaliadoEm().format(DIA_MES_ANO_HORA)
            );
        });
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object[]> buscarEnriquecimento(List<AuditoriaAtendimento> lista) {
        Map<Integer, Object[]> resultado = new HashMap<>();
        if (lista.isEmpty()) return resultado;

        List<Integer> ids = lista.stream().map(AuditoriaAtendimento::getId).toList();
        Query query = entityManager.createNativeQuery(
                "SELECT aa.id_auditoria_atendimento, p.nome, c.tipo::text, cv.nome " +
                        "FROM auditoria_atendimento aa " +
                        "JOIN consulta c ON c.id_consulta = aa.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio " +
                        "WHERE aa.id_auditoria_atendimento IN :ids"
        );
        query.setParameter("ids", ids);
        for (Object[] linha : (List<Object[]>) query.getResultList()) {
            Integer id = ((Number) linha[0]).intValue();
            resultado.put(id, new Object[]{linha[1], linha[2], linha[3]});
        }
        return resultado;
    }

    public AuditoriaResumoDto resumo() {
        Object[] linha = (Object[]) entityManager.createNativeQuery(
                "SELECT COUNT(*), " +
                        "  COUNT(*) FILTER (WHERE status = 'aprovado'), " +
                        "  COUNT(*) FILTER (WHERE status = 'atencao'), " +
                        "  COUNT(*) FILTER (WHERE status = 'bloqueado'), " +
                        "  COALESCE(SUM(valor_em_risco), 0) " +
                        "FROM auditoria_atendimento"
        ).getSingleResult();

        return new AuditoriaResumoDto(
                ((Number) linha[0]).longValue(), ((Number) linha[1]).longValue(),
                ((Number) linha[2]).longValue(), ((Number) linha[3]).longValue(),
                (BigDecimal) linha[4]
        );
    }

    public AuditoriaDetalheDto detalhar(Integer id) {
        AuditoriaAtendimento auditoria = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auditoria não encontrada"));

        Object[] contexto;
        try {
            contexto = (Object[]) entityManager.createNativeQuery(
                    "SELECT p.nome, c.tipo::text, m.nome, " +
                            "  to_char(a.data_slot, 'DD/MM/YYYY') || ' ' || to_char(a.hora_slot, 'HH24:MI'), cv.nome " +
                            "FROM auditoria_atendimento aa " +
                            "JOIN consulta c ON c.id_consulta = aa.id_consulta " +
                            "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                            "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                            "JOIN medico m ON m.id_medico = a.id_medico " +
                            "LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio " +
                            "WHERE aa.id_auditoria_atendimento = :id"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento/convênio da auditoria não encontrados");
        }

        AuditoriaDetalheDto.AtendimentoResumoDto atendimento = new AuditoriaDetalheDto.AtendimentoResumoDto(
                (String) contexto[0], (String) contexto[1], (String) contexto[2], (String) contexto[3]
        );

        List<AuditoriaDetalheDto.ItemDto> itens = itemRepository.findByIdAuditoriaAtendimentoOrderByIdAsc(id).stream()
                .map(this::paraItemDto)
                .toList();

        return new AuditoriaDetalheDto(
                auditoria.getId(), auditoria.getStatus(), auditoria.getValorEmRisco(),
                auditoria.getAvaliadoEm() == null ? "—" : auditoria.getAvaliadoEm().format(DIA_MES_ANO_HORA),
                (String) contexto[4], atendimento, itens
        );
    }

    private AuditoriaDetalheDto.ItemDto paraItemDto(AuditoriaItem item) {
        return new AuditoriaDetalheDto.ItemDto(item.getStatus(), item.getDescricao(), item.getSeveridade(), item.getAcaoRecomendada());
    }
}
