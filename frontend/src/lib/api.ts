import axios from 'axios';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';

// Assuming gateway is running on localhost:8080
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

export const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const { accessToken, userId } = useAuthStore.getState();
  const { queuePasses } = useQueueStore.getState();

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }

  // Determine the eventId to look up the correct Queue Pass.
  // 1. Try to extract from URL (e.g. /events/1001/seats)
  let eventId = null;
  const match = config.url?.match(/\/(?:events|queue)\/(\d+)/);
  if (match) {
    eventId = match[1];
  } 
  // 2. If not in URL, try to extract from JSON body (e.g. POST /bookings { eventId: 1001 })
  else if (config.data && typeof config.data === 'object' && config.data.eventId) {
    eventId = config.data.eventId.toString();
  }

  // If we found an eventId, inject its specific queue pass if we have one
  if (eventId) {
    const passToken = queuePasses[eventId];
    if (passToken) {
      config.headers['X-Queue-Pass'] = passToken;
    }
  }

  return config;
});

// Prevent multiple simultaneous refresh calls if multiple API calls fail at once
let isRefreshing = false;
let failedQueue: Array<{ resolve: (value?: unknown) => void; reject: (reason?: any) => void }> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Intercept 401s to automatically trigger token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If it's a 401 Unauthorized and we haven't already retried this request
    // Skip refresh logic for login and refresh endpoints
    if (originalRequest.url?.includes('/auth/login') || originalRequest.url?.includes('/auth/refresh')) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      // Prevent infinite loops if the refresh endpoint itself 401s
      if (originalRequest.url === '/auth/refresh') {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // If already refreshing, pause this request and add it to the queue
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Hit the refresh endpoint (browser automatically attaches HttpOnly refresh cookie via withCredentials)
        const response = await axios.post(
          `${API_BASE_URL}/auth/refresh`,
          {},
          { withCredentials: true }
        );

        const newAccessToken = response.data.accessToken;
        
        // Save new token to memory
        useAuthStore.getState().setAccessToken(newAccessToken);
        
        // Retry all queued requests with the new token
        processQueue(null, newAccessToken);
        
        // Retry the original request
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
        
      } catch (refreshError) {
        // If refresh fails (e.g. cookie expired), log out completely
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        
        if (typeof window !== 'undefined') {
          // Instead of redirecting to a non-existent /login page, open the modal
          import('@/store/useUIStore').then(({ useUIStore }) => {
            useUIStore.getState().openLoginModal();
          });
          
          if (window.location.pathname !== '/') {
            window.location.href = '/';
          }
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    if (error.response?.status === 403) {
      const errorData = error.response.data;
      const errorMessage = errorData?.error || errorData?.message || '';
      
      if (typeof errorMessage === 'string' && errorMessage.includes('Missing queue pass token')) {
        // Extract eventId from URL if possible (e.g., /events/1001/seatmap)
        const match = originalRequest.url?.match(/\/events\/(\d+)/);
        if (match && typeof window !== 'undefined') {
          const eventId = match[1];
          window.location.href = `/queue?eventId=${eventId}`;
          return Promise.reject(error);
        }
      }
    }
    
    return Promise.reject(error);
  }
);
