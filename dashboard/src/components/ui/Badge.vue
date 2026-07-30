<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'muted'
}>()
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
      {
        'bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300': !$props.variant || $props.variant === 'default' || $props.variant === 'muted',
        'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400':  $props.variant === 'success',
        'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400': $props.variant === 'warning',
        'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400':    $props.variant === 'danger',
        'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400':  $props.variant === 'info',
        'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-400': $props.variant === 'purple',
      },
    ]"
  >
    <slot />
  </span>
</template>
