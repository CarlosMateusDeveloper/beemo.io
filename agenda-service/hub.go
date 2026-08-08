package main

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"

	"github.com/gorilla/websocket"
)

// agendaEvent é o que trafega no WebSocket sempre que uma agenda muda.
type agendaEvent struct {
	Type   string `json:"type"` // created | updated | deleted
	Agenda Agenda `json:"agenda"`
}

// hub mantém os clientes WebSocket conectados e distribui eventos pra todos.
type hub struct {
	mu      sync.Mutex
	clients map[*websocket.Conn]struct{}
	allowedOrigin string
}

func newHub(allowedOrigin string) *hub {
	return &hub{
		clients:       make(map[*websocket.Conn]struct{}),
		allowedOrigin: allowedOrigin,
	}
}

func (h *hub) broadcast(event agendaEvent) {
	payload, err := json.Marshal(event)
	if err != nil {
		log.Printf("hub: erro ao serializar evento: %v", err)
		return
	}

	h.mu.Lock()
	defer h.mu.Unlock()
	for conn := range h.clients {
		if err := conn.WriteMessage(websocket.TextMessage, payload); err != nil {
			conn.Close()
			delete(h.clients, conn)
		}
	}
}

func (h *hub) serveWS(w http.ResponseWriter, r *http.Request) {
	upgrader := websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool {
			origin := r.Header.Get("Origin")
			return origin == "" || origin == h.allowedOrigin
		},
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("hub: falha no upgrade do websocket: %v", err)
		return
	}

	h.mu.Lock()
	h.clients[conn] = struct{}{}
	h.mu.Unlock()

	// Só precisamos manter a leitura pra detectar quando o cliente desconecta;
	// o hub é quem envia (broadcast), o cliente não manda comandos por aqui.
	go func() {
		defer func() {
			h.mu.Lock()
			delete(h.clients, conn)
			h.mu.Unlock()
			conn.Close()
		}()
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}()
}
