package main

import (
	"database/sql"
	"net/http"
)

// Paciente é uma leitura somente-consulta pra alimentar o seletor de
// paciente no formulário de nova consulta. Cadastro de paciente é feito em
// outro lugar do sistema — aqui só leitura, direto da tabela existente.
type Paciente struct {
	ID   int    `json:"id"`
	Nome string `json:"nome"`
}

func queryPacientes(db *sql.DB) ([]Paciente, error) {
	rows, err := db.Query(`SELECT id_paciente, nome FROM paciente ORDER BY nome`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	pacientes := []Paciente{}
	for rows.Next() {
		var p Paciente
		if err := rows.Scan(&p.ID, &p.Nome); err != nil {
			return nil, err
		}
		pacientes = append(pacientes, p)
	}
	return pacientes, rows.Err()
}

func (h *agendaHandler) listPacientes(w http.ResponseWriter, r *http.Request) {
	pacientes, err := queryPacientes(h.db)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, pacientes)
}
