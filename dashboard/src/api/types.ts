import type { RRType, ResponseSource, DNSRcode, Protocol } from '@/types/dns'

export type TimeRange = '15m' | '1h' | '6h' | '24h' | '7d' | '30d'

export interface OverviewStats {
  totalQueries: number
  cacheHits: number
  cacheMisses: number
  errors: number
  avgLatencyMs: number | null
  uniqueDomains: number
}

export interface TimelinePoint {
  timestamp: string
  count: number
}

export interface LatencyBucket {
  label: string
  count: number
}

export interface DomainStat {
  domain: string
  count: number
}

export interface DNSRequestRow {
  id: string
  timestamp: string
  domain: string
  qType: RRType
  protocol: Protocol
  source: ResponseSource
  rcode: DNSRcode
  // null when the server never responded (e.g. a timeout) — there's no responded_at to diff against.
  latencyMs: number | null
  resolutionTrace: string | null
}

export type DNSRequestDetail = DNSRequestRow

// The columns /api/stats/requests can sort by (a subset of what the backend allows —
// the ones exposed in the UI).
export type RequestSortColumn = 'received_at' | 'qname' | 'latency'
export type SortDirection = 'asc' | 'desc'

export interface RequestsQuery {
  startTime: number
  page: number
  sortColumn: RequestSortColumn
  sortDirection: SortDirection
  rcodes?: DNSRcode[]
  rrTypes?: RRType[]
  sources?: ResponseSource[]
  domainSearch?: string
}
