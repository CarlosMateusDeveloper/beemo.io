package br.com.clinica.service;

import br.com.clinica.dto.RetornoEnviarMensagemResponse;
import br.com.clinica.model.EnvioRetorno;
import br.com.clinica.model.GrupoRetorno;
import br.com.clinica.model.PacienteRetornoStatus;
import br.com.clinica.repository.EnvioRetornoRepository;
import br.com.clinica.repository.PacienteRetornoStatusRepository;
import br.com.clinica.repository.PacienteRepository;
import br.com.clinica.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// Ações em lote da Aba Pendentes (docs/specs/retorno.md, Aba 1). "Enviar
// mensagem" grava em `mensagem` (Fase 10, compartilhada com /whatsapp) e em
// `envio_retorno` (histórico/conversão da Aba Resultados) — mas não entrega
// de verdade: não existe integração real com WhatsApp em lugar nenhum do
// sistema (chatbot/services/whatsapp_service.py.send_message() é um
// NotImplementedError explícito). Mesma limitação honesta que o painel
// /whatsapp já tem hoje.
@Service
public class RetornoAcaoService {

    // Limite de frequência global (spec Aba 2: "nenhum paciente recebe mais
    // de X mensagens automáticas por período... padrão: 1 a cada 30 dias").
    // Constante em código, não uma tela de configuração — ninguém pediu pra
    // ajustar esse número ainda.
    private static final int LIMITE_FREQUENCIA_DIAS = 30;

    private final EntityManager entityManager;
    private final PacienteRetornoStatusRepository statusRepository;
    private final EnvioRetornoRepository envioRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    public RetornoAcaoService(
            EntityManager entityManager, PacienteRetornoStatusRepository statusRepository,
            EnvioRetornoRepository envioRepository, PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.entityManager = entityManager;
        this.statusRepository = statusRepository;
        this.envioRepository = envioRepository;
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void adiarTrintaDias(List<Integer> idsPaciente) {
        for (Integer id : idsPaciente) {
            PacienteRetornoStatus status = statusFor(id);
            status.setStatus("adiado");
            status.setAdiadoAte(LocalDate.now().plusDays(30));
            status.setAtualizadoEm(OffsetDateTime.now());
            statusRepository.save(status);
        }
    }

    @Transactional
    public void marcarNaoContatar(List<Integer> idsPaciente, String motivo) {
        for (Integer id : idsPaciente) {
            PacienteRetornoStatus status = statusFor(id);
            status.setStatus("nao_contatar");
            status.setMotivoNaoContatar(motivo);
            status.setAtualizadoEm(OffsetDateTime.now());
            statusRepository.save(status);
        }
    }

    @Transactional
    public RetornoEnviarMensagemResponse enviarMensagem(List<Integer> idsPaciente, GrupoRetorno grupo, String texto, Integer idUsuario) {
        OffsetDateTime desde = OffsetDateTime.now().minusDays(LIMITE_FREQUENCIA_DIAS);
        int enviados = 0;
        int pulados = 0;

        for (Integer idPaciente : idsPaciente) {
            if (envioRepository.contarDesde(idPaciente, desde) > 0) {
                pulados++;
                continue;
            }

            Object[] contato = buscarTelefone(idPaciente);
            if (contato == null) {
                pulados++;
                continue;
            }
            String telefone = (String) contato[0];

            Long idMensagem = gravarMensagem(idPaciente, telefone, texto);

            EnvioRetorno envio = new EnvioRetorno();
            envio.setPaciente(pacienteRepository.getReferenceById(idPaciente));
            envio.setGrupo(grupo);
            envio.setTexto(texto);
            envio.setIdMensagem(idMensagem);
            if (idUsuario != null) {
                envio.setUsuarioDisparou(usuarioRepository.getReferenceById(idUsuario));
            }
            envioRepository.save(envio);
            enviados++;
        }

        return new RetornoEnviarMensagemResponse(enviados, pulados);
    }

    private PacienteRetornoStatus statusFor(Integer idPaciente) {
        return statusRepository.findById(idPaciente).orElseGet(() -> {
            PacienteRetornoStatus novo = new PacienteRetornoStatus();
            novo.setPaciente(pacienteRepository.getReferenceById(idPaciente));
            return novo;
        });
    }

    private Object[] buscarTelefone(Integer idPaciente) {
        Query query = entityManager.createNativeQuery(
                "SELECT CONCAT(ddd, numero) FROM paciente WHERE id_paciente = :id"
        );
        query.setParameter("id", idPaciente);
        List<?> resultado = query.getResultList();
        if (resultado.isEmpty()) return null;
        return new Object[]{ resultado.get(0) };
    }

    // Grava em `mensagem` (schema Fase 10) igual o painel /whatsapp grava
    // quando um agente responde — direcao='saida', remetente='agente'.
    // Não passa por nenhum provedor real (ver comentário da classe).
    private Long gravarMensagem(Integer idPaciente, String telefone, String texto) {
        Query insert = entityManager.createNativeQuery(
                "INSERT INTO mensagem (id_paciente, telefone, direcao, tipo, conteudo, remetente) " +
                        "VALUES (:idPaciente, :telefone, CAST('saida' AS direcao_mensagem), " +
                        "CAST('texto' AS tipo_mensagem), :conteudo, CAST('agente' AS remetente_mensagem)) " +
                        "RETURNING id_mensagem"
        );
        insert.setParameter("idPaciente", idPaciente);
        insert.setParameter("telefone", telefone);
        insert.setParameter("conteudo", texto);
        Object id = insert.getSingleResult();
        return ((Number) id).longValue();
    }
}
