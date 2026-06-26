import axios from 'axios';
import { useAuthStore, TOKEN_ENDPOINT, CLIENT_ID } from "../hooks/useAuthStore";
import type { ForecastResponse, HistoryResponse, WeekPatternResponse } from "../types/room";

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
    async (error) => {
        const originalRequest = error.config;
        const isTokenRequest =
            error.config?.url?.includes('/openid-connect/token');

        if (error.response?.status === 401 && !isTokenRequest && !originalRequest._retry) {
            originalRequest._retry = true;

            const refreshToken = localStorage.getItem('refresh_token');
            if (!refreshToken) {
                useAuthStore.getState().logout();
                return Promise.reject(error);
            }

            try {
                const res = await fetch(TOKEN_ENDPOINT, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({
                        grant_type: 'refresh_token',
                        client_id: CLIENT_ID,
                        refresh_token: refreshToken,
                    }),
                });

                if (!res.ok) {
                    useAuthStore.getState().logout();
                    return Promise.reject(error);
                }

                const data = await res.json();
                localStorage.setItem('token', data.access_token);
                if (data.refresh_token) {
                    localStorage.setItem('refresh_token', data.refresh_token);
                }

                originalRequest.headers.Authorization = `Bearer ${data.access_token}`;
                return api(originalRequest);
            } catch {
                useAuthStore.getState().logout();
                return Promise.reject(error);
            }
        }
        return Promise.reject(error);
    }
);

export function fetchHistory(roomId: string, hours: number = 24): Promise<HistoryResponse> {
    return api.get<HistoryResponse>('/occupancy/history',
        { params: {roomId, hours } }).then(res => res.data);
}

export function fetchForecast(roomId: string, forecastHours: number = 12): Promise<ForecastResponse> {
    return api.get<ForecastResponse>('/forecast',
        { params: { roomId, forecastHours } }).then(res => res.data);
}

export function fetchWeekPattern(roomId: string, weeks: number = 8): Promise<WeekPatternResponse> {
    return api.get<WeekPatternResponse>('/occupancy/weekpattern',
        { params: { roomId, weeks } }).then(res => res.data);
}

export default api;