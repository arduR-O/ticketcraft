import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null; // For simplicity in this mockup
  setTokens: (access: string, refresh: string) => void;
  setUserId: (id: string) => void;
  logout: () => void;
  
  // Queue Pass tokens are per-event
  queuePasses: Record<string, string>;
  setQueuePass: (eventId: string, passToken: string) => void;
  clearQueuePass: (eventId: string) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: 'user123', // Default mock user for testing since auth endpoints might not be fully built
      queuePasses: {},

      setTokens: (access, refresh) => set({ accessToken: access, refreshToken: refresh }),
      setUserId: (id) => set({ userId: id }),
      logout: () => set({ accessToken: null, refreshToken: null, queuePasses: {} }),

      setQueuePass: (eventId, passToken) =>
        set((state) => ({
          queuePasses: {
            ...state.queuePasses,
            [eventId]: passToken,
          },
        })),
      clearQueuePass: (eventId) =>
        set((state) => {
          const newPasses = { ...state.queuePasses };
          delete newPasses[eventId];
          return { queuePasses: newPasses };
        }),
    }),
    {
      name: 'ticketcraft-auth',
    }
  )
);
