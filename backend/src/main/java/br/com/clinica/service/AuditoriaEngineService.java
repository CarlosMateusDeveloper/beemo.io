package br.com.clinica.service;

import br.com.clinica.model.*;
import br.com.clinica.repository.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Motor de auditoria (docs/specs/convenios.md, seção "Aba — Auditoria").
// Roda quando um atendimento é finalizado (chamado por
// ProntuarioEscritaService), aplica as regras ativas do convênio do
// paciente e grava o resultado em auditoria_atendimento/auditoria_item.
//
// Só 4 dos 9 tipos de regra são avaliáveis nesta fase — não existe no
// schema um link entre consulta e convenio_procedimento (só
// paciente.id_convenio, geral), então regras que dependem de saber QUAL
// procedimento foi realizado (cobertura, código, quantidade, profissional
// habilitado, divergência atendimento×faturamento) não avaliam e não geram
// item — ficam de fora silenciosamente (o enum status_auditoria_item só
// tem ok/falha, sem um terceiro estado "não avaliado"; a limitação está
// documentada na spec, não escondida).
@Service
public class AuditoriaEngineService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEngineService.class);

    private static final Set<RegraAuditoria.TipoRegra> TIPOS_AVALIAVEIS = Set.of(
            RegraAuditoria.TipoRegra.autorizacao_obrigatoria,
            RegraAuditoria.TipoRegra.documento_obrigatorio,
            RegraAuditoria.TipoRegra.paciente_inelegivel,
            RegraAuditoria.TipoRegra.prazo_faturamento_excedido
    );

    private final ConsultaRepository consultaRepository;
    private final RegraAuditoriaRepository regraRepository;
    private final DocumentoObrigatorioConvenioRepository documentoObrigatorioRepository;
    private final AutorizacaoConvenioRepository autorizacaoRepository;
    private final FaturaRepository faturaRepository;
    private final EntityManager entityManager;

    public AuditoriaEngineService(
            ConsultaRepository consultaRepository, RegraAuditoriaRepository regraRepository,
            DocumentoObrigatorioConvenioRepository documentoObrigatorioRepository,
            AutorizacaoConvenioRepository autorizacaoRepository, FaturaRepository faturaRepository,
            EntityManager entityManager
    ) {
        this.consultaRepository = consultaRepository;
        this.regraRepository = regraRepository;
        this.documentoObrigatorioRepository = documentoObrigatorioRepository;
        this.autorizacaoRepository = autorizacaoRepository;
        this.faturaRepository = faturaRepository;
        this.entityManager = entityManager;
    }

    // Nunca propaga exceção — uma falha no motor não pode derrubar a
    // assinatura do prontuário, que é a ação clínica principal.
    @Transactional
    public void avaliarAtendimento(Integer idConsulta) {
        try {
            avaliar(idConsulta);
        } catch (Exception e) {
            log.warn("Falha ao avaliar auditoria do atendimento (consulta {}): {}", idConsulta, e.getMessage(), e);
        }
    }

    private void avaliar(Integer idConsulta) {
        Consulta consulta = consultaRepository.findById(idConsulta).orElse(null);
        if (consulta == null) return;

        Paciente paciente = consulta.getPaciente();
        Convenio convenio = paciente.getConvenio();
        if (convenio == null) return; // atendimento particular — nada a auditar

        List<RegraAuditoria> regras = regraRepository.listarPorConvenio(convenio.getId());
        List<ItemAvaliado> itens = new ArrayList<>();

        for (RegraAuditoria regra : regras) {
            if (!Boolean.TRUE.equals(regra.getAtivo())) continue;
            if (regra.getProcedimento() != null) continue; // regra específica de procedimento — não avaliável
            if (!TIPOS_AVALIAVEIS.contains(regra.getTipo())) continue;

            ItemAvaliado item = switch (regra.getTipo()) {
                case autorizacao_obrigatoria -> avaliarAutorizacao(regra, idConsulta);
                case documento_obrigatorio -> avaliarDocumento(regra, convenio.getId(), idConsulta);
                case paciente_inelegivel -> avaliarElegibilidade(regra, convenio);
                case prazo_faturamento_excedido -> avaliarPrazoFaturamento(regra, consulta.getIdAgenda());
                default -> null;
            };
            if (item != null) itens.add(item);
        }

        String status = statusFinal(itens);
        BigDecimal valorEmRisco = "aprovado".equals(status) ? BigDecimal.ZERO : valorEmRisco(idConsulta);

        Integer idAuditoria = ((Number) entityManager.createNativeQuery(
                "INSERT INTO auditoria_atendimento (id_consulta, status, valor_em_risco, avaliado_em) " +
                        "VALUES (:idConsulta, CAST(:status AS status_auditoria_atendimento), :valorEmRisco, now()) " +
                        "RETURNING id_auditoria_atendimento"
        )
                .setParameter("idConsulta", idConsulta)
                .setParameter("status", status)
                .setParameter("valorEmRisco", valorEmRisco)
                .getSingleResult()).intValue();

        for (ItemAvaliado item : itens) {
            entityManager.createNativeQuery(
                    "INSERT INTO auditoria_item (id_auditoria_atendimento, id_regra, status, descricao, severidade, acao_recomendada) " +
                            "VALUES (:idAuditoria, :idRegra, CAST(:status AS status_auditoria_item), :descricao, " +
                            "  CAST(:severidade AS severidade_regra), :acaoRecomendada)"
            )
                    .setParameter("idAuditoria", idAuditoria)
                    .setParameter("idRegra", item.idRegra())
                    .setParameter("status", item.status())
                    .setParameter("descricao", item.descricao())
                    .setParameter("severidade", item.severidade())
                    .setParameter("acaoRecomendada", item.acaoRecomendada())
                    .executeUpdate();
        }
    }

    // Auto-resolve (spec seção 7): considera resolvida se já existe uma
    // autorização com status aprovado OU número de guia preenchido — não
    // exige que alguém tenha marcado "autorizado" manualmente se a guia já
    // foi emitida.
    private ItemAvaliado avaliarAutorizacao(RegraAuditoria regra, Integer idConsulta) {
        boolean encontrada = autorizacaoRepository.findByIdConsulta(idConsulta)
                .map(a -> "autorizado".equals(a.getStatus()) || (a.getNumeroGuia() != null && !a.getNumeroGuia().isBlank()))
                .orElse(false);
        if (encontrada) {
            return new ItemAvaliado(regra.getId(), "ok", "Autorização localizada para o atendimento", null, null);
        }
        return new ItemAvaliado(
                regra.getId(), "falha", "Autorização não encontrada para o atendimento",
                regra.getSeveridade().name(), "Adicionar autorização"
        );
    }

    // documento_anexo não tem catálogo por tipo/nome, então não dá pra
    // confirmar que o anexo É o documento exigido — só que existe algum
    // anexo pra essa consulta (mesmo princípio do checklist de documentos
    // disponíveis em GlosaDetalheService).
    private ItemAvaliado avaliarDocumento(RegraAuditoria regra, Integer idConvenio, Integer idConsulta) {
        List<String> exigidos = documentoObrigatorioRepository.listarPorConvenio(idConvenio).stream()
                .filter(d -> d.getProcedimento() == null && Boolean.TRUE.equals(d.getObrigatorio()))
                .map(DocumentoObrigatorioConvenio::getNomeDocumento)
                .toList();
        if (exigidos.isEmpty()) {
            return new ItemAvaliado(regra.getId(), "ok", "Nenhum documento obrigatório configurado para este convênio", null, null);
        }
        long anexos = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM documento_anexo WHERE id_consulta = :idConsulta"
        ).setParameter("idConsulta", idConsulta).getSingleResult()).longValue();
        if (anexos > 0) {
            return new ItemAvaliado(regra.getId(), "ok", "Documentação anexada ao atendimento", null, null);
        }
        return new ItemAvaliado(
                regra.getId(), "falha",
                "Documento(s) obrigatório(s) não encontrado(s): " + String.join(", ", exigidos),
                regra.getSeveridade().name(), "Anexar documento obrigatório"
        );
    }

    private ItemAvaliado avaliarElegibilidade(RegraAuditoria regra, Convenio convenio) {
        if (Boolean.TRUE.equals(convenio.getAtivo())) {
            return new ItemAvaliado(regra.getId(), "ok", "Convênio do paciente está ativo", null, null);
        }
        return new ItemAvaliado(
                regra.getId(), "falha", "Convênio do paciente está inativo",
                regra.getSeveridade().name(), "Verificar elegibilidade do paciente junto ao convênio"
        );
    }

    // parametros.prazoDias (JSONB da regra) — sem essa chave numérica, não
    // avaliável (nada configurado). "agenda.data_slot - CURRENT_DATE" direto
    // no SQL evita ambiguidade de tipo Date/LocalDate no retorno nativo.
    private ItemAvaliado avaliarPrazoFaturamento(RegraAuditoria regra, Integer idAgenda) {
        Object prazoConfig = regra.getParametros().get("prazoDias");
        if (!(prazoConfig instanceof Number prazoNumero)) return null;

        long dias = ((Number) entityManager.createNativeQuery(
                "SELECT (CURRENT_DATE - data_slot) FROM agenda WHERE id_agenda = :idAgenda"
        ).setParameter("idAgenda", idAgenda).getSingleResult()).longValue();

        int prazoDias = prazoNumero.intValue();
        if (dias <= prazoDias) {
            return new ItemAvaliado(regra.getId(), "ok", "Atendimento faturado dentro do prazo (" + dias + " de " + prazoDias + " dias)", null, null);
        }
        return new ItemAvaliado(
                regra.getId(), "falha",
                "Atendimento finalizado " + dias + " dias após a data do atendimento — prazo do convênio é " + prazoDias + " dias",
                regra.getSeveridade().name(), "Faturar com urgência ou registrar justificativa de atraso"
        );
    }

    private BigDecimal valorEmRisco(Integer idConsulta) {
        return faturaRepository.findByIdConsulta(idConsulta).map(Fatura::getValor).orElse(BigDecimal.ZERO);
    }

    private String statusFinal(List<ItemAvaliado> itens) {
        boolean temCritica = itens.stream().anyMatch(i -> "falha".equals(i.status()) && "critica".equals(i.severidade()));
        if (temCritica) return "bloqueado";
        boolean temFalha = itens.stream().anyMatch(i -> "falha".equals(i.status()));
        return temFalha ? "atencao" : "aprovado";
    }

    private record ItemAvaliado(Integer idRegra, String status, String descricao, String severidade, String acaoRecomendada) {
    }
}
