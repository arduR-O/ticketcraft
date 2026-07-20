import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  userId: string | null;
  userEmail: string | null;
  setAccessToken: (access: string) => void;
  logout: () => void;
}

// Helper to decode JWT without an external library
function decodeJwtPayload(token: string): any {
  try {
    return JSON.parse(atob(token.split('.')[1]));
  } catch (e) {
    return {};
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  userId: null,
  userEmail: null,

  setAccessToken: (access) => {
    const payload = decodeJwtPayload(access);
    set({ 
      accessToken: access, 
      userId: payload.sub || null,
      userEmail: payload.email || null
    });
  },

  logout: () => set({ accessToken: null, userId: null, userEmail: null }),
}));
