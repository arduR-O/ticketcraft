import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface QueueState {
  // Queue Pass tokens are per-event (key: eventId, value: passToken)
  queuePasses: Record<string, string>;
  
  // Future state for Phase 5 (Queue Flow) will go here:
  // position: number | null;
  // estimatedWait: string | null;
  // status: 'WAITING' | 'PROMOTED' | null;

  setQueuePass: (eventId: string, passToken: string) => void;
  clearQueuePass: (eventId: string) => void;
  clearAllPasses: () => void;
}

export const useQueueStore = create<QueueState>()(
  persist(
    (set) => ({
      queuePasses: {},

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

      clearAllPasses: () => set({ queuePasses: {} }),
    }),
    {
      name: 'ticketcraft-queue',
    }
  )
);
