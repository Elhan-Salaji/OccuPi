import axios from 'axios';
import { MOCK_FORECAST, MOCK_HISTORY, MOCK_WEEKPATTERN } from "./mockData";
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
    (error) => {
        if (error.response?.status === 401) {
            // Wenn ungültig ist -> Login
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export function fetchHistory(roomId: string, hours: number = 24): Promise<HistoryResponse> {
    return Promise.resolve(MOCK_HISTORY);
}

export function fetchForecast(roomId: string, forecastHours: number = 12): Promise<ForecastResponse> {
    return Promise.resolve(MOCK_FORECAST);
}

export function fetchWeekPattern(roomId: string, weeks: number = 8): Promise<WeekPatternResponse> {
    return Promise.resolve(MOCK_WEEKPATTERN);
}

export default api;