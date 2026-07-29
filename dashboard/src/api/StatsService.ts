import type { RRType, ResponseSource, DNSRcode, Protocol } from '@/types/dns'
import type {
  OverviewStats,
  TimelinePoint,
  LatencyBucket,
  DomainStat,
  DNSRequestRow,
  DNSRequestDetail,
  RequestsQuery,
} from './types'

export const STATS_SERVICE_KEY = Symbol('StatsService')

// startTime is a Unix epoch timestamp (seconds) — every query is "since startTime".
export interface StatsService {
  getOverview(startTime: number): Promise<OverviewStats>
  getTimeline(startTime: number, granularity: string): Promise<TimelinePoint[]>
  getRcodeBreakdown(startTime: number): Promise<Partial<Record<DNSRcode, number>>>
  getProtocolBreakdown(startTime: number): Promise<Partial<Record<Protocol, number>>>
  getRRTypeBreakdown(startTime: number): Promise<Partial<Record<RRType, number>>>
  getResponseSourceBreakdown(startTime: number): Promise<Partial<Record<ResponseSource, number>>>
  getLatencyDistribution(startTime: number): Promise<LatencyBucket[]>
  getTopDomains(startTime: number): Promise<DomainStat[]>
  getRequests(query: RequestsQuery): Promise<DNSRequestRow[]>
  getRequest(id: string): Promise<DNSRequestDetail>
}
