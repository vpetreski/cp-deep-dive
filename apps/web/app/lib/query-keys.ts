import type { Backend } from "./backend";

/**
 * Centralized query-key factory. Every read is namespaced by backend so
 * switching Python ↔ Kotlin doesn't reuse a stale cache.
 */
export const queryKeys = {
  health: (backend: Backend, baseUrl: string) =>
    ["health", backend, baseUrl] as const,
  version: (backend: Backend, baseUrl: string) =>
    ["version", backend, baseUrl] as const,
  instances: (
    backend: Backend,
    baseUrl: string,
    params: { limit?: number; cursor?: string },
  ) => ["instances", backend, baseUrl, params] as const,
  instance: (backend: Backend, baseUrl: string, id: string) =>
    ["instance", backend, baseUrl, id] as const,
  solution: (backend: Backend, baseUrl: string, jobId: string) =>
    ["solution", backend, baseUrl, jobId] as const,
};
