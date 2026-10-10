package main

import (
	"log"
	"net/http"
)

func withCORS(allowedOrigin string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if allowedBrowserOrigin(r.Header.Get("Origin"), allowedOrigin) {
			w.Header().Set("Access-Control-Allow-Origin", r.Header.Get("Origin"))
			w.Header().Set("Access-Control-Allow-Credentials", "true")
		}
		w.Header().Add("Vary", "Origin")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-TOKEN")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func main() {
	db := openDB()
	defer db.Close()
	bootstrapSchema(db)

	allowedOrigin := env("CORS_ALLOWED_ORIGIN", "http://localhost:5173,http://127.0.0.1:5173")
	h := &agendaHandler{db: db, hub: newHub(allowedOrigin)}

	mux := http.NewServeMux()
	mux.Handle("GET /agendas", requirePermission("agenda.visualizar", http.HandlerFunc(h.list)))
	mux.Handle("GET /agendas/{id}", requirePermission("agenda.visualizar", http.HandlerFunc(h.get)))
	mux.Handle("POST /agendas", requirePermission("agenda.gerenciar", http.HandlerFunc(h.create)))
	mux.Handle("PUT /agendas/{id}", requirePermission("agenda.gerenciar", http.HandlerFunc(h.update)))
	mux.Handle("DELETE /agendas/{id}", requirePermission("agenda.gerenciar", http.HandlerFunc(h.delete)))

	mux.Handle("GET /consultas", requirePermission("agenda.visualizar", http.HandlerFunc(h.listConsultas)))
	mux.Handle("GET /consultas/{id}", requirePermission("agenda.visualizar", http.HandlerFunc(h.getConsulta)))
	mux.Handle("POST /consultas", requirePermission("agenda.gerenciar", http.HandlerFunc(h.createConsulta)))
	mux.Handle("PUT /consultas/{id}", requirePermission("agenda.gerenciar", http.HandlerFunc(h.updateConsulta)))

	mux.Handle("GET /medicos", requirePermission("medico.visualizar", http.HandlerFunc(h.listMedicos)))
	mux.Handle("GET /pacientes", requirePermission("paciente.visualizar", http.HandlerFunc(h.listPacientes)))
	mux.Handle("GET /ws/agenda", requirePermission("agenda.visualizar", http.HandlerFunc(h.hub.serveWS)))

	port := env("AGENDA_SERVICE_PORT", "8081")
	log.Printf("agenda-service ouvindo na porta %s (websocket em /ws/agenda)", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(allowedOrigin, withAuth(withTenantTransaction(db, h.hub, mux)))))
}
