import axios from 'axios';
import { useAuthStore } from '@/store/useAuthStore';

// Assuming gateway is running on localhost:8080
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

export const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const { accessToken, queuePasses, userId } = useAuthStore.getState();

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }

  // Inject user ID for backend mock purposes (as our backend relies on X-User-Id header sometimes)
  if (userId) {
    config.headers['X-User-Id'] = userId;
  }

  // If the request URL has an eventId in it (e.g. /events/1001/seats), inject the queue pass
  // This is a naive regex matching for /events/{id} or /booking/{id} to append the queue pass.
  const match = config.url?.match(/\/(?:events|queue|booking)\/(\d+)/);
  if (match) {
    const eventId = match[1];
    const passToken = queuePasses[eventId];
    if (passToken) {
      config.headers['X-Queue-Pass'] = passToken;
    }
  }

  return config;
});

// Intercept 401s for refresh logic (stubbed for now since we don't have a real refresh endpoint)
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // If it's a 401 and we have a refresh token, we would normally try to refresh here.
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      // Redirect to login if implemented
      if (typeof window !== 'undefined') {
        // window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
