package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PosVendaMetricasMigrationContractTest {
    @Test void criaIndicesIdempotentesParaCoorteEventosEVinculos() throws Exception {
        String sql=Files.readString(Path.of("..","database","migrations","017_pos_venda_metricas_indices.sql"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_pos_venda_caso_coorte"));
        assertTrue(sql.contains("id_clinica,data_falta,status,id"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_pos_venda_evento_metricas"));
        assertTrue(sql.contains("id_clinica,id_caso,tipo,criado_em"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_pos_venda_consulta_caso"));
        assertTrue(sql.contains("VALUES (17) ON CONFLICT DO NOTHING"));
    }
}
