package main

import (
	"database/sql"
	"errors"
)

const consultaSelect = `
	SELECT c.id_consulta, c.id_paciente, p.nome, c.id_agenda, a.id_medico,
	       a.data_slot::text, a.hora_slot::text, c.status_consulta, c.tipo, c.duracao_minutos
	FROM consulta c
	JOIN agenda a ON a.id_agenda = c.id_agenda
	JOIN paciente p ON p.id_paciente = c.id_paciente
`

func scanConsulta(row interface{ Scan(...any) error }) (*Consulta, error) {
	var c Consulta
	err := row.Scan(&c.ID, &c.IDPaciente, &c.PacienteNome, &c.IDAgenda, &c.IDMedico,
		&c.DataSlot, &c.HoraSlot, &c.Status, &c.Tipo, &c.DuracaoMinutos)
	if err != nil {
		return nil, err
	}
	return &c, nil
}

func queryConsultas(db *sql.DB, idMedico int) ([]Consulta, error) {
	query := consultaSelect
	args := []any{}
	if idMedico > 0 {
		query += ` WHERE a.id_medico = $1`
		args = append(args, idMedico)
	}
	query += ` ORDER BY a.data_slot, a.hora_slot`

	rows, err := db.Query(query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	consultas := []Consulta{}
	for rows.Next() {
		c, err := scanConsulta(rows)
		if err != nil {
			return nil, err
		}
		consultas = append(consultas, *c)
	}
	return consultas, rows.Err()
}

func queryConsulta(db *sql.DB, id int) (*Consulta, error) {
	c, err := scanConsulta(db.QueryRow(consultaSelect+` WHERE c.id_consulta = $1`, id))
	if errors.Is(err, sql.ErrNoRows) {
		return nil, errNotFound
	}
	if err != nil {
		return nil, err
	}
	return c, nil
}

// findOrCreateAgendaSlot devolve o id_agenda para (idMedico, dataSlot,
// horaSlot), criando o slot como "Livre" se ainda não existir. A UNIQUE
// (id_medico, data_slot, hora_slot) trata corrida entre duas criações
// concorrentes para o mesmo horário.
func findOrCreateAgendaSlot(tx *sql.Tx, idMedico int, dataSlot, horaSlot string) (int, error) {
	var idAgenda int
	err := tx.QueryRow(
		`SELECT id_agenda FROM agenda WHERE id_medico = $1 AND data_slot = $2::date AND hora_slot = $3::time`,
		idMedico, dataSlot, horaSlot,
	).Scan(&idAgenda)
	if errors.Is(err, sql.ErrNoRows) {
		err = tx.QueryRow(
			`INSERT INTO agenda (id_medico, situacao, data_slot, hora_slot)
			 VALUES ($1, 'Livre', $2::date, $3::time)
			 RETURNING id_agenda`,
			idMedico, dataSlot, horaSlot,
		).Scan(&idAgenda)
	}
	if err != nil {
		return 0, err
	}
	return idAgenda, nil
}

// insertConsulta cria o slot de agenda (se preciso) e a consulta numa só
// transação, e marca o slot como "Ocupado".
func insertConsulta(db *sql.DB, in createConsultaInput) (*Consulta, error) {
	tx, err := db.Begin()
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	idAgenda, err := findOrCreateAgendaSlot(tx, in.IDMedico, in.DataSlot, in.HoraSlot)
	if err != nil {
		return nil, err
	}

	var idConsulta int
	err = tx.QueryRow(
		`INSERT INTO consulta (id_paciente, id_agenda, status_consulta, tipo, duracao_minutos)
		 VALUES ($1, $2, $3, $4, $5)
		 RETURNING id_consulta`,
		in.IDPaciente, idAgenda, in.Status, in.Tipo, in.DuracaoMinutos,
	).Scan(&idConsulta)
	if err != nil {
		return nil, err
	}

	if _, err := tx.Exec(`UPDATE agenda SET situacao = 'Ocupado' WHERE id_agenda = $1`, idAgenda); err != nil {
		return nil, err
	}

	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return queryConsulta(db, idConsulta)
}

// applyConsultaUpdate aplica só os campos preenchidos em updateConsultaInput.
// Quando dataSlot+horaSlot vêm preenchidos, move a consulta pra outro slot de
// agenda (libera o antigo, ocupa o novo) — é o que o drag-and-drop do
// calendário usa para reagendar.
func applyConsultaUpdate(db *sql.DB, id int, in updateConsultaInput) (*Consulta, error) {
	tx, err := db.Begin()
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	var idAgendaAtual, idMedicoAtual int
	err = tx.QueryRow(
		`SELECT c.id_agenda, a.id_medico FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda WHERE c.id_consulta = $1`,
		id,
	).Scan(&idAgendaAtual, &idMedicoAtual)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, errNotFound
	}
	if err != nil {
		return nil, err
	}

	if in.DataSlot != nil && in.HoraSlot != nil {
		novoIDAgenda, err := findOrCreateAgendaSlot(tx, idMedicoAtual, *in.DataSlot, *in.HoraSlot)
		if err != nil {
			return nil, err
		}
		if novoIDAgenda != idAgendaAtual {
			if _, err := tx.Exec(`UPDATE consulta SET id_agenda = $1 WHERE id_consulta = $2`, novoIDAgenda, id); err != nil {
				return nil, err
			}
			if _, err := tx.Exec(`UPDATE agenda SET situacao = 'Ocupado' WHERE id_agenda = $1`, novoIDAgenda); err != nil {
				return nil, err
			}
			if _, err := tx.Exec(`UPDATE agenda SET situacao = 'Livre' WHERE id_agenda = $1`, idAgendaAtual); err != nil {
				return nil, err
			}
		}
	}

	if in.IDPaciente != nil {
		if _, err := tx.Exec(`UPDATE consulta SET id_paciente = $1 WHERE id_consulta = $2`, *in.IDPaciente, id); err != nil {
			return nil, err
		}
	}
	if in.Status != nil {
		if _, err := tx.Exec(`UPDATE consulta SET status_consulta = $1 WHERE id_consulta = $2`, *in.Status, id); err != nil {
			return nil, err
		}
	}
	if in.Tipo != nil {
		if _, err := tx.Exec(`UPDATE consulta SET tipo = $1 WHERE id_consulta = $2`, *in.Tipo, id); err != nil {
			return nil, err
		}
	}
	if in.DuracaoMinutos != nil {
		if _, err := tx.Exec(`UPDATE consulta SET duracao_minutos = $1 WHERE id_consulta = $2`, *in.DuracaoMinutos, id); err != nil {
			return nil, err
		}
	}

	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return queryConsulta(db, id)
}
