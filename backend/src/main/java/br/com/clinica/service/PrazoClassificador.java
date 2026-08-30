package br.com.clinica.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Spec seção 9 — classificação visual de prazo, compartilhada por Glosa e
// RecursoGlosa: 🟢 >7 dias / 🟡 3-7 dias / 🔴 <3 dias / ⚫ expirado.
final class PrazoClassificador {

    private PrazoClassificador() {
    }

    static Integer diasRestantes(LocalDate prazo) {
        if (prazo == null) return null;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), prazo);
    }

    static String cor(Integer diasRestantes) {
        if (diasRestantes == null) return null;
        if (diasRestantes < 0) return "preto";
        if (diasRestantes < 3) return "vermelho";
        if (diasRestantes <= 7) return "amarelo";
        return "verde";
    }
}
