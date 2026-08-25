package br.com.clinica.service;

import br.com.clinica.dto.ProntuarioAtendimentoDto;
import br.com.clinica.dto.ProntuarioDetalheCompletoDto;
import br.com.clinica.dto.ProntuarioDocumentoDto;
import br.com.clinica.dto.ProntuarioPacienteDetalheDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Leituras de /prontuario/:pacienteId — cabeçalho do paciente, resumo
// clínico rápido (alergias/comorbidades/medicamentos ativos), histórico de
// atendimentos, documentos anexados, e o registro completo de um
// atendimento (usado tanto por "Ver atendimento" quanto pra pré-carregar um
// rascunho em "Continuar atendimento"). Tudo via SQL nativo — as entidades
// JPA equivalentes (Alergia, Comorbidade, ...) têm FK LAZY sem JOIN FETCH e
// quebrariam a serialização com open-in-view: false.
@Service
public class ProntuarioDetalheService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EntityManager entityManager;

    public ProntuarioDetalheService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public ProntuarioPacienteDetalheDto detalharPaciente(Integer idPaciente) {
        Query base = entityManager.createNativeQuery(
                "SELECT nome, cpf, ddd, numero, data_nascimento FROM paciente WHERE id_paciente = :id"
        );
        base.setParameter("id", idPaciente);
        Object[] linha;
        try {
            linha = (Object[]) base.getSingleResult();
        } catch (NoResultException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado");
        }
        String nome = (String) linha[0];
        String cpf = (String) linha[1];
        String ddd = (String) linha[2];
        String numero = (String) linha[3];
        LocalDate nascimento = paraLocalDate(linha[4]);
        int idade = Period.between(nascimento, LocalDate.now()).getYears();

        return new ProntuarioPacienteDetalheDto(
                idPaciente, nome, idade, cpf, formatarTelefone(ddd, numero),
                alergias(idPaciente), comorbidades(idPaciente), medicamentos(idPaciente),
                atendimentos(idPaciente)
        );
    }

    @SuppressWarnings("unchecked")
    private List<ProntuarioPacienteDetalheDto.AlergiaResumoDto> alergias(Integer idPaciente) {
        Query query = entityManager.createNativeQuery(
                "SELECT substancia, tipo::text, gravidade::text FROM alergia " +
                        "WHERE id_paciente = :id ORDER BY registrado_em DESC"
        );
        query.setParameter("id", idPaciente);
        List<ProntuarioPacienteDetalheDto.AlergiaResumoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            resultado.add(new ProntuarioPacienteDetalheDto.AlergiaResumoDto((String) l[0], (String) l[1], (String) l[2]));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<ProntuarioPacienteDetalheDto.ComorbidadeResumoDto> comorbidades(Integer idPaciente) {
        Query query = entityManager.createNativeQuery(
                "SELECT descricao, codigo_cid FROM comorbidade " +
                        "WHERE id_paciente = :id AND ativo = true ORDER BY id_comorbidade DESC"
        );
        query.setParameter("id", idPaciente);
        List<ProntuarioPacienteDetalheDto.ComorbidadeResumoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            resultado.add(new ProntuarioPacienteDetalheDto.ComorbidadeResumoDto((String) l[0], (String) l[1]));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<ProntuarioPacienteDetalheDto.MedicamentoResumoDto> medicamentos(Integer idPaciente) {
        Query query = entityManager.createNativeQuery(
                "SELECT medicamento, dosagem, posologia FROM medicamento_uso_continuo " +
                        "WHERE id_paciente = :id AND ativo = true ORDER BY id_medicamento_uso_continuo DESC"
        );
        query.setParameter("id", idPaciente);
        List<ProntuarioPacienteDetalheDto.MedicamentoResumoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            resultado.add(new ProntuarioPacienteDetalheDto.MedicamentoResumoDto((String) l[0], (String) l[1], (String) l[2]));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<ProntuarioAtendimentoDto> atendimentos(Integer idPaciente) {
        LocalDate hoje = LocalDate.now();
        Query query = entityManager.createNativeQuery(
                "SELECT c.id_consulta, pr.id_prontuario, a.data_slot, a.hora_slot, m.nome, c.tipo::text, " +
                        "  pr.assinado_em, pr.queixa_principal, pr.descricao " +
                        "FROM consulta c " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "LEFT JOIN prontuario pr ON pr.id_consulta = c.id_consulta " +
                        "WHERE c.id_paciente = :id AND a.data_slot <= :hoje AND c.status_consulta <> 'Cancelada' " +
                        "ORDER BY a.data_slot DESC, a.hora_slot DESC"
        );
        query.setParameter("id", idPaciente);
        query.setParameter("hoje", hoje);

        List<ProntuarioAtendimentoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Integer consultaId = ((Number) l[0]).intValue();
            Integer prontuarioId = l[1] == null ? null : ((Number) l[1]).intValue();
            LocalDate data = paraLocalDate(l[2]);
            String hora = paraLocalTime(l[3]).format(HORA);
            String profissional = (String) l[4];
            String tipo = (String) l[5];
            boolean temProntuario = prontuarioId != null;
            boolean finalizado = l[6] != null;
            String queixa = (String) l[7];
            String descricao = (String) l[8];

            String status = !temProntuario ? "sem_registro" : finalizado ? "finalizado" : "pendente";
            String resumo = queixa != null ? queixa : descricao;

            resultado.add(new ProntuarioAtendimentoDto(
                    consultaId, prontuarioId, data.format(DIA_MES_ANO), hora, profissional, tipo, status, resumo
            ));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    public List<ProntuarioDocumentoDto> documentos(Integer idPaciente) {
        Query query = entityManager.createNativeQuery(
                "SELECT id_documento, nome_arquivo, origem::text, enviado_em FROM documento_anexo " +
                        "WHERE id_paciente = :id ORDER BY enviado_em DESC"
        );
        query.setParameter("id", idPaciente);
        List<ProntuarioDocumentoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            resultado.add(new ProntuarioDocumentoDto(
                    ((Number) l[0]).intValue(), (String) l[1], (String) l[2], paraOffsetDateTime(l[3]).toString()
            ));
        }
        return resultado;
    }

    public ProntuarioDetalheCompletoDto detalharProntuario(Integer idProntuario) {
        Query query = entityManager.createNativeQuery(
                "SELECT pr.id_prontuario, pr.id_consulta, p.id_paciente, p.nome, a.id_medico, m.nome, a.data_slot, a.hora_slot, c.tipo::text, " +
                        "  pr.queixa_principal, pr.historia_doenca_atual, pr.descricao, pr.exame_fisico, " +
                        "  pr.hipotese_diagnostica, pr.diagnostico, pr.tipo_diagnostico::text, " +
                        "  pr.prescricao, pr.plano_terapeutico, pr.conduta, pr.assinado_em " +
                        "FROM prontuario pr " +
                        "JOIN consulta c ON c.id_consulta = pr.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "WHERE pr.id_prontuario = :id"
        );
        query.setParameter("id", idProntuario);
        Object[] l;
        try {
            l = (Object[]) query.getSingleResult();
        } catch (NoResultException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado");
        }

        LocalDate data = paraLocalDate(l[6]);
        OffsetDateTime assinadoEm = l[19] == null ? null : paraOffsetDateTime(l[19]);
        return new ProntuarioDetalheCompletoDto(
                ((Number) l[0]).intValue(), ((Number) l[1]).intValue(), ((Number) l[2]).intValue(), (String) l[3],
                ((Number) l[4]).intValue(), (String) l[5],
                data.format(DIA_MES_ANO), paraLocalTime(l[7]).format(HORA), (String) l[8],
                (String) l[9], (String) l[10], (String) l[11], (String) l[12],
                (String) l[13], (String) l[14], (String) l[15],
                (String) l[16], (String) l[17], (String) l[18],
                assinadoEm != null, assinadoEm == null ? null : assinadoEm.toString()
        );
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }

    private java.time.LocalTime paraLocalTime(Object valor) {
        if (valor instanceof java.time.LocalTime localTime) return localTime;
        if (valor instanceof java.sql.Time sqlTime) return sqlTime.toLocalTime();
        throw new IllegalStateException("Tipo de hora inesperado: " + valor.getClass());
    }

    private OffsetDateTime paraOffsetDateTime(Object valor) {
        if (valor instanceof OffsetDateTime offsetDateTime) return offsetDateTime;
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        throw new IllegalStateException("Tipo de timestamp inesperado: " + valor.getClass());
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
