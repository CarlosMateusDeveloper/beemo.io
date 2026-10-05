package main

import (
	"bytes"
	"context"
	"database/sql"
	"net/http"
	"strconv"
	"strings"
)

type DBTX interface {
	Query(string, ...any) (*sql.Rows, error)
	QueryRow(string, ...any) *sql.Row
	Exec(string, ...any) (sql.Result, error)
}
type txKey struct{}
type eventsKey struct{}

func requestDB(r *http.Request) DBTX { return r.Context().Value(txKey{}).(*sql.Tx) }
func deferBroadcast(r *http.Request, event any) {
	events := r.Context().Value(eventsKey{}).(*[]any)
	*events = append(*events, event)
}

type bufferedResponse struct {
	header http.Header
	status int
	body   bytes.Buffer
}

func (b *bufferedResponse) Header() http.Header { return b.header }
func (b *bufferedResponse) WriteHeader(status int) {
	if b.status == 0 {
		b.status = status
	}
}
func (b *bufferedResponse) Write(body []byte) (int, error) {
	if b.status == 0 {
		b.status = 200
	}
	return b.body.Write(body)
}
func withTenantTransaction(db *sql.DB, hub *hub, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/ws/") {
			next.ServeHTTP(w, r)
			return
		}
		tenant := tenantID(r)
		if tenant < 1 {
			writeError(w, 403, "Selecione uma clínica autorizada.")
			return
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			writeError(w, 503, "Banco indisponível.")
			return
		}
		defer tx.Rollback()
		if _, err = tx.Exec("SELECT set_config('app.tenant_id',$1,true)", strconv.Itoa(tenant)); err != nil {
			writeError(w, 503, "Não foi possível selecionar a clínica.")
			return
		}
		events := []any{}
		ctx := context.WithValue(context.WithValue(r.Context(), txKey{}, tx), eventsKey{}, &events)
		buffered := &bufferedResponse{header: make(http.Header)}
		next.ServeHTTP(buffered, r.WithContext(ctx))
		if buffered.status == 0 {
			buffered.status = 200
		}
		if buffered.status < 400 {
			if err = tx.Commit(); err != nil {
				writeError(w, 500, "Não foi possível salvar a alteração.")
				return
			}
		} else {
			_ = tx.Rollback()
		}
		for key, values := range buffered.header {
			w.Header()[key] = values
		}
		w.WriteHeader(buffered.status)
		_, _ = w.Write(buffered.body.Bytes())
		if buffered.status < 400 {
			for _, event := range events {
				hub.broadcast(tenant, event)
			}
		}
	})
}
