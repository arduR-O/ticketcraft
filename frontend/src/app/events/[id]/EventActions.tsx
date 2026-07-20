"use client";

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';
import { useUIStore } from '@/store/useUIStore';
import { api } from '@/lib/api';

export function EventActions({ eventId }: { eventId: number }) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const accessToken = useAuthStore((state) => state.accessToken);
  const setQueuePass = useQueueStore((state) => state.setQueuePass);
  const openLoginModal = useUIStore((state) => state.openLoginModal);
  const router = useRouter();

  const handleBookTickets = async () => {
    setError(null);
    if (!accessToken) {
      openLoginModal();
      return;
    }

    // Attempt to get queue pass (Fast-Track)
    setIsLoading(true);
    try {
      // As per Phase 1.2: GET /api/v1/queue/{eventId}/pass
      const res = await api.get(`/queue/${eventId}/pass`);
      
      if (res.status === 200 && res.data.passToken) {
        setQueuePass(eventId.toString(), res.data.passToken);
        router.push(`/events/${eventId}/seatmap`);
      }
    } catch (err: any) {
      if (err.response?.status === 409) {
        // Queue is active and full. Route to waiting room.
        router.push(`/queue?eventId=${eventId}`);
      } else {
        setError("Something went wrong while trying to access the event.");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <>
      <div className="mt-8 flex flex-col sm:flex-row items-center gap-4">
        <button
          onClick={handleBookTickets}
          disabled={isLoading}
          className="w-full sm:w-auto px-8 py-3 bg-blue-600 text-white font-bold rounded hover:bg-blue-700 transition-colors disabled:opacity-70 flex justify-center"
        >
          {isLoading ? (
            <div className="w-6 h-6 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
          ) : (
            'Book Tickets'
          )}
        </button>
        {error && <p className="text-red-500 text-sm">{error}</p>}
      </div>
    </>
  );
}
