package br.com.clinica.service;

import br.com.clinica.dto.GlosaDetalheDto;
import br.com.clinica.model.Glosa;
import br.com.clinica.model.GlosaHistorico;
import br.com.clinica.repository.GlosaHistoricoRepository;
import br.com.clinica.repository.GlosaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;

// Painel de análise de uma glosa (spec seção 4): atendimento, documentos
// disponíveis (checklist computado a partir do que já existe no ClinicOS,
// não cadastrado à mão), histórico e o recurso mais recente.
@Service
public class GlosaDetalheService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES_ANO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GlosaRepository repository;
    private final GlosaHistoricoRepository historicoRepository;
    private final RecursoGlosaLeituraService recursoLeituraService;
    private final EntityManager entityManager;

    public GlosaDetalheService(
            GlosaRepository repository, GlosaHistoricoRepository historicoRepository,
            RecursoGlosaLeituraService recursoLeituraService, EntityManager entityManager
    ) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.recursoLeituraService = recursoLeituraService;
        this.entityManager = entityManager;
    }

    public GlosaDetalheDto detalhar(Integer id) {
        Glosa glosa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Glosa não encontrada"));

        Object[] contexto = buscarContexto(glosa);
        String convenioNome = (String) contexto[0];
        Integer idConsulta = ((Number) contexto[1]).intValue();
        String pacienteNome = (String) contexto[2];
        String procedimento = (String) contexto[3];
        String profissionalNome = (String) contexto[4];
        String dataAtendimentoTxt = contexto[5] == null ? "—" : contexto[5].toString();

        GlosaDetalheDto.AtendimentoResumoDto atendimento =
                new GlosaDetalheDto.AtendimentoResumoDto(pacienteNome, procedimento, profissionalNome, dataAtendimentoTxt);
        GlosaDetalheDto.DocumentosDisponiveisDto documentosDisponiveis = documentosDisponiveis(idConsulta);
        List<GlosaDetalheDto.HistoricoItemDto> historico = historico(id);

        Integer diasRestantes = PrazoClassificador.diasRestantes(glosa.getPrazoRecurso());

        return new GlosaDetalheDto(
                glosa.getId(), glosa.getValor(), glosa.getValorFaturado(), glosa.getMotivo(), glosa.getCodigoMotivo(),
                glosa.getDataGlosa().format(DIA_MES_ANO),
                glosa.getPrazoRecurso() == null ? "—" : glosa.getPrazoRecurso().format(DIA_MES_ANO),
                diasRestantes, PrazoClassificador.cor(diasRestantes),
                glosa.getStatus(), glosa.getOrigem(), glosa.getRecorribilidade(), glosa.getCategoriaMotivo(),
                convenioNome, nomeUsuario(glosa.getIdUsuarioResponsavel()),
                atendimento, documentosDisponiveis, historico,
                recursoLeituraService.atual(id).orElse(null)
        );
    }

    private Object[] buscarContexto(Glosa glosa) {
        Query query = entityManager.createNativeQuery(
                "SELECT (SELECT nome FROM convenio WHERE id_convenio = :idConvenio), " +
                        "  c.id_consulta, p.nome, c.tipo::text, m.nome, " +
                        "  to_char(a.data_slot, 'DD/MM/YYYY') || ' ' || to_char(a.hora_slot, 'HH24:MI') " +
                        "FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "WHERE f.id_fatura = :idFatura"
        );
        query.setParameter("idConvenio", glosa.getIdConvenio());
        query.setParameter("idFatura", glosa.getIdFatura());
        try {
            return (Object[]) query.getSingleResult();
        } catch (NoResultException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fatura/consulta/convênio da glosa não encontrados");
        }
    }

    // Seção 4 da spec: o que já existe no sistema pra esse atendimento, não
    // um upload registrado à parte. "Solicitação médica" mapeia pra
    // solicitacao_exame (mais próximo disso no schema atual).
    @SuppressWarnings("unchecked")
    private GlosaDetalheDto.DocumentosDisponiveisDto documentosDisponiveis(Integer idConsulta) {
        Object[] linha = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                        "  EXISTS(SELECT 1 FROM prontuario pr WHERE pr.id_consulta = :idConsulta), " +
                        "  EXISTS(SELECT 1 FROM autorizacao_convenio ac WHERE ac.id_consulta = :idConsulta AND ac.numero_guia IS NOT NULL), " +
                        "  EXISTS(SELECT 1 FROM solicitacao_exame se JOIN prontuario pr ON pr.id_prontuario = se.id_prontuario " +
                        "           WHERE pr.id_consulta = :idConsulta), " +
                        "  EXISTS(SELECT 1 FROM autorizacao_convenio ac WHERE ac.id_consulta = :idConsulta AND ac.status = 'autorizado')"
        ).setParameter("idConsulta", idConsulta).getSingleResult();

        return new GlosaDetalheDto.DocumentosDisponiveisDto(
                Boolean.TRUE.equals(linha[0]), Boolean.TRUE.equals(linha[1]),
                Boolean.TRUE.equals(linha[2]), Boolean.TRUE.equals(linha[3])
        );
    }

    private List<GlosaDetalheDto.HistoricoItemDto> historico(Integer idGlosa) {
        return historicoRepository.findByIdGlosaOrderByCriadoEmAsc(idGlosa).stream()
                .map(h -> new GlosaDetalheDto.HistoricoItemDto(
                        h.getCriadoEm() == null ? "—" : h.getCriadoEm().format(DIA_MES_ANO_HORA),
                        h.getEvento(),
                        nomeUsuario(h.getIdUsuario())
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String nomeUsuario(Integer idUsuario) {
        if (idUsuario == null) return null;
        List<Object> resultado = entityManager.createNativeQuery("SELECT nome FROM usuario WHERE id = :id")
                .setParameter("id", idUsuario)
                .getResultList();
        return resultado.isEmpty() ? null : (String) resultado.get(0);
    }
}
