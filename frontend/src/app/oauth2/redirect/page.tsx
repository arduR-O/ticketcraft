'use client';

import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';
import { Loader2 } from 'lucide-react';

function OAuth2RedirectContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get('code');
    
    if (!code) {
      setTimeout(() => setError('No authorization code found. Please try logging in again.'), 0);
      return;
    }

    const exchangeToken = async () => {
      try {
        const response = await api.post('/auth/token', { code });
        setAccessToken(response.data.accessToken);
        
        // Handle post-login redirection for better UX
        const redirectUrl = localStorage.getItem('redirect_after_login');
        if (redirectUrl) {
          localStorage.removeItem('redirect_after_login');
          
          // If they were on an event page, send them straight to the queue/booking flow
          const eventMatch = redirectUrl.match(/\/events\/(\d+)/);
          if (eventMatch) {
            const eventId = eventMatch[1];
            try {
              const res = await api.get(`/queue/${eventId}/pass`, {
                headers: { Authorization: `Bearer ${response.data.accessToken}` }
              });
              if (res.status === 200 && res.data.passToken) {
                useQueueStore.getState().setQueuePass(eventId, res.data.passToken);
                router.push(`/events/${eventId}/seatmap`);
                return;
              }
            } catch (queueErr) {
              console.log('Fast track failed or queue active, routing to waiting room');
            }
            router.push(`/queue?eventId=${eventId}`);
            return;
          }
          
          router.push(redirectUrl);
        } else {
          router.push('/');
        }
      } catch (err: any) {
        console.error('OAuth token exchange failed:', err);
        setError('Failed to authenticate with Google. Please try again.');
      }
    };

    exchangeToken();
  }, [searchParams, router, setAccessToken]);

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col justify-center items-center p-4">
        <div className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
          <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Authentication Failed</h2>
          <p className="text-gray-600 mb-6">{error}</p>
          <button
            onClick={() => router.push('/')}
            className="w-full bg-blue-600 text-white font-bold py-3 px-4 rounded-md hover:bg-blue-700 transition-colors"
          >
            Return to Home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center items-center">
      <Loader2 className="w-12 h-12 text-blue-600 animate-spin mb-4" />
      <h2 className="text-xl font-semibold text-gray-700 animate-pulse">
        Securely signing you in...
      </h2>
      <p className="text-sm text-gray-500 mt-2">
        Please wait while we complete your authentication.
      </p>
    </div>
  );
}

export default function OAuth2RedirectPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-gray-50 flex flex-col justify-center items-center">
        <Loader2 className="w-12 h-12 text-blue-600 animate-spin mb-4" />
        <h2 className="text-xl font-semibold text-gray-700 animate-pulse">
          Loading...
        </h2>
      </div>
    }>
      <OAuth2RedirectContent />
    </Suspense>
  );
}
