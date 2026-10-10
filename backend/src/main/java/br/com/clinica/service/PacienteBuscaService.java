package br.com.clinica.service;

import br.com.clinica.dto.PacienteResumoDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Busca cadastral paginada, com escopo obrigatório para usuários médicos. */
@Service
public class PacienteBuscaService {
    private final EntityManager entityManager;
    private final AccessControlService access;
    public PacienteBuscaService(EntityManager entityManager, AccessControlService access) {
        this.entityManager = entityManager; this.access = access;
    }

    @SuppressWarnings("unchecked")
    public Page<PacienteResumoDto> buscar(String busca, Pageable pageable) {
        String term = busca == null ? "" : busca.trim();
        String digits = term.replaceAll("\\D", "");
        Integer doctor = access.patientDoctorId();
        String from = " FROM paciente p LEFT JOIN convenio cv ON cv.id_convenio=p.id_convenio "
                + "WHERE (CAST(:medicoId AS INTEGER) IS NULL OR EXISTS(SELECT 1 FROM consulta c "
                + "JOIN agenda a ON a.id_agenda=c.id_agenda WHERE c.id_paciente=p.id_paciente AND a.id_medico=:medicoId)) "
                + "AND (:busca='' OR lower(p.nome) LIKE lower(concat('%',:busca,'%')) "
                + "OR (:digitos<>'' AND p.cpf LIKE concat('%',:digitos,'%')) "
                + "OR (:digitos<>'' AND concat(p.ddd,p.numero) LIKE concat('%',:digitos,'%')))";
        Query data = params(entityManager.createNativeQuery("SELECT p.id_paciente,p.nome,p.cpf,p.ddd,p.numero,p.data_nascimento,cv.nome"
                + from + " ORDER BY p.nome,p.id_paciente LIMIT :limite OFFSET :inicio"), doctor, term, digits)
                .setParameter("limite", pageable.getPageSize()).setParameter("inicio", pageable.getOffset());
        Query count = params(entityManager.createNativeQuery("SELECT count(*)" + from), doctor, term, digits);
        List<PacienteResumoDto> result = new ArrayList<>();
        for (Object[] row : (List<Object[]>) data.getResultList()) {
            result.add(new PacienteResumoDto(((Number) row[0]).intValue(), (String) row[1], mask((String) row[2]),
                    phone((String) row[3], (String) row[4]), localDate(row[5]), (String) row[6]));
        }
        return new PageImpl<>(result, pageable, ((Number) count.getSingleResult()).longValue());
    }

    private Query params(Query query, Integer doctor, String term, String digits) {
        return query.setParameter("medicoId", doctor).setParameter("busca", term).setParameter("digitos", digits);
    }
    private String mask(String cpf) { return cpf == null || cpf.length()!=11 ? null : cpf.substring(0,3)+".***.***-"+cpf.substring(9); }
    private String phone(String ddd,String number) {
        if(number==null) return null;
        if(number.length()==9) return "("+ddd+") "+number.substring(0,5)+"-"+number.substring(5);
        if(number.length()==8) return "("+ddd+") "+number.substring(0,4)+"-"+number.substring(4);
        return "("+ddd+") "+number;
    }
    private LocalDate localDate(Object value) {
        if(value instanceof LocalDate date) return date;
        return ((java.sql.Date)value).toLocalDate();
    }
}
