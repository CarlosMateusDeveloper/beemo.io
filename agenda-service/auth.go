package main

import (
	"context"
	"encoding/json"
	"io"
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

type tenantKey struct{}

func sessionTenant(ctx context.Context, source *http.Request) (int, int, string) {
	authorization := source.Header.Get("Authorization")
	cookie, err := source.Cookie("clinicos_session")
	if authorization == "" && (err != nil || cookie.Value == "") {
		return 0, http.StatusUnauthorized, "unauthorized"
	}
	if len(authorization) > 8192 {
		return 0, http.StatusUnauthorized, "unauthorized"
	}
	method := http.MethodGet
	if source.Method != http.MethodGet && source.Method != http.MethodHead && source.Method != http.MethodOptions {
		method = http.MethodPost
	}
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(env("AUTH_API_URL", "http://localhost:8080"), "/")+"/api/auth/session/check", nil)
	if err != nil {
		return 0, http.StatusServiceUnavailable, "unavailable"
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
		return 0, http.StatusServiceUnavailable, "unavailable"
	}
	defer res.Body.Close()
	if res.StatusCode == 200 {
		var account struct {
			Tenant *struct {
				ID int `json:"id"`
			} `json:"tenantAtivo"`
		}
		if json.NewDecoder(io.LimitReader(res.Body, 65536)).Decode(&account) != nil || account.Tenant == nil || account.Tenant.ID < 1 {
			return 0, http.StatusForbidden, "tenant_required"
		}
		return account.Tenant.ID, http.StatusOK, ""
	}
	if res.StatusCode == 401 || res.StatusCode == 403 {
		var body struct {
			Code string `json:"code"`
		}
		_ = json.NewDecoder(io.LimitReader(res.Body, 4096)).Decode(&body)
		if body.Code != "csrf_invalid" && body.Code != "tenant_required" {
			body.Code = "forbidden"
		}
		return 0, res.StatusCode, body.Code
	}
	return 0, http.StatusServiceUnavailable, "unavailable"
}
func sessionStatus(ctx context.Context, source *http.Request) int {
	_, status, _ := sessionTenant(ctx, source)
	return status
}
func tenantID(r *http.Request) int { id, _ := r.Context().Value(tenantKey{}).(int); return id }

func withAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		tenant, status, code := sessionTenant(r.Context(), r)
		if status != http.StatusOK {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(status)
			_ = json.NewEncoder(w).Encode(map[string]string{"code": code, "message": "Sessão sem acesso à clínica ou indisponível."})
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), tenantKey{}, tenant)))
	})
}
