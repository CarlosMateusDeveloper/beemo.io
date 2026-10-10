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
	if own := doctorID(r); own > 0 {
		if idMedico > 0 && idMedico != own {
			writeError(w, http.StatusForbidden, "Acesso restrito à própria agenda")
			return
		}
		idMedico = own
	}

	consultas, err := queryConsultas(requestDB(r), idMedico)
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

	c, err := queryConsulta(requestDB(r), id)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if own := doctorID(r); own > 0 && c.IDMedico != own {
		writeError(w, http.StatusForbidden, "Acesso restrito à própria agenda")
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
	if own := doctorID(r); own > 0 && in.IDMedico != own {
		writeError(w, http.StatusForbidden, "Acesso restrito à própria agenda")
		return
	}

	c, err := insertConsulta(requestDB(r), in)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	deferBroadcast(r, consultaEvent{Entity: "consulta", Type: "created", Consulta: *c})
	writeJSON(w, http.StatusCreated, c)
}

func (h *agendaHandler) updateConsulta(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}
	if own := doctorID(r); own > 0 {
		current, findErr := queryConsulta(requestDB(r), id)
		if findErr != nil || current.IDMedico != own {
			writeError(w, http.StatusForbidden, "Acesso restrito à própria agenda")
			return
		}
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

	c, err := applyConsultaUpdate(requestDB(r), id, in)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	deferBroadcast(r, consultaEvent{Entity: "consulta", Type: "updated", Consulta: *c})
	writeJSON(w, http.StatusOK, c)
}
