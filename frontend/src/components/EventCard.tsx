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
      <div className="group flex flex-col sm:flex-row py-6 border-b border-gray-200 hover:bg-gray-50/50 transition-colors duration-200 -mx-4 px-4 sm:mx-0 sm:px-0">
        <div className="sm:w-64 h-48 sm:h-32 shrink-0 relative bg-gray-100 overflow-hidden sm:rounded-md">
          {/* We will use a dynamic placeholder image based on the event ID */}
          <img
            src={`https://picsum.photos/seed/${event.id}/800/400`}
            alt={event.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
        </div>
        
        <div className="flex-1 sm:pl-6 py-4 sm:py-0 flex flex-col justify-center">
          <div>
            <h3 className="text-2xl font-bold text-gray-900 group-hover:text-blue-600 transition-colors">
              {event.title}
            </h3>
            <p className="text-gray-500 mt-1 text-sm truncate">{event.description}</p>
          </div>

          <div className="mt-3 flex flex-wrap gap-4">
            <div className="flex items-center text-gray-700 text-sm font-medium" suppressHydrationWarning>
              <Calendar className="w-4 h-4 mr-1.5 text-blue-600" />
              {formattedDate}
            </div>
            <div className="flex items-center text-gray-700 text-sm font-medium">
              <MapPin className="w-4 h-4 mr-1.5 text-blue-600" />
              {event.venueName}, {event.venueLocation}
            </div>
          </div>
        </div>

        <div className="hidden sm:flex sm:flex-col sm:justify-center sm:items-end sm:w-48">
          <button className="text-blue-600 font-semibold py-2 px-6 rounded-full border border-blue-600 hover:bg-blue-50 transition-colors">
            See Tickets
          </button>
        </div>
      </div>
    </Link>
  );
}
