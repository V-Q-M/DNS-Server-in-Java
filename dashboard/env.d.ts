/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Base URL of the dashboard-server backend. Leave unset to use relative paths
  // (e.g. behind the dev server's /api proxy, see vite.config.ts).
  readonly VITE_API_BASE_URL?: string
}
