"use client";

import { useState } from "react";
import useSWR from "swr";
import { SearchBar } from "@/components/SearchBar";
import { EventCard, EventResponse } from "@/components/EventCard";
import { useGeolocation } from "@/hooks/useGeolocation";
import { useDebounce } from "@/hooks/useDebounce";
import { api } from "@/lib/api";

const fetcher = (url: string) => api.get(url).then((res) => res.data);

export default function HomePage() {
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearch = useDebounce(searchTerm, 300);
  const { lat, lng, loading: geoLoading } = useGeolocation();

  // Determine the endpoint to use
  let endpoint = null;

  if (debouncedSearch) {
    // If there's a search term, use text search
    endpoint = `/events/search?query=${encodeURIComponent(debouncedSearch)}`;
  } else if (!geoLoading) {
    // If geolocation is resolved (either successfully or failed)
    if (lat && lng) {
      endpoint = `/events/nearby?lat=${lat}&lng=${lng}`;
    } else {
      // Fallback if location is denied
      endpoint = `/events/search?query=concert`; // Default trending fallback
    }
  }

  const { data: events, error, isLoading } = useSWR<EventResponse[]>(
    endpoint,
    fetcher
  );

  return (
    <div className="min-h-screen bg-gray-50 py-10">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header / Hero Section */}
        <div className="text-center mb-10">
          <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight sm:text-5xl">
            Let's Make Live Happen
          </h1>
          <p className="mt-4 text-xl text-gray-500">
            Shop millions of live events and discover can't-miss concerts, games, theater and more.
          </p>
        </div>

        {/* Search Bar */}
        <div className="mb-12">
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

          {(isLoading || geoLoading) && (
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
            <div className="grid gap-6">
              {events.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
