<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'outline' | 'ghost'
  size?: 'sm' | 'md'
  disabled?: boolean
}>()
</script>

<template>
  <button
    :disabled="disabled"
    :class="[
      'inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:pointer-events-none disabled:opacity-50',
      {
        'bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800':                 !$props.variant || $props.variant === 'primary',
        'border border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300 dark:hover:bg-neutral-800': $props.variant === 'outline',
        'text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-neutral-100': $props.variant === 'ghost',
      },
      {
        'h-8 px-3 text-xs':  $props.size === 'sm',
        'h-9 px-4 text-sm':  !$props.size || $props.size === 'md',
      },
    ]"
  >
    <slot />
  </button>
</template>
