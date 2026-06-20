import { create } from 'zustand';

interface User {
    username: string;
    role: string;
}

type LoginResult = { ok: true } | { ok: false; error: string };

interface AuthState {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
    login: (username: string, password: string) => Promise<LoginResult>;
    logout: () => void;
}

// Keycloak connection — overridable via env, with local-dev defaults so the
// app works out of the box against the local Docker stack.
const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180';
const REALM = import.meta.env.VITE_KEYCLOAK_REALM ?? 'occupi';
const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'occupi-frontend';
const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;

interface JwtClaims {
    preferred_username?: string;
    email?: string;
    exp?: number;
    realm_access?: { roles?: string[] };
}

// Decode a JWT payload (base64url) without pulling in a dependency.
function decodeJwt(token: string): JwtClaims | null {
    try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(
            atob(base64)
                .split('')
                .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        return JSON.parse(json) as JwtClaims;
    } catch {
        return null;
    }
}

function userFromToken(token: string): User {
    const claims = decodeJwt(token);
    const username = claims?.preferred_username ?? 'unbekannt';
    const roles = claims?.realm_access?.roles ?? [];
    return { username, role: roles.includes('admin') ? 'admin' : 'user' };
}

function isExpired(token: string): boolean {
    const claims = decodeJwt(token);
    if (!claims?.exp) return true;
    // 5s clock-skew allowance
    return Date.now() >= claims.exp * 1000 - 5000;
}

// Derive the initial auth state from a token left in localStorage by a previous
// session. This runs synchronously when the store is created — i.e. before React's
// first render — so a page reload (F5) restores the session up front and
// ProtectedRoute sees the correct isAuthenticated value immediately, instead of
// briefly treating the user as logged out and redirecting to /login.
function loadInitialAuth(): Pick<AuthState, 'user' | 'token' | 'isAuthenticated'> {
    const token = localStorage.getItem('token');
    if (token && !isExpired(token)) {
        return { token, user: userFromToken(token), isAuthenticated: true };
    }
    // Token missing or expired — clear any stale leftovers.
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('refresh_token');
    return { user: null, token: null, isAuthenticated: false };
}

export const useAuthStore = create<AuthState>((set) => ({
    ...loadInitialAuth(),

    // Real login: exchange HdM credentials for a Keycloak JWT (password grant).
    // Credentials go straight to Keycloak, which federates the HdM LDAP.
    login: async (username, password) => {
        let res: Response;
        try {
            res = await fetch(TOKEN_ENDPOINT, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({
                    grant_type: 'password',
                    client_id: CLIENT_ID,
                    username: username.trim(),
                    password,
                }),
            });
        } catch {
            return {
                ok: false,
                error: 'Keine Verbindung zu Keycloak. Bist du im HdM-Netz/VPN und läuft der Stack?',
            };
        }

        if (!res.ok) {
            let body: { error?: string; error_description?: string } | null = null;
            try {
                body = await res.json();
            } catch {
                /* non-JSON error body */
            }
            if (body?.error === 'invalid_grant') {
                return { ok: false, error: 'Ungültiger Benutzername oder Passwort.' };
            }
            return {
                ok: false,
                error: body?.error_description ?? body?.error ?? `Keycloak-Fehler (HTTP ${res.status}).`,
            };
        }

        const data = await res.json();
        const token = data.access_token as string;
        localStorage.setItem('token', token);
        if (data.refresh_token) {
            localStorage.setItem('refresh_token', data.refresh_token);
        }
        set({ token, user: userFromToken(token), isAuthenticated: true });
        return { ok: true };
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('refresh_token');
        set({ user: null, token: null, isAuthenticated: false });
    },
}));
