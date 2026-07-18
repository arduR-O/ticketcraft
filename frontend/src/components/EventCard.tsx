import Link from 'next/link';
import { Calendar, MapPin } from 'lucide-react';

export interface EventResponse {
  id: number;
  title: string;
  description: string;
  date: string;
  artistName: string;
  venueName: string;
  venueLocation: string;
}

export function EventCard({ event }: { event: EventResponse }) {
  const formattedDate = new Date(event.date).toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });

  return (
    <Link href={`/events/${event.id}`}>
      <div className="group flex flex-col sm:flex-row bg-white border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-shadow duration-200">
        <div className="sm:w-64 h-48 sm:h-auto shrink-0 relative bg-gray-100 overflow-hidden">
          {/* We will use a dynamic placeholder image based on the artist name */}
          <img
            src={`https://source.unsplash.com/800x400/?concert,${encodeURIComponent(event.artistName)}`}
            alt={event.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
        </div>
        
        <div className="flex-1 p-5 flex flex-col justify-between">
          <div>
            <h3 className="text-xl font-bold text-gray-900 group-hover:text-blue-600 transition-colors">
              {event.title}
            </h3>
            <p className="text-gray-500 mt-1 text-sm line-clamp-2">{event.description}</p>
          </div>

          <div className="mt-4 space-y-2">
            <div className="flex items-center text-gray-700 text-sm">
              <Calendar className="w-4 h-4 mr-2 text-gray-400" />
              {formattedDate}
            </div>
            <div className="flex items-center text-gray-700 text-sm">
              <MapPin className="w-4 h-4 mr-2 text-gray-400" />
              {event.venueName}, {event.venueLocation}
            </div>
          </div>
        </div>

        <div className="hidden sm:flex sm:flex-col sm:justify-center sm:items-center sm:w-48 bg-gray-50 border-l border-gray-200 p-4">
          <span className="text-sm text-gray-500 mb-2">Tickets from</span>
          <button className="bg-blue-600 text-white font-semibold py-2 px-6 rounded hover:bg-blue-700 transition-colors">
            See Tickets
          </button>
        </div>
      </div>
    </Link>
  );
}
