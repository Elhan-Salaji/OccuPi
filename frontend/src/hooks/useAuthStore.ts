import { create } from 'zustand';

interface User {
    username: string;
    role: string;
}

interface AuthState {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
    login: (username: string, password: string) => Promise<boolean>;
    logout: () => void;
    initializeAuth: () => void;
}

const MOCK_TOKEN = 'mock-jwt-token-xyz-12345';

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    token: null,
    isAuthenticated: false,

    // added error handling and edited token handling
    initializeAuth: () => {
        try {
            const token = localStorage.getItem('token');
            const savedUser = localStorage.getItem('user');

            // token string must exactly match the mock token now
            if (token === MOCK_TOKEN && savedUser) {
                set({
                    token,
                    user: JSON.parse(savedUser),
                    isAuthenticated: true,
                });
            } else {
                localStorage.removeItem('token');
                localStorage.removeItem('user');
            }

        } catch (error) {
            // Bei korrupten JSON-Daten im localStorage alles zurücksetzen
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            set({ user: null, token: null, isAuthenticated: false });
        }
    },

    login: async (username, password) => {
        if (username.trim() === 'admin' && password === 'admin123') {
            const mockUser = { username, role: 'admin' };

            localStorage.setItem('token', MOCK_TOKEN);
            localStorage.setItem('user', JSON.stringify(mockUser));

            set({
                user: mockUser,
                token: MOCK_TOKEN,
                isAuthenticated: true,
            });
            return true;
        }
        return false;
    },
    // removed window.location.href. We now handle the redirect reactively via the ProtectedRoute / App layout.
    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        set({ user: null, token: null, isAuthenticated: false });
    },
}));

// the automatic call at the module level has been removed here
