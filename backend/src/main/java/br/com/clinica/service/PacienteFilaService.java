package br.com.clinica.service;

import br.com.clinica.dto.PacienteFilaItemDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Fila do dia (kanban /pacientes, docs/specs/pacientes.md seção 3b). Mapeia
// status_consulta_enum pras 5 colunas do fluxo; "Agendada"/"Faltou" cancelada
// ficam fora do board (não fazem parte da fila de hoje em andamento).
@Service
public class PacienteFilaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> COLUNA_POR_STATUS = Map.of(
            "Agendada", "agendado",
            "Confirmada", "confirmado",
            "Em Espera", "recepcao",
            "Em Atendimento", "atendimento",
            "Realizada", "concluido"
    );

    private final EntityManager entityManager;

    public PacienteFilaService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<PacienteFilaItemDto> listarHoje() {
        LocalDate hoje = LocalDate.now();

        Query query = entityManager.createNativeQuery(
                "SELECT c.status_consulta::text, p.nome, a.hora_slot, e.nome, cv.nome, fa.checkin_em " +
                        "FROM consulta c " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "LEFT JOIN convenio cv ON cv.id_convenio = p.id_convenio " +
                        "LEFT JOIN fila_atendimento fa ON fa.id_consulta = c.id_consulta " +
                        "WHERE a.data_slot = :hoje " +
                        "  AND c.status_consulta::text IN ('Agendada', 'Confirmada', 'Em Espera', 'Em Atendimento', 'Realizada') " +
                        "ORDER BY a.hora_slot ASC"
        );
        query.setParameter("hoje", hoje);

        LocalDateTime agora = LocalDateTime.now();
        List<PacienteFilaItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            String statusConsulta = (String) l[0];
            String coluna = COLUNA_POR_STATUS.get(statusConsulta);
            if (coluna == null) continue;

            String nome = (String) l[1];
            String hora = paraLocalTime(l[2]).format(HORA);
            String especialidade = (String) l[3];
            String convenio = (String) l[4];
            Object checkinRaw = l[5];

            Integer esperaMin = null;
            if ("recepcao".equals(coluna) && checkinRaw != null) {
                LocalDateTime checkin = paraLocalDateTime(checkinRaw);
                esperaMin = (int) Duration.between(checkin, agora).toMinutes();
            }

            resultado.add(new PacienteFilaItemDto(coluna, nome, hora, especialidade, esperaMin, convenio));
        }

        // Recepção ordena por maior espera primeiro; as demais colunas já vieram por horário.
        resultado.sort(Comparator.comparing((PacienteFilaItemDto d) -> "recepcao".equals(d.coluna()) ? 0 : 1)
                .thenComparing(d -> "recepcao".equals(d.coluna()) ? -orZero(d.esperaMin()) : 0));

        return resultado;
    }

    private int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private java.time.LocalTime paraLocalTime(Object valor) {
        if (valor instanceof java.time.LocalTime localTime) return localTime;
        if (valor instanceof java.sql.Time sqlTime) return sqlTime.toLocalTime();
        throw new IllegalStateException("Tipo de hora inesperado: " + valor.getClass());
    }

    private LocalDateTime paraLocalDateTime(Object valor) {
        if (valor instanceof LocalDateTime localDateTime) return localDateTime;
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("Tipo de timestamp inesperado: " + valor.getClass());
    }
}
