import { EventResponse } from '@/components/EventCard';
import { Calendar, MapPin, Info } from 'lucide-react';
import { EventActions } from './EventActions';

async function getEvent(id: string): Promise<EventResponse | null> {
  // Use absolute URL for SSR fetch
  const res = await fetch(`http://localhost:8080/api/v1/events/${id}`, {
    next: { revalidate: 60 } // Cache for 60 seconds
  });
  
  if (!res.ok) {
    return null;
  }
  return res.json();
}

export default async function EventDetailPage({ params }: { params: { id: string } }) {
  const event = await getEvent(params.id);

  if (!event) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
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

  return (
    <div className="min-h-screen bg-white">
      {/* Hero Image Section */}
      <div className="w-full h-[40vh] md:h-[50vh] bg-gray-900 relative">
        <img
          src={`https://source.unsplash.com/1600x900/?concert,${encodeURIComponent(event.artistName)}`}
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
        <div className="flex flex-col lg:flex-row gap-12">
          
          {/* Main Info */}
          <div className="flex-1">
            <div className="bg-gray-50 border border-gray-100 rounded-2xl p-6 md:p-8 mb-8">
              <h3 className="text-2xl font-bold text-gray-900 mb-6 border-b border-gray-200 pb-4">Event Details</h3>
              
              <div className="space-y-6">
                <div className="flex items-start">
                  <Calendar className="w-6 h-6 text-blue-600 mr-4 shrink-0 mt-1" />
                  <div>
                    <h4 className="font-semibold text-gray-900">Date and Time</h4>
                    <p className="text-gray-600">{formattedDate}</p>
                  </div>
                </div>

                <div className="flex items-start">
                  <MapPin className="w-6 h-6 text-blue-600 mr-4 shrink-0 mt-1" />
                  <div>
                    <h4 className="font-semibold text-gray-900">Location</h4>
                    <p className="text-gray-600">{event.venueName}</p>
                    <p className="text-gray-500 text-sm mt-1">{event.venueLocation}</p>
                  </div>
                </div>

                <div className="flex items-start">
                  <Info className="w-6 h-6 text-blue-600 mr-4 shrink-0 mt-1" />
                  <div>
                    <h4 className="font-semibold text-gray-900">About this event</h4>
                    <p className="text-gray-600 mt-2 leading-relaxed">{event.description}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Sidebar Actions */}
          <div className="lg:w-[400px]">
            <div className="sticky top-24 bg-white border border-gray-200 shadow-xl rounded-2xl p-6 md:p-8">
              <h3 className="text-2xl font-bold text-gray-900 mb-2">Tickets</h3>
              <p className="text-gray-500 mb-6">Secure your spot before they sell out.</p>
              
              <div className="bg-gray-50 rounded-lg p-4 mb-6">
                <div className="flex justify-between items-center text-lg font-semibold text-gray-900">
                  <span>General Admission</span>
                  <span>$ --</span>
                </div>
                <p className="text-sm text-gray-500 mt-1">Prices are subject to change</p>
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
