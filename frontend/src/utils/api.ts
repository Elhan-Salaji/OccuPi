import axios from 'axios';
import { useAuthStore} from "../hooks/useAuthStore";

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

//Request Interceptor (JWT stamp)
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response Interceptor
api.interceptors.response.use(
    (response) => response,
    (error) => {
        const isTokenRequest =
            error.config?.url?.includes('/openid-connect/token');
        if (error.response?.status === 401 && !isTokenRequest) {
            useAuthStore.getState().logout();
        }
        return Promise.reject(error);
    }
);

export default api;