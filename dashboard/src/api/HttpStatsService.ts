import type { StatsService } from './StatsService'
import type {
  OverviewStats,
  TimelinePoint,
  LatencyBucket,
  DomainStat,
  DNSRequestRow,
  DNSRequestDetail,
  RequestsQuery,
} from './types'
import { rcodeName, rrTypeName } from '@/types/dns'
import type { RRType, ResponseSource, DNSRcode, Protocol } from '@/types/dns'

// The server sends SQL timestamps as "yyyy-MM-dd HH:mm:ss[.nnnnnnnnn]" (java.sql.Timestamp#toString),
// not ISO 8601 — this reshapes them into something `Date` reliably parses everywhere.
function parseSqlTimestamp(value: string): string {
  return new Date(value.replace(' ', 'T').replace(/(\.\d{3})\d*$/, '$1')).toISOString()
}

interface RawRequestRow {
  transaction_id: string
  received_at: string
  qname: string
  qtype: number
  protocol: Protocol
  rcode: number | null
  response_source: ResponseSource
  responded_at: string | null
  resolution_trace: string | null
}

// Latency buckets come back as labels like "5-20ms", "100-500ms", ">500ms" (and possibly
// "<5ms") — sort by the bound closest to zero, with open-ended buckets ("<"/">") at the edges.
function latencyBucketOrder(label: string): number {
  if (label.startsWith('<')) return -Infinity
  if (label.startsWith('>')) return Infinity
  const match = label.match(/^(\d+(?:\.\d+)?)/)
  return match ? Number(match[1]) : 0
}

function buildRequestRow(raw: RawRequestRow): DNSRequestRow {
  const latencyMs = raw.responded_at
    ? new Date(parseSqlTimestamp(raw.responded_at)).getTime() - new Date(parseSqlTimestamp(raw.received_at)).getTime()
    : null
  return {
    id: raw.transaction_id,
    timestamp: parseSqlTimestamp(raw.received_at),
    domain: raw.qname,
    qType: rrTypeName(raw.qtype),
    protocol: raw.protocol,
    source: raw.response_source,
    rcode: rcodeName(raw.rcode),
    latencyMs,
    resolutionTrace: raw.resolution_trace,
  }
}

export class HttpStatsService implements StatsService {
  constructor(private readonly baseUrl: string = '') {}

  private async get<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
    const search = new URLSearchParams()
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined) search.set(k, String(v))
      }
    }
    const query = search.toString()
    const res = await fetch(`${this.baseUrl}${path}${query ? `?${query}` : ''}`)
    if (!res.ok) throw new Error(`HTTP ${res.status} for ${path}`)
    return res.json() as Promise<T>
  }

  async getOverview(startTime: number): Promise<OverviewStats> {
    const raw = await this.get<{
      total_queries: number
      cache_hits: number
      cache_misses: number
      errors: number
      avg_latency_ms: number | null
      unique_domains: number
    }>('/api/stats/overview', { start_time: startTime })
    return {
      totalQueries: raw.total_queries,
      cacheHits: raw.cache_hits,
      cacheMisses: raw.cache_misses,
      errors: raw.errors,
      avgLatencyMs: raw.avg_latency_ms,
      uniqueDomains: raw.unique_domains,
    }
  }

  async getTimeline(startTime: number, granularity: string): Promise<TimelinePoint[]> {
    const raw = await this.get<Record<string, number>>('/api/stats/timeline', { start_time: startTime, granularity })
    return Object.entries(raw)
      .map(([bucket, count]) => ({ timestamp: parseSqlTimestamp(bucket), count }))
      .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
  }

  getRcodeBreakdown(startTime: number) {
    return this.get<Partial<Record<DNSRcode, number>>>('/api/stats/rcodes', { start_time: startTime })
  }

  getProtocolBreakdown(startTime: number) {
    return this.get<Partial<Record<Protocol, number>>>('/api/stats/protocols', { start_time: startTime })
  }

  getRRTypeBreakdown(startTime: number) {
    return this.get<Partial<Record<RRType, number>>>('/api/stats/rr-types', { start_time: startTime })
  }

  getResponseSourceBreakdown(startTime: number) {
    return this.get<Partial<Record<ResponseSource, number>>>('/api/stats/sources', { start_time: startTime })
  }

  async getLatencyDistribution(startTime: number): Promise<LatencyBucket[]> {
    const raw = await this.get<Record<string, number>>('/api/stats/latency-distribution', { start_time: startTime })
    return Object.entries(raw)
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => latencyBucketOrder(a.label) - latencyBucketOrder(b.label))
  }

  async getTopDomains(startTime: number): Promise<DomainStat[]> {
    const raw = await this.get<Record<string, number>>('/api/stats/top-domains', { start_time: startTime })
    return Object.entries(raw)
      .map(([domain, count]) => ({ domain, count }))
      .sort((a, b) => b.count - a.count)
  }

  async getRequests(query: RequestsQuery): Promise<DNSRequestRow[]> {
    const raw = await this.get<RawRequestRow[]>('/api/stats/requests', {
      start_time: query.startTime,
      page: query.page,
      sorting_column: query.sortColumn,
      sort_direction: query.sortDirection,
      rcodes: query.rcodes?.length ? query.rcodes.join(',') : undefined,
      rr_types: query.rrTypes?.length ? query.rrTypes.join(',') : undefined,
      sources: query.sources?.length ? query.sources.join(',') : undefined,
      domain_search: query.domainSearch || undefined,
    })
    return raw.map(buildRequestRow)
  }

  async getRequest(id: string): Promise<DNSRequestDetail> {
    const raw = await this.get<RawRequestRow>(`/api/stats/request/${id}`)
    return buildRequestRow(raw)
  }
}
