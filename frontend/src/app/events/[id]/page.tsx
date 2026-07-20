import { Calendar, MapPin, Info, AlertCircle } from 'lucide-react';
import { EventActions } from './EventActions';

export interface EventDetailResponse {
  id: number;
  title: string;
  description: string;
  date: string;
  artistName: string;
  venueName: string;
  venueLocation: string;
  latitude: number;
  longitude: number;
  totalSeats: number;
  availableSeats: number;
  pricingTiers: Record<string, number>;
}

async function getEvent(id: string): Promise<EventDetailResponse | null> {
  // Use absolute URL for SSR fetch
  const res = await fetch(`http://localhost:8080/api/v1/events/${id}`, {
    next: { revalidate: 3600 } // Cache for 1 hour (3600 seconds)
  });
  
  if (!res.ok) {
    return null;
  }
  return res.json();
}

export default async function EventDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  const event = await getEvent(resolvedParams.id);

  if (!event) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <h1 className="text-4xl font-bold text-gray-900 mb-4">Event Not Found</h1>
          <p className="text-gray-500">The event you are looking for does not exist or has been removed.</p>
        </div>
      </div>
    );
  }

  const formattedDate = new Date(event.date).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });

  // Format pricing tiers to be user-friendly (e.g. GENERAL_ADMISSION -> General Admission)
  const formatTierName = (name: string) => {
    return name.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ');
  };

  return (
    <div className="min-h-screen bg-white">
      {/* Hero Image Section */}
      <div className="w-full h-[40vh] md:h-[50vh] bg-gray-900 relative">
        <img
          src={`https://picsum.photos/seed/${event.id}/1600/900`}
          alt={event.title}
          className="w-full h-full object-cover opacity-60"
        />
        <div className="absolute bottom-0 left-0 w-full bg-gradient-to-t from-black/80 to-transparent p-6 md:p-12">
          <div className="max-w-7xl mx-auto">
            <span className="inline-block px-3 py-1 bg-blue-600 text-white text-sm font-semibold rounded-full mb-4">
              Concert
            </span>
            <h1 className="text-4xl md:text-6xl font-bold text-white mb-2">{event.title}</h1>
            <h2 className="text-xl md:text-3xl text-gray-200">{event.artistName}</h2>
          </div>
        </div>
      </div>

      {/* Content Section */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="flex flex-col lg:flex-row gap-16">
          
          {/* Main Info */}
          <div className="flex-1">
            <div className="mb-12">
              <h3 className="text-2xl font-bold text-gray-900 mb-8 border-b-2 border-gray-900 inline-block pb-2">Event Details</h3>
              
              <div className="space-y-8">
                <div className="flex items-start">
                  <Calendar className="w-6 h-6 text-gray-400 mr-4 shrink-0 mt-1" />
                  <div>
                    <h4 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-1">Date and Time</h4>
                    <p className="text-lg text-gray-900">{formattedDate}</p>
                  </div>
                </div>

                <div className="flex items-start">
                  <MapPin className="w-6 h-6 text-gray-400 mr-4 shrink-0 mt-1" />
                  <div>
                    <h4 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-1">Location</h4>
                    <p className="text-lg text-gray-900">{event.venueName}</p>
                    <p className="text-gray-500 mt-1">{event.venueLocation}</p>
                  </div>
                </div>

                <div className="flex items-start">
                  <Info className="w-6 h-6 text-gray-400 mr-4 shrink-0 mt-1" />
                  <div className="max-w-2xl">
                    <h4 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-1">About this event</h4>
                    <p className="text-gray-700 mt-2 leading-relaxed text-lg">{event.description}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Sidebar Actions */}
          <div className="lg:w-[400px]">
            <div className="sticky top-24 pt-8 lg:pt-0 border-t lg:border-t-0 lg:border-l border-gray-200 lg:pl-10">
              <h3 className="text-3xl font-bold text-gray-900 mb-2">Tickets</h3>
              <p className="text-gray-500 mb-8 text-lg">Secure your spot before they sell out.</p>
              
              <div className="border-b border-gray-200 pb-6 mb-6 space-y-4">
                {Object.entries(event.pricingTiers || {}).map(([tier, price]) => (
                  <div key={tier} className="flex justify-between items-center text-xl font-bold text-gray-900">
                    <span>{formatTierName(tier)}</span>
                    <span>${price.toFixed(2)}</span>
                  </div>
                ))}
                
                {Object.keys(event.pricingTiers || {}).length === 0 && (
                  <div className="flex justify-between items-center text-xl font-bold text-gray-900">
                    <span>General Admission</span>
                    <span>Price TBA</span>
                  </div>
                )}
                <p className="text-sm text-gray-500 pt-2 border-t border-gray-100">Prices are subject to change</p>
              </div>

              {/* Interactive Client Component for Book Tickets & Login */}
              <EventActions eventId={event.id} />

              
              <p className="text-xs text-gray-400 mt-4 text-center">
                All sales are final. Ticket limits may apply.
              </p>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
