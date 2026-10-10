package br.com.clinica.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClinicalScopeService {
    private final JdbcTemplate db;
    private final AccessControlService access;
    public ClinicalScopeService(JdbcTemplate db, AccessControlService access) { this.db = db; this.access = access; }

    public void patient(Integer patientId) {
        Integer doctor = access.clinicalDoctorId();
        if (doctor == null) return;
        require("SELECT EXISTS(SELECT 1 FROM consulta c JOIN agenda a ON a.id_agenda=c.id_agenda "
                + "WHERE c.id_paciente=? AND a.id_medico=?)", patientId, doctor);
    }

    public void consultation(Integer consultationId) {
        Integer doctor = access.clinicalDoctorId();
        if (doctor == null) return;
        require("SELECT EXISTS(SELECT 1 FROM consulta c JOIN agenda a ON a.id_agenda=c.id_agenda "
                + "WHERE c.id_consulta=? AND a.id_medico=?)", consultationId, doctor);
    }

    public void chart(Integer chartId) {
        Integer doctor = access.clinicalDoctorId();
        if (doctor == null) return;
        require("SELECT EXISTS(SELECT 1 FROM prontuario p JOIN consulta c ON c.id_consulta=p.id_consulta "
                + "JOIN agenda a ON a.id_agenda=c.id_agenda WHERE p.id_prontuario=? AND a.id_medico=?)", chartId, doctor);
    }

    public void legacyResource(String resource, Integer id) {
        if (id == null) throw AccessControlService.forbidden();
        switch (resource) {
            case "alergias" -> patient(related("SELECT id_paciente FROM alergia WHERE id_alergia=?", id));
            case "cirurgias-previas" -> patient(related("SELECT id_paciente FROM cirurgia_previa WHERE id_cirurgia_previa=?", id));
            case "comorbidades" -> patient(related("SELECT id_paciente FROM comorbidade WHERE id_comorbidade=?", id));
            case "medicamentos-uso-continuo" -> patient(related("SELECT id_paciente FROM medicamento_uso_continuo WHERE id_medicamento_uso_continuo=?", id));
            case "exames" -> patient(related("SELECT id_paciente FROM exame WHERE id_exame=?", id));
            case "documentos-clinicos" -> chart(related("SELECT id_prontuario FROM documento_clinico WHERE id_documento_clinico=?", id));
            case "encaminhamentos" -> chart(related("SELECT id_prontuario FROM encaminhamento WHERE id_encaminhamento=?", id));
            case "itens-prescricao" -> chart(related("SELECT id_prontuario FROM item_prescricao WHERE id_item_prescricao=?", id));
            case "solicitacoes-exame" -> chart(related("SELECT id_prontuario FROM solicitacao_exame WHERE id_solicitacao_exame=?", id));
            case "sinais-vitais" -> consultation(related("SELECT id_consulta FROM sinal_vital WHERE id_sinal_vital=?", id));
            default -> throw AccessControlService.forbidden();
        }
    }

    private Integer related(String sql, Integer id) {
        var values = db.queryForList(sql, Integer.class, id);
        if (values.isEmpty()) throw AccessControlService.forbidden();
        return values.getFirst();
    }

    private void require(String sql, Object id, Integer doctor) {
        if (!Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, id, doctor)))
            throw AccessControlService.forbidden();
    }
}
