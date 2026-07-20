'use client';

import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';
import { api, API_BASE_URL } from '@/lib/api';
import { Users, Clock, ShieldCheck, Loader2 } from 'lucide-react';

function QueueContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const eventId = searchParams.get('eventId');
  const accessToken = useAuthStore((state) => state.accessToken);
  const setQueuePass = useQueueStore((state) => state.setQueuePass);

  const [status, setStatus] = useState<'CONNECTING' | 'WAITING' | 'PROMOTED' | 'DISCONNECTED'>('CONNECTING');
  const [position, setPosition] = useState<number | null>(null);
  const [estimatedWait, setEstimatedWait] = useState<string | null>(null);

  useEffect(() => {
    if (!eventId) {
      router.push('/');
      return;
    }

    if (!accessToken) {
      // Must be logged in to queue. Redirect to home, let them login, then try again.
      router.push(`/events/${eventId}`);
      return;
    }

    let eventSource: EventSource | null = null;
    let heartbeatInterval: NodeJS.Timeout | null = null;

    const connectToQueue = () => {
      // 1. Establish SSE Connection immediately
      const sseUrl = `${API_BASE_URL}/queue/stream?eventId=${eventId}&access_token=${accessToken}`;
      eventSource = new EventSource(sseUrl);

      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          
          if (data.status === 'PROMOTED') {
            setStatus('PROMOTED');
            if (data.passToken) {
              setQueuePass(eventId, data.passToken);
              cleanup();
              router.push(`/events/${eventId}/seatmap`);
            }
          } else if (data.status === 'WAITING') {
            setStatus('WAITING');
            setPosition(data.position);
            setEstimatedWait(data.estimatedWait);
          } else if (data.status === 'DISCONNECTED') {
            setStatus('DISCONNECTED');
            cleanup();
          }
        } catch (err) {
          console.error('Failed to parse SSE data:', err);
        }
      };

      eventSource.onerror = (error) => {
        console.error('SSE Error:', error);
        setStatus('DISCONNECTED');
        cleanup();
      };

      // 2. Attempt Fast-Track concurrently
      api.get(`/queue/${eventId}/pass`)
        .then((response) => {
          if (response.data && response.data.passToken) {
            setQueuePass(eventId, response.data.passToken);
            cleanup();
            router.push(`/events/${eventId}/seatmap`);
          }
        })
        .catch((error) => {
          // 409 Conflict means the queue is active. SSE will handle updates.
          if (error.response?.status !== 409) {
            console.error('Fast-track failed with unexpected error:', error);
          }
        });

      // 3. Start Heartbeat
      heartbeatInterval = setInterval(async () => {
        try {
          await api.post(`/queue/${eventId}/heartbeat`);
        } catch (error) {
          console.error('Heartbeat failed:', error);
        }
      }, 2000); // Demo purpose: 2s heartbeat for fast eviction. Prod will have a longer heartbeat.
    };

    const cleanup = () => {
      if (eventSource) {
        eventSource.close();
        eventSource = null;
      }
      if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
      }
    };

    connectToQueue();

    return () => {
      cleanup();
    };
  }, [eventId, accessToken, router, setQueuePass]);

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center text-blue-600 mb-6 animate-pulse">
          <ShieldCheck className="w-16 h-16" />
        </div>
        <h2 className="text-center text-3xl font-extrabold text-gray-900">
          You are in the waiting room
        </h2>
        <p className="mt-2 text-center text-sm text-gray-600">
          Due to high demand, we&apos;ve placed you in a virtual queue. Please do not refresh this page.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow-xl sm:rounded-lg sm:px-10 border border-gray-100">
          
          {status === 'CONNECTING' && (
            <div className="flex flex-col items-center justify-center space-y-4 py-6">
              <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
              <p className="text-gray-500 font-medium">Securing your place in line...</p>
            </div>
          )}

          {status === 'WAITING' && (
            <div className="space-y-6">
              <div className="bg-blue-50 rounded-xl p-6 text-center border border-blue-100">
                <p className="text-sm font-semibold text-blue-800 uppercase tracking-wide">
                  Your place in line
                </p>
                <div className="mt-2 flex items-center justify-center text-5xl font-black text-blue-600">
                  <Users className="w-8 h-8 mr-3 opacity-70" />
                  {position !== null ? position.toLocaleString() : '--'}
                </div>
              </div>

              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200">
                <div className="flex items-center text-gray-700 font-medium">
                  <Clock className="w-5 h-5 mr-3 text-gray-400" />
                  Estimated Wait
                </div>
                <div className="text-gray-900 font-bold">
                  {estimatedWait || 'Calculating...'}
                </div>
              </div>
              
              <div className="w-full bg-gray-200 rounded-full h-2.5 overflow-hidden">
                <div className="bg-blue-600 h-2.5 rounded-full animate-pulse" style={{ width: '100%' }}></div>
              </div>
            </div>
          )}

          {status === 'PROMOTED' && (
            <div className="flex flex-col items-center justify-center space-y-4 py-6">
              <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-gray-900">It&apos;s your turn!</h3>
              <p className="text-gray-500 font-medium text-center">Redirecting you to the seatmap...</p>
            </div>
          )}

          {status === 'DISCONNECTED' && (
            <div className="flex flex-col items-center justify-center space-y-4 py-6">
              <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-gray-900">Connection Lost</h3>
              <p className="text-gray-500 text-center text-sm">
                We lost connection to the waiting room. Please return to the event page and try again.
              </p>
              <button 
                onClick={() => router.push(`/events/${eventId}`)}
                className="mt-4 w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
              >
                Return to Event
              </button>
            </div>
          )}
        </div>
        
        <div className="mt-6 text-center">
          <p className="text-xs text-gray-400">
            Powered by TicketCraft High-Availability Queueing System
          </p>
        </div>
      </div>
    </div>
  );
}

export default function QueuePage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
      </div>
    }>
      <QueueContent />
    </Suspense>
  );
}
