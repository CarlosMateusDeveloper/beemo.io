package main

import (
	"database/sql"
	"errors"
)

var errNotFound = errors.New("agenda não encontrada")

func listAgendas(db *sql.DB, idMedico int) ([]Agenda, error) {
	query := `SELECT id_agenda, id_medico, situacao, data_slot::text, hora_slot::text FROM agenda`
	args := []any{}
	if idMedico > 0 {
		query += ` WHERE id_medico = $1`
		args = append(args, idMedico)
	}
	query += ` ORDER BY data_slot, hora_slot`

	rows, err := db.Query(query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	agendas := []Agenda{}
	for rows.Next() {
		var a Agenda
		if err := rows.Scan(&a.ID, &a.IDMedico, &a.Situacao, &a.DataSlot, &a.HoraSlot); err != nil {
			return nil, err
		}
		agendas = append(agendas, a)
	}
	return agendas, rows.Err()
}

func getAgenda(db *sql.DB, id int) (*Agenda, error) {
	var a Agenda
	err := db.QueryRow(
		`SELECT id_agenda, id_medico, situacao, data_slot::text, hora_slot::text FROM agenda WHERE id_agenda = $1`,
		id,
	).Scan(&a.ID, &a.IDMedico, &a.Situacao, &a.DataSlot, &a.HoraSlot)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, errNotFound
	}
	if err != nil {
		return nil, err
	}
	return &a, nil
}

func createAgenda(db *sql.DB, a *Agenda) error {
	return db.QueryRow(
		`INSERT INTO agenda (id_medico, situacao, data_slot, hora_slot)
		 VALUES ($1, $2, $3::date, $4::time)
		 RETURNING id_agenda`,
		a.IDMedico, a.Situacao, a.DataSlot, a.HoraSlot,
	).Scan(&a.ID)
}

func updateAgenda(db *sql.DB, a *Agenda) error {
	res, err := db.Exec(
		`UPDATE agenda SET id_medico = $1, situacao = $2, data_slot = $3::date, hora_slot = $4::time
		 WHERE id_agenda = $5`,
		a.IDMedico, a.Situacao, a.DataSlot, a.HoraSlot, a.ID,
	)
	if err != nil {
		return err
	}
	rows, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return errNotFound
	}
	return nil
}

func deleteAgenda(db *sql.DB, id int) error {
	res, err := db.Exec(`DELETE FROM agenda WHERE id_agenda = $1`, id)
	if err != nil {
		return err
	}
	rows, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return errNotFound
	}
	return nil
}
