package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
)

// consultaEvent é o que trafega no WebSocket sempre que uma consulta muda.
// "entity" distingue de agendaEvent, já que os dois trafegam no mesmo /ws/agenda.
type consultaEvent struct {
	Entity   string   `json:"entity"` // "consulta"
	Type     string   `json:"type"`   // created | updated
	Consulta Consulta `json:"consulta"`
}

func (h *agendaHandler) listConsultas(w http.ResponseWriter, r *http.Request) {
	idMedico := 0
	if v := r.URL.Query().Get("idMedico"); v != "" {
		parsed, err := strconv.Atoi(v)
		if err != nil {
			writeError(w, http.StatusBadRequest, "idMedico inválido")
			return
		}
		idMedico = parsed
	}

	consultas, err := queryConsultas(h.db, idMedico)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, consultas)
}

func (h *agendaHandler) getConsulta(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}

	c, err := queryConsulta(h.db, id)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, c)
}

func (h *agendaHandler) createConsulta(w http.ResponseWriter, r *http.Request) {
	var in createConsultaInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		writeError(w, http.StatusBadRequest, "corpo inválido")
		return
	}
	if in.Tipo == "" {
		in.Tipo = TipoConsultaPadrao
	}
	if in.Status == "" {
		in.Status = StatusAgendada
	}
	if in.DuracaoMinutos == 0 {
		in.DuracaoMinutos = 30
	}
	if err := in.validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	c, err := insertConsulta(h.db, in)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	h.hub.broadcast(consultaEvent{Entity: "consulta", Type: "created", Consulta: *c})
	writeJSON(w, http.StatusCreated, c)
}

func (h *agendaHandler) updateConsulta(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}

	var in updateConsultaInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		writeError(w, http.StatusBadRequest, "corpo inválido")
		return
	}
	if err := in.validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	c, err := applyConsultaUpdate(h.db, id, in)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	h.hub.broadcast(consultaEvent{Entity: "consulta", Type: "updated", Consulta: *c})
	writeJSON(w, http.StatusOK, c)
}
