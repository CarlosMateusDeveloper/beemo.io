package br.com.clinica.service;

import br.com.clinica.dto.DocumentoClinicoRequest;
import br.com.clinica.dto.ProntuarioSalvarRequest;
import br.com.clinica.model.Consulta;
import br.com.clinica.model.DocumentoClinico;
import br.com.clinica.model.Prontuario;
import br.com.clinica.model.TipoDocumentoClinico;
import br.com.clinica.repository.DocumentoClinicoRepository;
import br.com.clinica.repository.ProntuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClinicalDataEncryptionServiceTest {
    private ClinicalTextCipher cipher;

    @BeforeEach void setup() {
        cipher = new ClinicalTextCipher("v1", Base64.getEncoder().encodeToString(new byte[32]), "", false);
        TenantContext.set(new TenantService.Access(7, "Clínica de teste", "administrador"));
    }

    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void chartWriteNeverPersistsPlaintextAndKeepsDraftRoundTrip() {
        var repository = mock(ProntuarioRepository.class);
        var entityManager = mock(EntityManager.class);
        var auditoria = mock(AuditoriaEngineService.class);
        Consulta consulta = mock(Consulta.class);
        when(consulta.getId()).thenReturn(5);
        when(entityManager.getReference(Consulta.class, 5)).thenReturn(consulta);
        AtomicBoolean firstSaveWasBlank = new AtomicBoolean();
        when(repository.findById(12)).thenAnswer(call -> Optional.of(saved));
        var service = new ProntuarioEscritaService(repository, entityManager, auditoria, cipher);
        var request = new ProntuarioSalvarRequest(5, null, "dor", null, "evolução", null,
                null, "hipótese", null, null, null, null, null, false);
        // Capture the managed entity through the repository answer rather than logging values.
        saved = null;
        when(repository.save(any(Prontuario.class))).thenAnswer(call -> {
            Prontuario p = call.getArgument(0);
            if (p.getId() == null) {
                firstSaveWasBlank.set(p.getDescricao().isEmpty() && p.getDiagnostico().isEmpty()
                        && p.getPrescricao().isEmpty() && p.getQueixaPrincipal() == null);
                p.setId(12);
            }
            saved = p;
            return p;
        });
        var response = service.criar(request);
        assertEquals(12, response.id());
        assertTrue(firstSaveWasBlank.get());
        assertNotEquals("evolução", saved.getDescricao());
        assertTrue(saved.getDescricao().startsWith("enc:v1:"));
        assertEquals("evolução", cipher.decrypt(saved.getDescricao(), 7, "prontuario", "descricao", 12));
        assertEquals("dor", cipher.decrypt(saved.getQueixaPrincipal(), 7, "prontuario", "queixa_principal", 12));
    }

    private Prontuario saved;

    @Test void documentInsertUsesOnlyPlaceholderAndEncryptedUpdate() {
        var repository = mock(DocumentoClinicoRepository.class);
        var entityManager = mock(EntityManager.class);
        Query insert = mock(Query.class);
        Query update = mock(Query.class);
        when(entityManager.createNativeQuery(startsWith("INSERT INTO documento_clinico"))).thenReturn(insert);
        when(entityManager.createNativeQuery(startsWith("UPDATE documento_clinico"))).thenReturn(update);
        when(insert.setParameter(anyString(), any())).thenReturn(insert);
        when(update.setParameter(anyString(), any())).thenReturn(update);
        when(insert.getSingleResult()).thenReturn(21);
        DocumentoClinico document = new DocumentoClinico();
        document.setId(21);
        document.setTipo(TipoDocumentoClinico.ATESTADO);
        Prontuario chart = new Prontuario();
        chart.setId(12);
        document.setProntuario(chart);
        when(repository.findById(21)).thenAnswer(call -> Optional.of(document));
        when(update.setParameter(eq("texto"), anyString())).thenAnswer(call -> {
            document.setTexto(call.getArgument(1));
            return update;
        });
        var service = new DocumentoClinicoEscritaService(repository, entityManager, cipher);
        var response = service.criar(new DocumentoClinicoRequest(12, "ATESTADO", null, null, "texto clínico"));
        verify(insert).setParameter("texto", "");
        verify(update).setParameter(eq("texto"), startsWith("enc:v1:"));
        assertEquals("texto clínico", response.texto());
        assertNotEquals("texto clínico", document.getTexto());
    }
}
