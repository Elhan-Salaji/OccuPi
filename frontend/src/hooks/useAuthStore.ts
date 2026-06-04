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

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    token: null,
    isAuthenticated: false,

    // Checks at startup whether data exists in localStorage
    initializeAuth: () => {
        const token = localStorage.getItem('token');
        const savedUser = localStorage.getItem('user');

        if (token && savedUser) {
            set({
                token,
                user: JSON.parse(savedUser),
                isAuthenticated: true,
            });
        }
    },

    // Mock-Login
    login: async (username, password) => {
        if (username.trim() === 'admin' && password === 'admin123') {
            const mockToken = 'mock-jwt-token-xyz-12345';
            const mockUser = { username, role: 'admin' };

            localStorage.setItem('token', mockToken);
            localStorage.setItem('user', JSON.stringify(mockUser));

            set({
                user: mockUser,
                token: mockToken,
                isAuthenticated: true,
            });
            return true;
        }
        return false;
    },

    // Logout Logik
    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        set({ user: null, token: null, isAuthenticated: false });
        // redirect to login page, alternative over router
        window.location.href = '/login';
    },
}));

//you stay logged in even after refresh
useAuthStore.getState().initializeAuth();