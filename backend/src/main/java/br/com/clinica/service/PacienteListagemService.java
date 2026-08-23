package br.com.clinica.service;

import br.com.clinica.dto.PacienteListagemItemDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Tabela de /pacientes (ver docs/specs/pacientes.md, seção 3a). Busca/filtro
// de convênio/status continuam client-side, como já eram com o mock — aqui
// só devolve a base completa já com os campos calculados.
@Service
public class PacienteListagemService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EntityManager entityManager;

    public PacienteListagemService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<PacienteListagemItemDto> listar() {
        LocalDate hoje = LocalDate.now();
        LocalDate seisMesesAtras = hoje.minusMonths(6);
        LocalDate umAnoAtras = hoje.minusMonths(12);

        Map<Integer, Linha> porPaciente = new LinkedHashMap<>();

        Query base = entityManager.createNativeQuery(
                "SELECT p.id_paciente, p.nome, p.ddd, p.numero, p.data_nascimento, cv.nome, " +
                        "  EXISTS(SELECT 1 FROM documento_anexo d WHERE d.id_paciente = p.id_paciente) " +
                        "FROM paciente p LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio"
        );
        for (Object[] l : (List<Object[]>) base.getResultList()) {
            Linha linha = new Linha();
            linha.id = ((Number) l[0]).intValue();
            linha.nome = (String) l[1];
            linha.ddd = (String) l[2];
            linha.numero = (String) l[3];
            linha.dataNascimento = paraLocalDate(l[4]);
            linha.convenio = (String) l[5];
            linha.temDocumento = Boolean.TRUE.equals(l[6]);
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

        List<PacienteListagemItemDto> resultado = new ArrayList<>();
        for (Linha linha : porPaciente.values()) {
            resultado.add(linha.paraDto(hoje, seisMesesAtras, umAnoAtras));
        }
        return resultado;
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

    private String formatarTelefone(String ddd, String numero) {
        if (numero.length() == 9) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 5), numero.substring(5));
        }
        if (numero.length() == 8) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 4), numero.substring(4));
        }
        return String.format("(%s) %s", ddd, numero);
    }

    private class Linha {
        Integer id;
        String nome;
        String ddd;
        String numero;
        LocalDate dataNascimento;
        String convenio;
        boolean temDocumento;
        LocalDate ultimaData;
        String ultimaEspecialidade;
        java.time.LocalDateTime proximaData;
        String proximaTxt;

        PacienteListagemItemDto paraDto(LocalDate hoje, LocalDate seisMesesAtras, LocalDate umAnoAtras) {
            String status;
            if (!temDocumento) {
                status = "inc";
            } else if (ultimaData == null) {
                status = "off";
            } else if (!ultimaData.isBefore(seisMesesAtras)) {
                status = "ok";
            } else if (!ultimaData.isBefore(umAnoAtras)) {
                status = "risk";
            } else {
                status = "off";
            }

            int idade = Period.between(dataNascimento, hoje).getYears();
            String ultimaTxt = ultimaData == null ? "—" : ultimaData.format(DIA_MES);

            return new PacienteListagemItemDto(
                    id, nome, formatarTelefone(ddd, numero), true,
                    convenio == null ? "Particular" : convenio,
                    idade,
                    ultimaData == null ? null : ultimaData.toString(), ultimaTxt,
                    ultimaEspecialidade == null ? "—" : ultimaEspecialidade,
                    proximaData == null ? null : proximaData.toString(),
                    proximaTxt == null ? "—" : proximaTxt, status
            );
        }
    }
}
