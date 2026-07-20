import { EventResponse } from "@/components/EventCard";
import { HomeClient } from "@/components/HomeClient";

// Next.js 16 Best Practice: Use a Server Component to fetch initial data 
// securely and natively using fetch(), keeping the bundle small and SEO friendly.
export default async function HomePage() {
  
  // Use the internal network URL since this runs on the server side
  // The gateway is running on localhost:8080 on this VM
  const gatewayUrl = process.env.INTERNAL_GATEWAY_URL || 'http://localhost:8080';
  
  let initialEvents: EventResponse[] = [];
  try {
    const res = await fetch(`${gatewayUrl}/api/v1/events/search?query=concert`, {
      // Revalidate every 60 seconds (ISR)
      next: { revalidate: 60 }
    });
    
    if (res.ok) {
      initialEvents = await res.json();
    }
  } catch (err) {
    console.error("Failed to fetch initial events for SSR", err);
  }

  return (
    <HomeClient initialEvents={initialEvents} />
  );
}
