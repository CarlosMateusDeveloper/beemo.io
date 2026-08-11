package main

import (
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
)

type agendaHandler struct {
	db  *sql.DB
	hub *hub
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"erro": message})
}

func (h *agendaHandler) list(w http.ResponseWriter, r *http.Request) {
	idMedico := 0
	if v := r.URL.Query().Get("idMedico"); v != "" {
		parsed, err := strconv.Atoi(v)
		if err != nil {
			writeError(w, http.StatusBadRequest, "idMedico inválido")
			return
		}
		idMedico = parsed
	}

	agendas, err := listAgendas(h.db, idMedico)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, agendas)
}

func (h *agendaHandler) get(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}

	agenda, err := getAgenda(h.db, id)
	if errors.Is(err, errNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, agenda)
}

func (h *agendaHandler) create(w http.ResponseWriter, r *http.Request) {
	var a Agenda
	if err := json.NewDecoder(r.Body).Decode(&a); err != nil {
		writeError(w, http.StatusBadRequest, "corpo inválido")
		return
	}
	if a.Situacao == "" {
		a.Situacao = SituacaoLivre
	}
	if err := a.validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	if err := createAgenda(h.db, &a); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	h.hub.broadcast(agendaEvent{Entity: "agenda", Type: "created", Agenda: a})
	writeJSON(w, http.StatusCreated, a)
}

func (h *agendaHandler) update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}

	var a Agenda
	if err := json.NewDecoder(r.Body).Decode(&a); err != nil {
		writeError(w, http.StatusBadRequest, "corpo inválido")
		return
	}
	a.ID = id
	if err := a.validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	if err := updateAgenda(h.db, &a); err != nil {
		if errors.Is(err, errNotFound) {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	h.hub.broadcast(agendaEvent{Entity: "agenda", Type: "updated", Agenda: a})
	writeJSON(w, http.StatusOK, a)
}

func (h *agendaHandler) delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "id inválido")
		return
	}

	if err := deleteAgenda(h.db, id); err != nil {
		if errors.Is(err, errNotFound) {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	h.hub.broadcast(agendaEvent{Entity: "agenda", Type: "deleted", Agenda: Agenda{ID: id}})
	w.WriteHeader(http.StatusNoContent)
}
