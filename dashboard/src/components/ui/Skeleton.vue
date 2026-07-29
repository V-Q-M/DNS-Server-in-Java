<script setup lang="ts">
defineProps<{ class?: string }>()
</script>

<template>
  <div :class="['animate-pulse rounded-md bg-neutral-200 dark:bg-neutral-800', $props.class]" />
</template>
