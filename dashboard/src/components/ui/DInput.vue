<script setup lang="ts">
defineProps<{
  modelValue?: string
  placeholder?: string
  type?: string
}>()
defineEmits<{ 'update:modelValue': [v: string] }>()
</script>

<template>
  <input
    :value="modelValue"
    :placeholder="placeholder"
    :type="type ?? 'text'"
    class="h-9 w-full rounded-lg border border-neutral-200 bg-white px-3 text-sm text-neutral-900 placeholder:text-neutral-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100 dark:placeholder:text-neutral-500"
    @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>
