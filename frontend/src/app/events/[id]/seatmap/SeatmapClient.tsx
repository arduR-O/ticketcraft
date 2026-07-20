"use client";

import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { api, API_BASE_URL } from '@/lib/api';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';
import { Loader2, AlertCircle } from 'lucide-react';
import { CheckoutModal } from '@/components/CheckoutModal';

interface Seat {
  id: number;
  seatNumber: string;
  rowNumber: string;
  section: string;
  xCoordinate: number;
  yCoordinate: number;
  category: string;
  status: 'AVAILABLE' | 'LOCKED' | 'SOLD';
  price: number;
}

export function SeatmapClient({ eventId }: { eventId: string }) {
  const [seats, setSeats] = useState<Seat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [isCheckoutOpen, setIsCheckoutOpen] = useState(false);
  const router = useRouter();
  
  const eventSourceRef = useRef<EventSource | null>(null);
  const heartbeatIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const accessToken = useAuthStore((state) => state.accessToken);
  const queuePasses = useQueueStore((state) => state.queuePasses);

  useEffect(() => {
    if (!accessToken) {
      router.push('/');
      return;
    }

    const fetchInitialSeats = async () => {
      try {
        const response = await api.get(`/events/${eventId}/seatmap`);
        setSeats(response.data);
        setLoading(false);
      } catch (err: any) {
        console.error('Failed to load seatmap', err);
        setError('Failed to load seatmap. You may need a valid queue pass.');
        setLoading(false);
      }
    };

    fetchInitialSeats();

    // Setup SSE for live seat updates
    const setupSSE = () => {
      const passToken = queuePasses[eventId];
      if (!passToken) return;

      const url = new URL(`${API_BASE_URL}/events/${eventId}/seat-stream`);
      url.searchParams.append('access_token', accessToken);
      url.searchParams.append('queue_pass', passToken);
      
      const eventSource = new EventSource(url.toString());
      
      eventSource.addEventListener('seat-update', (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data && data.updates && Array.isArray(data.updates)) {
            data.updates.forEach((update: any) => {
              setSeats(prev => prev.map(seat => 
                seat.id === update.seatId ? { ...seat, status: update.newStatus } : seat
              ));
              
              if (update.newStatus !== 'AVAILABLE') {
                setSelectedSeatIds(prev => prev.filter(id => id !== update.seatId));
              }
            });
          }
        } catch (e) {
          console.error("Error parsing SSE data", e);
        }
      });

      eventSource.onerror = (error) => {
        console.error('SSE Error:', error);
      };

      eventSourceRef.current = eventSource;
    };

    setupSSE();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, [eventId, accessToken, queuePasses, router]);

  useEffect(() => {
    // Setup heartbeat to queue service to maintain active session
    let heartbeatInterval: NodeJS.Timeout;
    
    const sendHeartbeat = async () => {
      try {
        await api.post(`/queue/${eventId}/heartbeat`);
      } catch (err) {
        console.error("Failed to send heartbeat", err);
      }
    };

    if (!isCheckoutOpen) {
      // Send immediately, then every 2 seconds
      sendHeartbeat();
      heartbeatInterval = setInterval(sendHeartbeat, 2000); // Demo purpose: 2s heartbeat for fast eviction. Prod will have a longer heartbeat.
      heartbeatIntervalRef.current = heartbeatInterval;
    }

    return () => {
      if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
      }
    };
  }, [eventId, isCheckoutOpen]);

  const handleSeatClick = (seat: Seat) => {
    if (seat.status !== 'AVAILABLE') return;

    setSelectedSeatIds(prev => {
      if (prev.includes(seat.id)) {
        return prev.filter(id => id !== seat.id);
      } else {
        if (prev.length >= 6) {
          alert('You can only select up to 6 seats.');
          return prev;
        }
        return [...prev, seat.id];
      }
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="w-12 h-12 text-blue-600 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
        <div className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
          <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Access Denied</h2>
          <p className="text-gray-600 mb-6">{error}</p>
          <button
            onClick={() => router.push(`/events/${eventId}`)}
            className="bg-blue-600 text-white font-bold py-2 px-6 rounded hover:bg-blue-700"
          >
            Return to Event
          </button>
        </div>
      </div>
    );
  }

  const selectedSeats = seats.filter(s => selectedSeatIds.includes(s.id));
  const totalPrice = selectedSeats.reduce((sum, seat) => sum + seat.price, 0);

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <div className="p-6 bg-white border-b border-gray-200 flex justify-between items-center shadow-sm">
        <h1 className="text-2xl font-bold text-gray-900">Select Your Seats</h1>
        <div className="flex gap-4">
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 bg-gray-200 rounded-full border border-gray-300"></div>
            <span className="text-sm text-gray-600">Available</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 bg-blue-500 rounded-full border border-blue-400"></div>
            <span className="text-sm text-gray-600">Selected</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 bg-yellow-400 rounded-full border border-yellow-500"></div>
            <span className="text-sm text-gray-600">Locked</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 bg-red-500 rounded-full border border-red-600"></div>
            <span className="text-sm text-gray-600">Sold</span>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-auto relative p-8 pb-32">
        <div className="mx-auto bg-white rounded-3xl border border-gray-200 shadow-xl relative p-8" style={{ width: '700px', height: '600px' }}>
          {/* Stage Area */}
          <div className="absolute top-4 left-1/2 -translate-x-1/2 w-64 h-12 bg-gray-100 rounded-b-full border-t border-gray-200 shadow-inner flex items-center justify-center">
            <span className="text-gray-400 font-bold uppercase tracking-widest text-sm">Stage</span>
          </div>

          {/* Seat Grid */}
          {seats.map(seat => {
            const isSelected = selectedSeatIds.includes(seat.id);
            const isAvailable = seat.status === 'AVAILABLE';
            
            let bgColor = 'bg-gray-200';
            let borderColor = 'border-gray-300';
            let cursor = 'cursor-pointer hover:bg-gray-300 hover:scale-110';
            
            if (isSelected) {
              bgColor = 'bg-blue-500 shadow-[0_0_10px_rgba(59,130,246,0.3)]';
              borderColor = 'border-blue-400';
            } else if (seat.status === 'LOCKED') {
              bgColor = 'bg-yellow-400';
              borderColor = 'border-yellow-500';
              cursor = 'cursor-not-allowed opacity-50';
            } else if (seat.status === 'SOLD') {
              bgColor = 'bg-red-500';
              borderColor = 'border-red-600';
              cursor = 'cursor-not-allowed opacity-50';
            }

            return (
              <div
                key={seat.id}
                onClick={() => handleSeatClick(seat)}
                className={`absolute w-8 h-8 rounded-t-lg rounded-b-sm border-b-2 transition-all duration-200 group flex justify-center items-end pb-1 ${bgColor} ${borderColor} ${cursor}`}
                style={{ 
                  left: `${seat.xCoordinate + 50}px`, 
                  top: `${seat.yCoordinate + 50}px`
                }}
              >
                {/* Tooltip */}
                <div className="absolute -top-12 left-1/2 -translate-x-1/2 bg-white text-gray-900 text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 whitespace-nowrap pointer-events-none z-10 shadow-lg border border-gray-200 transition-opacity">
                  <div className="font-bold">{seat.section}</div>
                  <div>Row {seat.rowNumber}, Seat {seat.seatNumber}</div>
                  <div className="text-blue-400 font-medium">${seat.price.toFixed(2)}</div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Action Bar */}
      <div className={`bg-white border-t border-gray-200 p-4 transition-transform duration-300 ${selectedSeats.length > 0 ? 'translate-y-0' : 'translate-y-full absolute bottom-0 w-full'} fixed bottom-0 left-0 w-full z-20 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)]`}>
        <div className="max-w-7xl mx-auto flex justify-between items-center">
          <div>
            <h3 className="text-gray-900 font-bold text-lg">{selectedSeats.length} Seat{selectedSeats.length !== 1 ? 's' : ''} Selected</h3>
            <p className="text-gray-400 text-sm">
              {selectedSeats.map(s => `${s.rowNumber}${s.seatNumber}`).join(', ')}
            </p>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <p className="text-gray-400 text-sm">Total</p>
              <p className="text-gray-900 font-bold text-2xl">${totalPrice.toFixed(2)}</p>
            </div>
            <button
              onClick={() => setIsCheckoutOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-8 rounded-lg shadow-lg hover:shadow-blue-500/20 transition-all transform hover:-translate-y-0.5 active:translate-y-0"
            >
              Proceed to Checkout
            </button>
          </div>
        </div>
      </div>

      {isCheckoutOpen && (
        <CheckoutModal
          isOpen={isCheckoutOpen}
          onClose={() => setIsCheckoutOpen(false)}
          eventId={eventId}
          selectedSeats={selectedSeats}
          totalPrice={totalPrice}
        />
      )}
    </div>
  );
}
