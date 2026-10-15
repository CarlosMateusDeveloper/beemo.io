package br.com.clinica.service;

import br.com.clinica.dto.PacienteCobrancaDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PacienteCobrancaService {
    private final JdbcTemplate db;

    public PacienteCobrancaService(JdbcTemplate db) { this.db = db; }

    public Map<String, Object> listar(String busca, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        String termo = busca == null ? "" : busca.strip();
        String filtro = " WHERE (? = '' OR strpos(lower(p.nome), lower(?)) > 0) ";
        long total = db.queryForObject("SELECT count(*) FROM fatura f "
                + "JOIN consulta c ON c.id_consulta=f.id_consulta "
                + "JOIN paciente p ON p.id_paciente=c.id_paciente" + filtro,
                Long.class, termo, termo);
        List<PacienteCobrancaDto> itens = db.query("""
                SELECT p.id_paciente,p.nome,c.id_consulta,a.data_slot,a.hora_slot,c.tipo::text,
                       cv.nome,f.valor,f.status::text
                FROM fatura f
                JOIN consulta c ON c.id_consulta=f.id_consulta
                JOIN agenda a ON a.id_agenda=c.id_agenda
                JOIN paciente p ON p.id_paciente=c.id_paciente
                LEFT JOIN convenio cv ON cv.id_convenio=p.id_convenio
                """ + filtro + " ORDER BY a.data_slot DESC,a.hora_slot DESC,f.id_fatura DESC LIMIT ? OFFSET ?",
                (rs, row) -> new PacienteCobrancaDto(rs.getInt(1), rs.getString(2), rs.getInt(3),
                        rs.getObject(4, java.time.LocalDate.class), rs.getObject(5, java.time.LocalTime.class),
                        rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getString(9)),
                termo, termo, tamanho, pagina * tamanho);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("itens", itens);
        resposta.put("pagina", pagina);
        resposta.put("tamanho", tamanho);
        resposta.put("total", total);
        resposta.put("totalPaginas", (total + tamanho - 1) / tamanho);
        return resposta;
    }
}
