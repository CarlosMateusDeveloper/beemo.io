package main

import (
	"net/http"
)

// Paciente é uma leitura somente-consulta pra alimentar o seletor de
// paciente no formulário de nova consulta. Cadastro de paciente é feito em
// outro lugar do sistema — aqui só leitura, direto da tabela existente.
type Paciente struct {
	ID   int    `json:"id"`
	Nome string `json:"nome"`
}

func queryPacientes(db DBTX, idMedico int) ([]Paciente, error) {
	rows, err := db.Query(`SELECT p.id_paciente, p.nome FROM paciente p
		WHERE ($1 = 0 OR EXISTS(SELECT 1 FROM consulta c JOIN agenda a ON a.id_agenda=c.id_agenda
			WHERE c.id_paciente=p.id_paciente AND a.id_medico=$1)) ORDER BY p.nome`, idMedico)
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
	pacientes, err := queryPacientes(requestDB(r), doctorID(r))
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, pacientes)
}
