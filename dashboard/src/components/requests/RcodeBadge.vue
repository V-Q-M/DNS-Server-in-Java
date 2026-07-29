<script setup lang="ts">
import Badge from '@/components/ui/Badge.vue'
import type { DNSRcode } from '@/types/dns'

defineProps<{ rcode: DNSRcode }>()

const variantMap: Record<DNSRcode, 'success' | 'info' | 'warning' | 'danger' | 'purple' | 'muted'> = {
  NOERROR:     'success',
  NXDOMAIN:    'warning',
  SERVFAIL:    'danger',
  REFUSED:     'danger',
  FORMERR:     'warning',
  NOTIMP:      'purple',
  OTHER:       'muted',
  NO_RESPONSE: 'muted',
}

const labelMap: Record<DNSRcode, string> = {
  NOERROR:     'NoError',
  NXDOMAIN:    'NXDomain',
  SERVFAIL:    'ServFail',
  REFUSED:     'Refused',
  FORMERR:     'FormErr',
  NOTIMP:      'NotImp',
  OTHER:       'Other',
  NO_RESPONSE: 'No Response',
}
</script>

<template>
  <Badge :variant="variantMap[rcode]">{{ labelMap[rcode] }}</Badge>
</template>
