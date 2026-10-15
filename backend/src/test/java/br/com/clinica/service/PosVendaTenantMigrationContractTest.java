package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PosVendaTenantMigrationContractTest {
    @Test void todosOsRecursosOperacionaisTemTenantERlsForcada() throws Exception {
        String sql=Files.readString(Path.of("..","database","migrations","016_pos_venda_operacao.sql"));
        var tables=List.of("pos_venda_tarefa","pos_venda_jornada","pos_venda_jornada_etapa",
                "pos_venda_jornada_inscricao","pos_venda_campanha","pos_venda_campanha_publico","pos_venda_historico");
        for(String table:tables) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS "+table),table+" deve existir");
            assertTrue(sql.contains("'"+table+"'"),table+" deve participar do bloco de RLS");
        }
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id''"));
        assertTrue(sql.contains("VALUES (16) ON CONFLICT DO NOTHING"));
    }

    @Test void tabelasMutaveisPossuemVersaoParaConcorrenciaOtimista() throws Exception {
        String sql=Files.readString(Path.of("..","database","migrations","016_pos_venda_operacao.sql"));
        assertTrue(sql.contains("pos_venda_operacao_versionar"));
        assertTrue(sql.contains("versao INTEGER NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("trg_%s_versionar"));
    }
}
