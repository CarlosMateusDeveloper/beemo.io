package main

import (
	"log"
	"net/http"
)

func withCORS(allowedOrigin string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", allowedOrigin)
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
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

	allowedOrigin := env("CORS_ALLOWED_ORIGIN", "http://localhost:5173")
	h := &agendaHandler{db: db, hub: newHub(allowedOrigin)}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /agendas", h.list)
	mux.HandleFunc("GET /agendas/{id}", h.get)
	mux.HandleFunc("POST /agendas", h.create)
	mux.HandleFunc("PUT /agendas/{id}", h.update)
	mux.HandleFunc("DELETE /agendas/{id}", h.delete)
	mux.HandleFunc("GET /ws/agenda", h.hub.serveWS)

	port := env("AGENDA_SERVICE_PORT", "8081")
	log.Printf("agenda-service ouvindo na porta %s (websocket em /ws/agenda)", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(allowedOrigin, mux)))
}
