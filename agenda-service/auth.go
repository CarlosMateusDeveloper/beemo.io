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
type accessKey struct{}
type sessionAccess struct {
	Tenant      int
	Permissions map[string]bool
	DoctorID    int
}

func readSessionAccess(ctx context.Context, source *http.Request) (sessionAccess, int, string) {
	authorization := source.Header.Get("Authorization")
	cookie, err := source.Cookie("clinicos_session")
	if authorization == "" && (err != nil || cookie.Value == "") {
		return sessionAccess{}, http.StatusUnauthorized, "unauthorized"
	}
	if len(authorization) > 8192 {
		return sessionAccess{}, http.StatusUnauthorized, "unauthorized"
	}
	method := http.MethodGet
	if source.Method != http.MethodGet && source.Method != http.MethodHead && source.Method != http.MethodOptions {
		method = http.MethodPost
	}
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(env("AUTH_API_URL", "http://localhost:8080"), "/")+"/api/auth/session/check", nil)
	if err != nil {
		return sessionAccess{}, http.StatusServiceUnavailable, "unavailable"
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
		return sessionAccess{}, http.StatusServiceUnavailable, "unavailable"
	}
	defer res.Body.Close()
	if res.StatusCode == 200 {
		var account struct {
			Tenant *struct {
				ID          int      `json:"id"`
				Permissions []string `json:"permissoes"`
				DoctorID    *int     `json:"idMedico"`
			} `json:"tenantAtivo"`
		}
		if json.NewDecoder(io.LimitReader(res.Body, 65536)).Decode(&account) != nil || account.Tenant == nil || account.Tenant.ID < 1 {
			return sessionAccess{}, http.StatusForbidden, "tenant_required"
		}
		access := sessionAccess{Tenant: account.Tenant.ID, Permissions: map[string]bool{}}
		for _, permission := range account.Tenant.Permissions {
			access.Permissions[permission] = true
		}
		if account.Tenant.DoctorID != nil {
			access.DoctorID = *account.Tenant.DoctorID
		}
		return access, http.StatusOK, ""
	}
	if res.StatusCode == 401 || res.StatusCode == 403 {
		var body struct {
			Code string `json:"code"`
		}
		_ = json.NewDecoder(io.LimitReader(res.Body, 4096)).Decode(&body)
		if body.Code != "csrf_invalid" && body.Code != "tenant_required" {
			body.Code = "forbidden"
		}
		return sessionAccess{}, res.StatusCode, body.Code
	}
	return sessionAccess{}, http.StatusServiceUnavailable, "unavailable"
}
func sessionTenant(ctx context.Context, source *http.Request) (int, int, string) {
	access, status, code := readSessionAccess(ctx, source)
	return access.Tenant, status, code
}
func sessionStatus(ctx context.Context, source *http.Request) int {
	_, status, _ := sessionTenant(ctx, source)
	return status
}
func tenantID(r *http.Request) int { id, _ := r.Context().Value(tenantKey{}).(int); return id }
func currentAccess(r *http.Request) sessionAccess {
	access, _ := r.Context().Value(accessKey{}).(sessionAccess)
	return access
}
func doctorID(r *http.Request) int { return currentAccess(r).DoctorID }

func requirePermission(permission string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !currentAccess(r).Permissions[permission] {
			writeJSON(w, http.StatusForbidden, map[string]string{"code": "forbidden", "message": "Seu perfil não tem permissão para esta ação."})
			return
		}
		next.ServeHTTP(w, r)
	})
}

func withAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		access, status, code := readSessionAccess(r.Context(), r)
		if status != http.StatusOK {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(status)
			_ = json.NewEncoder(w).Encode(map[string]string{"code": code, "message": "Sessão sem acesso à clínica ou indisponível."})
			return
		}
		ctx := context.WithValue(r.Context(), tenantKey{}, access.Tenant)
		next.ServeHTTP(w, r.WithContext(context.WithValue(ctx, accessKey{}, access)))
	})
}
