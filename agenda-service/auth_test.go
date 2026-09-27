package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSessionProtection(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/auth/session/check" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		c, err := r.Cookie("clinicos_session")
		if r.Header.Get("Authorization") != "Bearer test-valid-session" && (err != nil || c.Value != "test-cookie") {
			w.WriteHeader(401)
			return
		}
		if r.Method == "POST" && r.Header.Get("X-CSRF-TOKEN") != "test-csrf" {
			w.WriteHeader(403)
			return
		}
		w.WriteHeader(200)
	}))
	defer upstream.Close()
	t.Setenv("AUTH_API_URL", upstream.URL)
	handler := withAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	for _, tc := range []struct {
		method, path, auth, cookie, csrf string
		expected                         int
	}{
		{"GET", "/consultas", "", "", "", 401},
		{"GET", "/consultas", "Bearer invalid", "", "", 401},
		{"GET", "/consultas", "Bearer test-valid-session", "", "", 204},
		{"GET", "/ws/agenda", "", "test-cookie", "", 204},
		{"GET", "/ws/agenda?token=test-valid-session", "", "", "", 401},
		{"POST", "/consultas", "", "test-cookie", "", 403},
		{"POST", "/consultas", "", "test-cookie", "test-csrf", 204},
	} {
		r := httptest.NewRequest(tc.method, tc.path, nil)
		r.Header.Set("Authorization", tc.auth)
		if tc.cookie != "" {
			r.AddCookie(&http.Cookie{Name: "clinicos_session", Value: tc.cookie})
		}
		r.Header.Set("X-CSRF-TOKEN", tc.csrf)
		w := httptest.NewRecorder()
		handler.ServeHTTP(w, r)
		if w.Code != tc.expected {
			t.Errorf("%s %s: got %d want %d", tc.method, tc.path, w.Code, tc.expected)
		}
	}
	upstream.Close()
	r := httptest.NewRequest("GET", "/consultas", nil)
	r.Header.Set("Authorization", "Bearer test-valid-session")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != 503 {
		t.Errorf("backend offline: got %d", w.Code)
	}
}
func TestBrowserOrigins(t *testing.T) {
	configured := "http://localhost:5173,http://127.0.0.1:5173"
	if !allowedBrowserOrigin("http://127.0.0.1:5173", configured) || allowedBrowserOrigin("https://evil.example", configured) {
		t.Fatal("origin allowlist failed")
	}
	h := newHub(configured)
	r := httptest.NewRequest("GET", "/ws/agenda", nil)
	r.Header.Set("Origin", "https://evil.example")
	r.Header.Set("Connection", "Upgrade")
	r.Header.Set("Upgrade", "websocket")
	r.Header.Set("Sec-WebSocket-Version", "13")
	r.Header.Set("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
	w := httptest.NewRecorder()
	h.serveWS(w, r)
	if w.Code != 403 {
		t.Errorf("cross-origin websocket: %d", w.Code)
	}
}
