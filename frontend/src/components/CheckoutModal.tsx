import { useState, useEffect, useRef } from 'react';
import { X, CheckCircle2, AlertCircle, CreditCard, Loader2 } from 'lucide-react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

interface Seat {
  id: number;
  seatNumber: string;
  rowNumber: string;
  section: string;
  price: number;
}

interface CheckoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  eventId: string;
  selectedSeats: Seat[];
  totalPrice: number;
}

export function CheckoutModal({ isOpen, onClose, eventId, selectedSeats, totalPrice }: CheckoutModalProps) {
  const [step, setStep] = useState<'RESERVING' | 'PAYMENT' | 'SUCCESS' | 'ERROR'>('RESERVING');
  const [lockedSeats] = useState(selectedSeats);
  const [lockedTotalPrice] = useState(totalPrice);
  const [bookingId, setBookingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cardNumber, setCardNumber] = useState('1234567812345678');
  const [isProcessing, setIsProcessing] = useState(false);
  const hasInitiatedReservation = useRef(false);
  
  const router = useRouter();

  useEffect(() => {
    if (!isOpen) {
      // Reset state when closed, unless it's success (we want them to see it)
      if (step !== 'SUCCESS') {
        setStep('RESERVING');
        setBookingId(null);
        setError(null);
        setCardNumber('');
        hasInitiatedReservation.current = false;
      }
      return;
    }

    if (step === 'RESERVING' && lockedSeats.length > 0 && !hasInitiatedReservation.current) {
      hasInitiatedReservation.current = true;
      reserveSeats();
    }
  }, [isOpen, lockedSeats]);

  const reserveSeats = async () => {
    try {
      const response = await api.post('/bookings', {
        eventId: parseInt(eventId),
        seatIds: lockedSeats.map(s => s.id)
      });
      
      if (response.status === 201) {
        setBookingId(response.data.bookingId);
        setStep('PAYMENT');
      }
    } catch (err: any) {
      console.error('Reservation failed:', err);
      const backendError = err.response?.data?.message || err.response?.data?.error || 'Failed to reserve seats. They may have just been taken by someone else.';
      setError(backendError);
      setStep('ERROR');
    }
  };

  const handleCheckout = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bookingId) return;

    setIsProcessing(true);
    setError(null);

    try {
      const response = await api.post(`/bookings/${bookingId}/checkout`, {
        cardNumber: cardNumber.replace(/\s+/g, '')
      });

      if (response.status === 200) {
        setStep('SUCCESS');
      }
    } catch (err: any) {
      console.error('Checkout failed:', err);
      setError(err.response?.data?.message || err.response?.data?.error || 'Payment failed. Please try again.');
    } finally {
      setIsProcessing(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-70 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden relative animate-in fade-in zoom-in duration-300">
        
        {/* Header */}
        <div className="bg-gray-50 px-6 py-4 border-b border-gray-100 flex justify-between items-center">
          <h2 className="text-xl font-bold text-gray-900">
            {step === 'RESERVING' && 'Securing your seats...'}
            {step === 'PAYMENT' && 'Complete Checkout'}
            {step === 'SUCCESS' && 'Order Confirmed'}
            {step === 'ERROR' && 'Reservation Failed'}
          </h2>
          {step !== 'RESERVING' && step !== 'SUCCESS' && (
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
              <X className="w-6 h-6" />
            </button>
          )}
        </div>

        {/* Content */}
        <div className="p-8">
          
          {step === 'RESERVING' && (
            <div className="flex flex-col items-center justify-center py-8">
              <div className="relative">
                <Loader2 className="w-16 h-16 text-blue-600 animate-spin" />
                <div className="absolute inset-0 border-4 border-blue-200 rounded-full animate-ping opacity-20"></div>
              </div>
              <p className="mt-6 text-lg font-medium text-gray-700 text-center">
                Please wait while we lock in your tickets...
              </p>
              <p className="text-sm text-gray-500 mt-2 text-center">
                This usually takes just a moment.
              </p>
            </div>
          )}

          {step === 'PAYMENT' && (
            <div>
              <div className="bg-blue-50 border border-blue-100 rounded-xl p-4 mb-8">
                <div className="flex justify-between items-end mb-2">
                  <span className="text-sm font-medium text-blue-900 uppercase tracking-wider">Total Amount</span>
                  <p className="text-3xl font-bold text-blue-600">${lockedTotalPrice.toFixed(2)}</p>
                </div>
                <p className="text-sm text-blue-500 mt-2">
                  For {lockedSeats.length} ticket(s) 
                  ({lockedSeats.map(s => `${s.rowNumber}${s.seatNumber}`).join(', ')})
                </p>
              </div>

              {error && (
                <div className="mb-6 p-4 bg-red-50 border border-red-100 rounded-lg flex items-start text-red-700 text-sm">
                  <AlertCircle className="w-5 h-5 mr-3 shrink-0 mt-0.5" />
                  <p>{error}</p>
                </div>
              )}

              <form onSubmit={handleCheckout}>
                <div className="mb-6">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Card Details (Simulated)
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <CreditCard className="h-5 w-5 text-gray-400" />
                    </div>
                    <input
                      type="text"
                      required
                      value={cardNumber}
                      onChange={(e) => setCardNumber(e.target.value)}
                      placeholder="1234 5678 9012 3456"
                      className="pl-10 w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500 outline-none transition-shadow text-gray-900 font-mono"
                    />
                  </div>
                  <p className="text-xs text-gray-500 mt-2">
                    Enter any 16-digit number to simulate a successful payment.
                  </p>
                </div>

                <button
                  type="submit"
                  disabled={isProcessing}
                  className="w-full bg-gray-900 text-white font-bold py-4 rounded-xl hover:bg-black transition-all transform hover:-translate-y-0.5 shadow-lg flex justify-center items-center disabled:opacity-70 disabled:transform-none"
                >
                  {isProcessing ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin mr-2" /> Processing...
                    </>
                  ) : (
                    `Pay $${lockedTotalPrice.toFixed(2)}`
                  )}
                </button>
              </form>
            </div>
          )}

          {step === 'ERROR' && (
            <div className="text-center py-6">
              <div className="w-20 h-20 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
                <AlertCircle className="w-10 h-10 text-red-600" />
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-2">We couldn't lock your seats</h3>
              <p className="text-gray-600 mb-8">{error}</p>
              <button
                onClick={onClose}
                className="bg-gray-900 text-white font-bold py-3 px-8 rounded-lg hover:bg-black transition-colors"
              >
                Return to Seatmap
              </button>
            </div>
          )}

          {step === 'SUCCESS' && (
            <div className="text-center py-6">
              <div className="w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6 animate-in zoom-in duration-500">
                <CheckCircle2 className="w-12 h-12 text-green-600" />
              </div>
              <h3 className="text-2xl font-bold text-gray-900 mb-2">You're going to the event!</h3>
              <p className="text-gray-600 mb-2">Your tickets have been successfully booked.</p>
              <p className="text-sm font-mono text-gray-500 mb-8 bg-gray-50 py-2 rounded">Booking ID: {bookingId}</p>
              
              <button
                onClick={() => {
                  onClose();
                  router.push('/');
                }}
                className="bg-blue-600 text-white font-bold py-3 px-8 rounded-lg hover:bg-blue-700 transition-colors w-full"
              >
                Return to Home
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
