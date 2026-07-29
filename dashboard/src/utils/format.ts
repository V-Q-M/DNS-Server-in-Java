import { format, formatDistanceToNow } from 'date-fns'

export function formatLatency(ms: number | null): string {
  if (ms === null) return '—'
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

export function formatPercent(ratio: number, decimals = 1): string {
  return `${(ratio * 100).toFixed(decimals)}%`
}

export function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}K`
  return String(Math.round(n))
}

export function formatQpm(qpm: number): string {
  return `${qpm.toFixed(2)} /min`
}

export function formatDate(iso: string): string {
  return format(new Date(iso), 'MMM d, HH:mm:ss')
}

export function formatDateShort(iso: string): string {
  return format(new Date(iso), 'HH:mm')
}

export function formatRelative(iso: string): string {
  return formatDistanceToNow(new Date(iso), { addSuffix: true })
}

export function formatTrend(ratio: number): string {
  const pct = (ratio * 100).toFixed(1)
  return ratio >= 0 ? `+${pct}%` : `${pct}%`
}
