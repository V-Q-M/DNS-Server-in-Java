<script setup lang="ts">
defineProps<{ class?: string }>()
</script>

<template>
  <div
    :class="[
      'rounded-xl border border-neutral-200 bg-white shadow-sm dark:border-neutral-800 dark:bg-neutral-900',
      $props.class,
    ]"
  >
    <slot />
  </div>
</template>
