import { useState } from 'react';
import { X } from 'lucide-react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuthStore } from '@/store/useAuthStore';
import { useQueueStore } from '@/store/useQueueStore';
import { api } from '@/lib/api';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export function LoginModal({ isOpen, onClose, onSuccess }: LoginModalProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSignUp, setIsSignUp] = useState(false);
  const setAccessToken = useAuthStore((state) => state.setAccessToken);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      if (isSignUp) {
        await api.post('/auth/register', { email, password });
      }
      
      const response = await api.post('/auth/login', { email, password });
      
      // We only receive and store the accessToken in memory.
      setAccessToken(response.data.accessToken);
      
      if (onSuccess) {
        onSuccess();
      }

      // If user logs in while on an event page, push them directly to the queue
      const eventMatch = pathname?.match(/\/events\/(\d+)/);
      if (eventMatch) {
        const eventId = eventMatch[1];
        try {
          const res = await api.get(`/queue/${eventId}/pass`, {
            headers: { Authorization: `Bearer ${response.data.accessToken}` }
          });
          if (res.status === 200 && res.data.passToken) {
            useQueueStore.getState().setQueuePass(eventId, res.data.passToken);
            router.push(`/events/${eventId}/seatmap`);
            onClose();
            return;
          }
        } catch (queueErr) {
          console.log('Fast track failed or queue active, routing to waiting room');
        }
        router.push(`/queue?eventId=${eventId}`);
        onClose();
        return;
      }
      
      onClose();

    } catch (err: any) {
      console.error('Login error:', err, err.response);
      
      const backendError = err.response?.data?.error || err.response?.data?.message;
      if (backendError) {
        setError(backendError);
      } else if (err.response?.status === 401 && !isSignUp) {
        setError('Invalid credentials, or this email was registered via Google.');
      } else {
        setError(isSignUp ? 'Failed to sign up.' : 'Failed to login. Please try again.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const toggleMode = (e: React.MouseEvent) => {
    e.preventDefault();
    setIsSignUp(!isSignUp);
    setError(null);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md p-6 relative animate-in fade-in zoom-in duration-200">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition-colors"
          aria-label="Close"
        >
          <X className="w-6 h-6" />
        </button>

        <div className="mb-8 text-center">
          <h2 className="text-2xl font-bold text-gray-900">{isSignUp ? 'Create an Account' : 'Welcome Back'}</h2>
          <p className="text-sm text-gray-500 mt-2">{isSignUp ? 'Join TicketCraft today.' : 'Sign in to your TicketCraft account.'}</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {error && (
            <div className="bg-red-50 text-red-600 p-3 rounded-md text-sm text-center">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="email">
              Email Address
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500 outline-none transition-shadow text-gray-900"
              placeholder="you@example.com"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500 outline-none transition-shadow text-gray-900"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full bg-blue-600 text-white font-bold py-3 px-4 rounded-md hover:bg-blue-700 transition-colors disabled:opacity-70 disabled:cursor-not-allowed flex justify-center items-center"
          >
            {isLoading ? (
              <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            ) : (
              isSignUp ? 'Sign Up' : 'Sign In'
            )}
          </button>
        </form>

        <div className="relative mt-6">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-300"></div>
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-white text-gray-500">Or continue with</span>
          </div>
        </div>

        <div className="mt-6">
          <button
            onClick={() => {
              if (pathname) localStorage.setItem('redirect_after_login', pathname);
              window.location.href = 'http://localhost:8080/oauth2/authorization/google';
            }}
            className="w-full flex items-center justify-center px-4 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 transition-colors"
          >
            <svg className="h-5 w-5 mr-2" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
            </svg>
            Google
          </button>
        </div>

        <div className="mt-6 text-center text-sm text-gray-500">
          {isSignUp ? (
            <>Already have an account? <a href="#" onClick={toggleMode} className="text-blue-600 font-semibold hover:underline">Sign In</a></>
          ) : (
            <>Don&apos;t have an account? <a href="#" onClick={toggleMode} className="text-blue-600 font-semibold hover:underline">Sign Up</a></>
          )}
        </div>
      </div>
    </div>
  );
}
