import type { TimeRange } from '@/api/types'

const RANGE_SECONDS: Record<TimeRange, number> = {
  '15m': 15 * 60,
  '1h': 60 * 60,
  '6h': 6 * 60 * 60,
  '24h': 24 * 60 * 60,
  '7d': 7 * 24 * 60 * 60,
  '30d': 30 * 24 * 60 * 60,
}

// Postgres interval literals accepted by the /timeline endpoint, chosen so a range
// renders as a reasonable number of buckets regardless of how wide it is.
const TIMELINE_GRANULARITY: Record<TimeRange, string> = {
  '15m': '1 minutes',
  '1h': '5 minutes',
  '6h': '15 minutes',
  '24h': '1 hours',
  '7d': '6 hours',
  '30d': '1 days',
}

export function rangeSeconds(range: TimeRange): number {
  return RANGE_SECONDS[range]
}

export function startTimeFor(range: TimeRange): number {
  return Math.floor(Date.now() / 1000) - RANGE_SECONDS[range]
}

export function timelineGranularityFor(range: TimeRange): string {
  return TIMELINE_GRANULARITY[range]
}
