"use client";

import { useState } from "react";
import useSWR from "swr";
import { SearchBar } from "@/components/SearchBar";
import { EventCard, EventResponse } from "@/components/EventCard";
import { useGeolocation } from "@/hooks/useGeolocation";
import { useDebounce } from "@/hooks/useDebounce";
import { api } from "@/lib/api";

const fetcher = (url: string) => api.get(url).then((res) => res.data);

interface HomeClientProps {
  initialEvents: EventResponse[];
}

export function HomeClient({ initialEvents }: HomeClientProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearch = useDebounce(searchTerm, 300);
  const { lat, lng, loading: geoLoading } = useGeolocation();

  let endpoint = null;

  if (debouncedSearch) {
    endpoint = `/events/search?query=${encodeURIComponent(debouncedSearch)}`;
  } else if (!geoLoading && lat && lng) {
    endpoint = `/events/nearby?lat=${lat}&lng=${lng}`;
  } else if (!geoLoading) {
    endpoint = `/events/search?query=concert`;
  }

  // Use initialEvents as fallback data to ensure Next.js SSR matches initial hydration!
  const { data: events, error, isLoading } = useSWR<EventResponse[]>(
    endpoint,
    fetcher,
    { fallbackData: !debouncedSearch && !lat && !lng ? initialEvents : undefined }
  );

  return (
    <div className="min-h-screen bg-gray-50 pb-10">
      {/* Hero Section */}
      <div className="relative w-full bg-gray-950 py-24 px-4 sm:px-6 lg:px-8 mb-4">
        <div className="relative z-10 max-w-4xl mx-auto flex flex-col items-center justify-center">
          <h1 className="text-4xl font-extrabold text-white tracking-tight sm:text-6xl text-center">
            Let&apos;s Make Live Happen
          </h1>
          <p className="mt-6 text-xl text-gray-400 text-center max-w-2xl">
            Shop millions of live events and discover can&apos;t-miss concerts, games, theater and more.
          </p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Search Bar placed directly above results */}
        <div className="mb-10 pt-4">
          <SearchBar 
            value={searchTerm} 
            onChange={(e) => setSearchTerm(e.target.value)} 
          />
        </div>

        {/* Results Section */}
        <div>
          <h2 className="text-2xl font-bold text-gray-900 mb-6">
            {debouncedSearch 
              ? `Search results for "${debouncedSearch}"` 
              : lat && lng 
                ? "Events Near You" 
                : "Trending Events"}
          </h2>

          {(isLoading || geoLoading) && !events && (
            <div className="flex justify-center items-center h-48">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            </div>
          )}

          {error && (
            <div className="text-center text-red-600 py-10 bg-red-50 rounded-lg">
              Failed to load events. Please try again later.
            </div>
          )}

          {events && events.length === 0 && (
            <div className="text-center text-gray-500 py-10">
              No events found. Try a different search term.
            </div>
          )}

          {events && events.length > 0 && (
            <div className="flex flex-col">
              {[...events].reverse().map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
