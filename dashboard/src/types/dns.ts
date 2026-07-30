// 'OTHER' covers qtypes the backend doesn't map to a named RR type (see rrTypeName).
export type RRType = 'A' | 'AAAA' | 'MX' | 'CNAME' | 'TXT' | 'NS' | 'PTR' | 'SOA' | 'SRV' | 'OTHER'

export const RR_TYPES: RRType[] = ['A', 'AAAA', 'MX', 'CNAME', 'TXT', 'NS', 'PTR', 'SOA', 'SRV', 'OTHER']

export type ResponseSource = 'ZONE' | 'CACHE' | 'RECURSIVE_RESOLVER'

export const RESPONSE_SOURCES: ResponseSource[] = ['ZONE', 'CACHE', 'RECURSIVE_RESOLVER']

export type Protocol = 'UDP' | 'TCP'

const RRTYPE_NAME_BY_NUMBER: Record<number, RRType> = {
  1: 'A', 2: 'NS', 5: 'CNAME', 6: 'SOA', 12: 'PTR', 15: 'MX', 16: 'TXT', 28: 'AAAA', 33: 'SRV',
}

export function rrTypeName(qtype: number): RRType {
  return RRTYPE_NAME_BY_NUMBER[qtype] ?? 'OTHER'
}

// 'NO_RESPONSE' is a frontend-only pseudo-code for rows where responded_at / rcode is null
// (the server never answered — e.g. a timeout), so it can still show up in rcode breakdowns.
export type DNSRcode =
  | 'NOERROR'
  | 'FORMERR'
  | 'SERVFAIL'
  | 'NXDOMAIN'
  | 'NOTIMP'
  | 'REFUSED'
  | 'OTHER'
  | 'NO_RESPONSE'

export const DNS_RCODES: DNSRcode[] = [
  'NOERROR', 'FORMERR', 'SERVFAIL', 'NXDOMAIN', 'NOTIMP', 'REFUSED', 'OTHER', 'NO_RESPONSE',
]

const RCODE_NAME_BY_NUMBER: Record<number, DNSRcode> = {
  0: 'NOERROR',
  1: 'FORMERR',
  2: 'SERVFAIL',
  3: 'NXDOMAIN',
  4: 'NOTIMP',
  5: 'REFUSED',
}

export function rcodeName(rcode: number | null): DNSRcode {
  if (rcode === null) return 'NO_RESPONSE'
  return RCODE_NAME_BY_NUMBER[rcode] ?? 'OTHER'
}
