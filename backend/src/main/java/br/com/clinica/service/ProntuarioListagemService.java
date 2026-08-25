package br.com.clinica.service;

import br.com.clinica.dto.ProntuarioListagemItemDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Tabela de /prontuario (docs/specs/prontuario.md, seção 4). Um paciente por
// linha, com o atendimento (consulta+prontuario) mais recente dele — quem
// nunca teve um prontuário documentado não aparece aqui. Busca/filtro
// continuam client-side, mesmo padrão de /pacientes e /medicos.
@Service
public class ProntuarioListagemService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager entityManager;

    public ProntuarioListagemService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<ProntuarioListagemItemDto> listar() {
        LocalDate hoje = LocalDate.now();
        Query query = entityManager.createNativeQuery(
                "SELECT p.id_paciente, p.nome, p.cpf, p.ddd, p.numero, " +
                        "  pr.id_prontuario, ultima.data_slot, m.nome, pr.assinado_em " +
                        "FROM (" +
                        "  SELECT DISTINCT ON (c.id_paciente) c.id_paciente, c.id_consulta, a.id_medico, a.data_slot, a.hora_slot " +
                        "  FROM consulta c " +
                        "  JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  JOIN prontuario pron ON pron.id_consulta = c.id_consulta " +
                        "  ORDER BY c.id_paciente, a.data_slot DESC, a.hora_slot DESC" +
                        ") ultima " +
                        "JOIN paciente p ON p.id_paciente = ultima.id_paciente " +
                        "JOIN medico m ON m.id_medico = ultima.id_medico " +
                        "JOIN prontuario pr ON pr.id_consulta = ultima.id_consulta"
        );

        List<ProntuarioListagemItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Integer pacienteId = ((Number) l[0]).intValue();
            String nome = (String) l[1];
            String cpf = (String) l[2];
            String ddd = (String) l[3];
            String numero = (String) l[4];
            Integer prontuarioId = ((Number) l[5]).intValue();
            LocalDate ultimaData = paraLocalDate(l[6]);
            String profissional = (String) l[7];
            boolean finalizado = l[8] != null;

            resultado.add(new ProntuarioListagemItemDto(
                    pacienteId, nome, cpf, formatarTelefone(ddd, numero), prontuarioId,
                    ultimaData.toString(), ultimaData.format(DIA_MES),
                    profissional, finalizado ? "finalizado" : "pendente"
            ));
        }
        resultado.sort((a, b) -> b.ultimaData().compareTo(a.ultimaData()));
        return resultado;
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
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
}
