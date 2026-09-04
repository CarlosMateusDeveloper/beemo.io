package br.com.clinica.model;

// Espelha o enum nativo grupo_retorno do Postgres (Fase 16, docs/specs/retorno.md).
public enum GrupoRetorno {
    tratamento_interrompido, retorno_medico, exame_pendente, ritmo_quebrado
}
