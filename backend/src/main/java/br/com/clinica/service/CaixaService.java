package br.com.clinica.service;

import br.com.clinica.dto.CaixaResponse;
import br.com.clinica.dto.FecharTurnoRequest;
import br.com.clinica.dto.FecharTurnoResponse;
import br.com.clinica.dto.RegistrarPagamentoRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// Agrega a tela /caixa a partir do que o schema sustenta de verdade: agenda +
// consulta + fatura (fila "a receber hoje") e pagamento (movimento do dia,
// totais do turno). "Turno de caixa" é auto-aberto no primeiro acesso do dia
// (a tela não tem ação de "abrir caixa" — só "fechar") e "operador" é
// placeholder, igual ao resto do app: não há sessão/login real ainda.
@Service
public class CaixaService {

    private static final int ID_CLINICA_ATUAL = 1;
    private static final String OPERADOR_PLACEHOLDER = "Ana Souza";
    private static final String OPERADOR_CARGO_PLACEHOLDER = "Administradora";
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] MESES_PT = {
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    };

    private final EntityManager entityManager;

    public CaixaService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public CaixaResponse turnoAtual() {
        Integer idTurno = turnoAbertoHojeOuCriar();

        Object horaAberturaRaw = entityManager.createNativeQuery(
                "SELECT aberto_em FROM turno_caixa WHERE id_turno_caixa = :id"
        ).setParameter("id", idTurno).getSingleResult();

        List<Object[]> pagamentosHoje = pagamentosDoTurno(idTurno);

        BigDecimal totalHoje = BigDecimal.ZERO;
        BigDecimal dinheiro = BigDecimal.ZERO;
        BigDecimal cartao = BigDecimal.ZERO;
        BigDecimal pix = BigDecimal.ZERO;
        for (Object[] linha : pagamentosHoje) {
            BigDecimal valor = (BigDecimal) linha[5];
            String metodo = (String) linha[4];
            totalHoje = totalHoje.add(valor);
            switch (metodo) {
                case "dinheiro" -> dinheiro = dinheiro.add(valor);
                case "cartao_debito", "cartao_credito" -> cartao = cartao.add(valor);
                case "pix" -> pix = pix.add(valor);
                default -> {
                }
            }
        }

        CaixaResponse.TurnoDto turno = new CaixaResponse.TurnoDto(
                dataLabelHoje(), formatarHoraTimestamp(horaAberturaRaw), OPERADOR_PLACEHOLDER, OPERADOR_CARGO_PLACEHOLDER,
                totalHoje, pagamentosHoje.size(), dinheiro, cartao, pix
        );

        return new CaixaResponse(turno, buscarReceberHoje(), mapearMovimento(pagamentosHoje));
    }

    @Transactional
    public void registrarPagamento(RegistrarPagamentoRequest req) {
        Integer idTurno = turnoAbertoHojeOuCriar();
        BigDecimal desconto = req.desconto() == null ? BigDecimal.ZERO : req.desconto();
        BigDecimal valorPago = req.valor().subtract(desconto).max(BigDecimal.ZERO);
        String metodoBanco = metodoParaBanco(req.metodo());
        Integer parcelas = "credito".equals(req.metodo()) ? req.parcelas() : null;

        entityManager.createNativeQuery(
                "INSERT INTO pagamento (id_fatura, metodo, valor_pago, parcelas, desconto, motivo_desconto, id_turno_caixa) " +
                        "VALUES (:idFatura, CAST(:metodo AS metodo_pagamento), :valorPago, :parcelas, :desconto, :motivo, :idTurno)"
        )
                .setParameter("idFatura", req.idFatura())
                .setParameter("metodo", metodoBanco)
                .setParameter("valorPago", valorPago)
                .setParameter("parcelas", parcelas)
                .setParameter("desconto", desconto)
                .setParameter("motivo", req.motivoDesconto())
                .setParameter("idTurno", idTurno)
                .executeUpdate();

        entityManager.createNativeQuery(
                "UPDATE fatura SET status = 'pago' WHERE id_fatura = :idFatura"
        ).setParameter("idFatura", req.idFatura()).executeUpdate();
    }

    @Transactional
    public FecharTurnoResponse fecharTurno(FecharTurnoRequest req) {
        Integer idTurno = turnoAbertoHojeOuCriar();

        BigDecimal dinheiroEsperado = (BigDecimal) entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(valor_pago), 0) FROM pagamento WHERE id_turno_caixa = :idTurno AND metodo = 'dinheiro'"
        ).setParameter("idTurno", idTurno).getSingleResult();

        BigDecimal contado = req.dinheiroContado() == null ? BigDecimal.ZERO : req.dinheiroContado();
        BigDecimal diferenca = contado.subtract(dinheiroEsperado);
        boolean temDiferenca = diferenca.abs().compareTo(new BigDecimal("0.005")) >= 0;

        entityManager.createNativeQuery(
                "UPDATE turno_caixa SET fechado_em = now(), dinheiro_contado = :contado, diferenca = :diferenca, observacao = :obs " +
                        "WHERE id_turno_caixa = :idTurno"
        )
                .setParameter("contado", contado)
                .setParameter("diferenca", diferenca)
                .setParameter("obs", req.observacao())
                .setParameter("idTurno", idTurno)
                .executeUpdate();

        return new FecharTurnoResponse(temDiferenca, diferenca);
    }

    private Integer turnoAbertoHojeOuCriar() {
        Integer existente = buscarTurnoAbertoHoje();
        if (existente != null) return existente;

        entityManager.createNativeQuery(
                "INSERT INTO turno_caixa (id_clinica, operador_nome) VALUES (:idClinica, :operador)"
        ).setParameter("idClinica", ID_CLINICA_ATUAL).setParameter("operador", OPERADOR_PLACEHOLDER).executeUpdate();

        Integer criado = buscarTurnoAbertoHoje();
        if (criado == null) {
            throw new IllegalStateException("Falha ao abrir turno de caixa");
        }
        return criado;
    }

    @SuppressWarnings("unchecked")
    private Integer buscarTurnoAbertoHoje() {
        List<Object> encontrados = entityManager.createNativeQuery(
                "SELECT id_turno_caixa FROM turno_caixa " +
                        "WHERE id_clinica = :idClinica AND fechado_em IS NULL AND aberto_em::date = CURRENT_DATE " +
                        "ORDER BY aberto_em DESC LIMIT 1"
        ).setParameter("idClinica", ID_CLINICA_ATUAL).getResultList();
        return encontrados.isEmpty() ? null : ((Number) encontrados.get(0)).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<CaixaResponse.ReceberItemDto> buscarReceberHoje() {
        List<Object[]> linhas = entityManager.createNativeQuery(
                "SELECT f.id_fatura, a.hora_slot, p.nome, c.tipo::text, cv.nome, f.valor, f.status::text " +
                        "FROM consulta c " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio " +
                        "JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "WHERE a.data_slot = CURRENT_DATE " +
                        "ORDER BY a.hora_slot"
        ).getResultList();

        return linhas.stream().map(linha -> new CaixaResponse.ReceberItemDto(
                ((Number) linha[0]).intValue(),
                formatarHora(linha[1]),
                (String) linha[2],
                (String) linha[3],
                (String) linha[4],
                (BigDecimal) linha[5],
                "pago".equals(linha[6])
        )).collect(Collectors.toList());
    }

    // Colunas: 0 id_pagamento, 1 pago_em, 2 nome paciente, 3 tipo consulta, 4 metodo, 5 valor_pago, 6 operador do turno
    @SuppressWarnings("unchecked")
    private List<Object[]> pagamentosDoTurno(Integer idTurno) {
        return entityManager.createNativeQuery(
                "SELECT pg.id_pagamento, pg.pago_em, p.nome, c.tipo::text, pg.metodo::text, pg.valor_pago, tc.operador_nome " +
                        "FROM pagamento pg " +
                        "JOIN fatura f ON f.id_fatura = pg.id_fatura " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "JOIN turno_caixa tc ON tc.id_turno_caixa = pg.id_turno_caixa " +
                        "WHERE pg.id_turno_caixa = :idTurno " +
                        "ORDER BY pg.pago_em DESC"
        ).setParameter("idTurno", idTurno).getResultList();
    }

    private List<CaixaResponse.MovimentoItemDto> mapearMovimento(List<Object[]> linhas) {
        return linhas.stream().map(linha -> {
            String metodo = (String) linha[4];
            return new CaixaResponse.MovimentoItemDto(
                    ((Number) linha[0]).intValue(),
                    formatarHoraTimestamp(linha[1]),
                    (String) linha[2],
                    (String) linha[3],
                    metodoParaFrontend(metodo),
                    metodoLabel(metodo),
                    (BigDecimal) linha[5],
                    "in",
                    (String) linha[6]
            );
        }).collect(Collectors.toList());
    }

    private String metodoParaBanco(String metodoFrontend) {
        return switch (metodoFrontend) {
            case "dinheiro" -> "dinheiro";
            case "debito" -> "cartao_debito";
            case "credito" -> "cartao_credito";
            case "pix" -> "pix";
            default -> throw new IllegalArgumentException("Método de pagamento inválido: " + metodoFrontend);
        };
    }

    private String metodoParaFrontend(String metodoBanco) {
        return switch (metodoBanco) {
            case "cartao_debito" -> "debito";
            case "cartao_credito" -> "credito";
            default -> metodoBanco;
        };
    }

    private String metodoLabel(String metodoBanco) {
        return switch (metodoBanco) {
            case "dinheiro" -> "Dinheiro";
            case "cartao_debito" -> "Débito";
            case "cartao_credito" -> "Crédito";
            case "pix" -> "Pix";
            case "boleto" -> "Boleto";
            case "convenio" -> "Convênio";
            default -> metodoBanco;
        };
    }

    private String diaSemanaPt(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Segunda";
            case TUESDAY -> "Terça";
            case WEDNESDAY -> "Quarta";
            case THURSDAY -> "Quinta";
            case FRIDAY -> "Sexta";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private String dataLabelHoje() {
        LocalDate hoje = LocalDate.now();
        return String.format("%s, %d de %s de %d",
                diaSemanaPt(hoje.getDayOfWeek()), hoje.getDayOfMonth(), MESES_PT[hoje.getMonthValue() - 1], hoje.getYear());
    }

    private String formatarHora(Object valor) {
        if (valor instanceof java.time.LocalTime lt) return lt.format(HORA_FMT);
        if (valor instanceof java.sql.Time t) return t.toLocalTime().format(HORA_FMT);
        return String.valueOf(valor);
    }

    private String formatarHoraTimestamp(Object valor) {
        if (valor instanceof java.time.OffsetDateTime odt) return odt.toLocalTime().format(HORA_FMT);
        if (valor instanceof java.time.LocalDateTime ldt) return ldt.toLocalTime().format(HORA_FMT);
        if (valor instanceof java.time.Instant instant) {
            return instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(HORA_FMT);
        }
        if (valor instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalTime().format(HORA_FMT);
        return String.valueOf(valor);
    }
}
