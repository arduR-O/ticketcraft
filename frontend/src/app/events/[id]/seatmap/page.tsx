import { SeatmapClient } from './SeatmapClient';

export default async function SeatmapPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <SeatmapClient eventId={resolvedParams.id} />;
}
