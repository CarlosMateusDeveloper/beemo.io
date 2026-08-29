package br.com.clinica.service;

import br.com.clinica.dto.PacienteBuscaRow;
import br.com.clinica.dto.PacienteResumoDto;
import br.com.clinica.repository.PacienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

// GET /api/pacientes (issue #11): busca paginada por nome/CPF/telefone, cpf
// mascarado na resposta ("nunca expor CPF completo fora do necessário" — o
// cadastro completo continua em GET /api/pacientes/{id}, ali é necessário).
@Service
public class PacienteBuscaService {

    private final PacienteRepository repository;

    public PacienteBuscaService(PacienteRepository repository) {
        this.repository = repository;
    }

    public Page<PacienteResumoDto> buscar(String busca, Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        String digitos = termo.replaceAll("\\D", "");
        return repository.buscar(termo, digitos, pageable).map(this::paraResumo);
    }

    private PacienteResumoDto paraResumo(PacienteBuscaRow linha) {
        return new PacienteResumoDto(
                linha.id(), linha.nome(), mascararCpf(linha.cpf()), formatarTelefone(linha.ddd(), linha.numero()),
                linha.dataNascimento(), linha.convenio()
        );
    }

    private String mascararCpf(String cpf) {
        return cpf.substring(0, 3) + ".***.***-" + cpf.substring(9);
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
