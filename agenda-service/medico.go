package main

import (
	"database/sql"
	"net/http"
)

// Medico é uma leitura somente-consulta pra alimentar o seletor de médico e
// o filtro de especialidade no frontend. Cadastro de médico não é
// responsabilidade deste serviço — só leitura, direto da tabela existente.
type Medico struct {
	ID            int    `json:"id"`
	Nome          string `json:"nome"`
	Especialidade string `json:"especialidade"`
}

func queryMedicos(db *sql.DB) ([]Medico, error) {
	rows, err := db.Query(`
		SELECT m.id_medico, m.nome, e.nome
		FROM medico m
		JOIN especialidade e ON e.id_especialidade = m.id_especialidade
		WHERE m.ativo = true
		ORDER BY m.nome
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	medicos := []Medico{}
	for rows.Next() {
		var m Medico
		if err := rows.Scan(&m.ID, &m.Nome, &m.Especialidade); err != nil {
			return nil, err
		}
		medicos = append(medicos, m)
	}
	return medicos, rows.Err()
}

func (h *agendaHandler) listMedicos(w http.ResponseWriter, r *http.Request) {
	medicos, err := queryMedicos(h.db)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, medicos)
}
