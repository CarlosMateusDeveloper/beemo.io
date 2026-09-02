package br.com.clinica.service;

import br.com.clinica.dto.PacienteListagemItemDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Tabela de /pacientes (ver docs/specs/pacientes.md, seção 3a). listar() é o
// dump completo (usado por quem precisa da lista inteira, ex.: seleção de
// paciente no fluxo de Novo atendimento do Prontuário — ver
// prontuario/api.js fetchPacientesParaSelecao). paginar() é o que a própria
// tela /pacientes usa (issue #4): filtra/ordena/pagina em memória sobre o
// mesmo conjunto já calculado, então o que trafega pra tela é só uma página
// por vez, mesmo a base completa sendo lida do banco a cada chamada.
@Service
public class PacienteListagemService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EntityManager entityManager;

    public PacienteListagemService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PacienteListagemItemDto> listar() {
        LocalDate hoje = LocalDate.now();
        List<Linha> linhas = carregarLinhas();
        List<PacienteListagemItemDto> resultado = new ArrayList<>();
        for (Linha linha : linhas) {
            resultado.add(linha.paraDto(hoje));
        }
        return resultado;
    }

    public Page<PacienteListagemItemDto> paginar(
            String busca, String status, String filtroKpi, List<String> convenios,
            String ordem, String direcao, Pageable pageable
    ) {
        LocalDate hoje = LocalDate.now();
        List<Linha> linhas = carregarLinhas();

        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        String termoDigitos = termo.replaceAll("\\D", "");

        List<Linha> filtradas = linhas.stream()
                .filter(l -> termo.isEmpty()
                        || l.nome.toLowerCase(Locale.ROOT).contains(termo)
                        || (!termoDigitos.isEmpty() && (l.ddd + l.numero).contains(termoDigitos))
                        || (!termoDigitos.isEmpty() && l.cpf != null && l.cpf.contains(termoDigitos)))
                .filter(l -> !"Ativos".equals(status) || !"off".equals(l.status(hoje)))
                .filter(l -> filtroKpi == null || filtroKpi.isBlank() || filtroKpi.equals(l.status(hoje)))
                .filter(l -> convenios == null || convenios.isEmpty() || convenios.contains(l.convenio))
                .toList();

        Comparator<Linha> comparador = comparadorPor(ordem, hoje);
        if ("asc".equalsIgnoreCase(direcao)) {
            filtradas = filtradas.stream().sorted(comparador).toList();
        } else {
            filtradas = filtradas.stream().sorted(comparador.reversed()).toList();
        }

        int total = filtradas.size();
        int inicio = Math.min((int) pageable.getOffset(), total);
        int fim = Math.min(inicio + pageable.getPageSize(), total);
        List<PacienteListagemItemDto> pagina = filtradas.subList(inicio, fim).stream()
                .map(l -> l.paraDto(hoje))
                .toList();

        return new PageImpl<>(pagina, pageable, total);
    }

    private Comparator<Linha> comparadorPor(String ordem, LocalDate hoje) {
        Comparator<String> nullsFirstStr = Comparator.nullsFirst(Comparator.naturalOrder());
        Comparator<LocalDate> nullsFirstData = Comparator.nullsFirst(Comparator.naturalOrder());
        return switch (ordem == null ? "" : ordem) {
            case "proxima" -> Comparator.comparing((Linha l) -> l.proximaData, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(l -> l.status(hoje), nullsFirstStr);
            case "nome" -> Comparator.comparing(l -> l.nome, nullsFirstStr);
            default -> Comparator.comparing((Linha l) -> l.ultimaData, nullsFirstData); // "ultima" (default)
        };
    }

    @SuppressWarnings("unchecked")
    private List<Linha> carregarLinhas() {
        LocalDate hoje = LocalDate.now();
        Map<Integer, Linha> porPaciente = new LinkedHashMap<>();

        Query base = entityManager.createNativeQuery(
                "SELECT p.id_paciente, p.nome, p.cpf, p.ddd, p.numero, p.data_nascimento, cv.nome, " +
                        "  EXISTS(SELECT 1 FROM documento_anexo d WHERE d.id_paciente = p.id_paciente) " +
                        "FROM paciente p LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio"
        );
        for (Object[] l : (List<Object[]>) base.getResultList()) {
            Linha linha = new Linha();
            linha.id = ((Number) l[0]).intValue();
            linha.nome = (String) l[1];
            linha.cpf = (String) l[2];
            linha.ddd = (String) l[3];
            linha.numero = (String) l[4];
            linha.dataNascimento = paraLocalDate(l[5]);
            linha.convenio = (String) l[6];
            linha.temDocumento = Boolean.TRUE.equals(l[7]);
            porPaciente.put(linha.id, linha);
        }

        Query ultima = entityManager.createNativeQuery(
                "SELECT ultima.id_paciente, ultima.data_slot, e.nome FROM (" +
                        "  SELECT DISTINCT ON (c.id_paciente) c.id_paciente, a.data_slot, a.id_medico " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  WHERE a.data_slot <= :hoje AND c.status_consulta <> 'Cancelada' " +
                        "  ORDER BY c.id_paciente, a.data_slot DESC" +
                        ") ultima JOIN medico m ON m.id_medico = ultima.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade"
        );
        ultima.setParameter("hoje", hoje);
        for (Object[] l : (List<Object[]>) ultima.getResultList()) {
            Linha linha = porPaciente.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.ultimaData = paraLocalDate(l[1]);
            linha.ultimaEspecialidade = (String) l[2];
        }

        Query proxima = entityManager.createNativeQuery(
                "SELECT DISTINCT ON (c.id_paciente) c.id_paciente, a.data_slot, a.hora_slot " +
                        "FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "WHERE c.status_consulta IN ('Agendada', 'Confirmada') " +
                        "  AND (a.data_slot > :hoje OR (a.data_slot = :hoje AND a.hora_slot >= :agora)) " +
                        "ORDER BY c.id_paciente, a.data_slot ASC, a.hora_slot ASC"
        );
        proxima.setParameter("hoje", hoje);
        proxima.setParameter("agora", LocalTime.now());
        for (Object[] l : (List<Object[]>) proxima.getResultList()) {
            Linha linha = porPaciente.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            LocalDate data = paraLocalDate(l[1]);
            LocalTime hora = paraLocalTime(l[2]);
            String diaTxt = data.equals(hoje) ? "Hoje" : data.format(DIA_MES);
            linha.proximaData = data.atTime(hora);
            linha.proximaTxt = diaTxt + " " + hora.format(HORA);
        }

        return new ArrayList<>(porPaciente.values());
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }

    private LocalTime paraLocalTime(Object valor) {
        if (valor instanceof LocalTime localTime) return localTime;
        if (valor instanceof java.sql.Time sqlTime) return sqlTime.toLocalTime();
        throw new IllegalStateException("Tipo de hora inesperado: " + valor.getClass());
    }

    private static String formatarTelefone(String ddd, String numero) {
        if (numero.length() == 9) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 5), numero.substring(5));
        }
        if (numero.length() == 8) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 4), numero.substring(4));
        }
        return String.format("(%s) %s", ddd, numero);
    }

    // Mesmo formato de PacienteBuscaService (issue #11) — mantido aqui em vez
    // de compartilhado porque as duas classes calculam a linha de formas
    // muito diferentes (query única vs. 3 queries + classificação em Java).
    private static String mascararCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return null;
        return cpf.substring(0, 3) + ".***.***-" + cpf.substring(9);
    }

    private static class Linha {
        Integer id;
        String nome;
        String cpf;
        String ddd;
        String numero;
        LocalDate dataNascimento;
        String convenio;
        boolean temDocumento;
        LocalDate ultimaData;
        String ultimaEspecialidade;
        LocalDateTime proximaData;
        String proximaTxt;

        String status(LocalDate hoje) {
            LocalDate seisMesesAtras = hoje.minusMonths(6);
            LocalDate umAnoAtras = hoje.minusMonths(12);
            if (!temDocumento) return "inc";
            if (ultimaData == null) return "off";
            if (!ultimaData.isBefore(seisMesesAtras)) return "ok";
            if (!ultimaData.isBefore(umAnoAtras)) return "risk";
            return "off";
        }

        PacienteListagemItemDto paraDto(LocalDate hoje) {
            int idade = Period.between(dataNascimento, hoje).getYears();
            String ultimaTxt = ultimaData == null ? "—" : ultimaData.format(DIA_MES);

            return new PacienteListagemItemDto(
                    id, nome, mascararCpf(cpf), formatarTelefone(ddd, numero), true,
                    convenio == null ? "Particular" : convenio,
                    idade,
                    ultimaData == null ? null : ultimaData.toString(), ultimaTxt,
                    ultimaEspecialidade == null ? "—" : ultimaEspecialidade,
                    proximaData == null ? null : proximaData.toString(),
                    proximaTxt == null ? "—" : proximaTxt, status(hoje)
            );
        }
    }
}
