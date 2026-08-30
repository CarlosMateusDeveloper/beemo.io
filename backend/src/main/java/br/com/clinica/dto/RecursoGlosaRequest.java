package br.com.clinica.dto;

import java.time.LocalDate;

// Usado tanto pra criar (POST) quanto editar (PUT) um recurso enquanto ele
// ainda não foi enviado (spec seção 6). evidenciasConferidas é opcional —
// null no create (fica FALSE, default do banco); no update, só muda se vier
// preenchido.
public record RecursoGlosaRequest(
        String justificativa, LocalDate prazoLimite, Integer idUsuarioResponsavel, Boolean evidenciasConferidas
) {
}
