import type { RRType, ResponseSource, DNSRcode, Protocol } from '@/types/dns'

export const RCODE_COLORS: Record<DNSRcode, string> = {
  NOERROR:     '#22c55e',
  NXDOMAIN:    '#f59e0b',
  SERVFAIL:    '#ef4444',
  REFUSED:     '#dc2626',
  FORMERR:     '#f97316',
  NOTIMP:      '#8b5cf6',
  OTHER:       '#64748b',
  NO_RESPONSE: '#334155',
}

export const PROTOCOL_COLORS: Record<Protocol, string> = {
  UDP: '#3b82f6',
  TCP: '#8b5cf6',
}

export const RR_TYPE_COLORS: Record<RRType, string> = {
  A:    '#3b82f6',
  AAAA: '#8b5cf6',
  MX:   '#22c55e',
  CNAME:'#f59e0b',
  TXT:  '#ec4899',
  NS:   '#06b6d4',
  PTR:  '#84cc16',
  SOA:  '#f97316',
  SRV:  '#14b8a6',
  OTHER:'#64748b',
}

export const RESPONSE_SOURCE_COLORS: Record<ResponseSource, string> = {
  CACHE:    '#3b82f6',
  ZONE:     '#22c55e',
  RECURSIVE_RESOLVER:'#f59e0b',
}

export const CHART_GRID_COLOR  = 'rgba(115,115,115,0.15)'
export const CHART_TICK_COLOR  = 'rgba(115,115,115,0.8)'

export function alpha(hex: string, a: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${a})`
}
