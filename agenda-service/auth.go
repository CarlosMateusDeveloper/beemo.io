package main

import (
	"context"
	"net/http"
	"strings"
	"time"
)

var authClient = &http.Client{Timeout: 5 * time.Second, CheckRedirect: func(req *http.Request, via []*http.Request) error { return http.ErrUseLastResponse }}

func allowedBrowserOrigin(origin, configured string) bool {
	for _, allowed := range strings.Split(configured, ",") {
		if origin != "" && origin == strings.TrimSpace(allowed) {
			return true
		}
	}
	return false
}

func sessionStatus(ctx context.Context, source *http.Request) int {
	authorization := source.Header.Get("Authorization")
	cookie, err := source.Cookie("clinicos_session")
	if authorization == "" && (err != nil || cookie.Value == "") {
		return http.StatusUnauthorized
	}
	if len(authorization) > 8192 {
		return http.StatusUnauthorized
	}
	method := http.MethodGet
	if source.Method != http.MethodGet && source.Method != http.MethodHead && source.Method != http.MethodOptions {
		method = http.MethodPost
	}
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(env("AUTH_API_URL", "http://localhost:8080"), "/")+"/api/auth/session/check", nil)
	if err != nil {
		return http.StatusServiceUnavailable
	}
	req.Header.Set("Authorization", authorization)
	for _, name := range []string{"clinicos_session", "clinicos_csrf"} {
		if c, err := source.Cookie(name); err == nil {
			req.AddCookie(c)
		}
	}
	req.Header.Set("X-CSRF-TOKEN", source.Header.Get("X-CSRF-TOKEN"))
	res, err := authClient.Do(req)
	if err != nil {
		return http.StatusServiceUnavailable
	}
	defer res.Body.Close()
	if res.StatusCode == 200 || res.StatusCode == 401 || res.StatusCode == 403 {
		return res.StatusCode
	}
	return http.StatusServiceUnavailable
}

func withAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		status := sessionStatus(r.Context(), r)
		if status != http.StatusOK {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(status)
			if status == 403 {
				_, _ = w.Write([]byte("{\"code\":\"csrf_invalid\",\"message\":\"Atualize a sessão e tente novamente.\"}"))
			} else {
				_, _ = w.Write([]byte("{\"message\":\"Sessão indisponível ou inválida.\"}"))
			}
			return
		}
		next.ServeHTTP(w, r)
	})
}
