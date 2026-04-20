import type {
  HealthResponse,
  InstancesPage,
  NspInstance,
  SolveAccepted,
  SolveRequest,
  SolveResponse,
  VersionResponse,
  ApiError,
} from "./types";

/**
 * Small HTTP client wrapper around fetch. Every call is keyed by baseUrl so
 * that switching backends in BackendProvider invalidates the right queries.
 *
 * The server returns an `Error` envelope ({ code, message, details }) on 4xx/5xx;
 * we rethrow a typed error so `useQuery` / `useMutation` surface it consistently.
 */

export class NspApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly details?: Record<string, unknown>;

  constructor(status: number, payload: ApiError) {
    super(payload.message || `HTTP ${status}`);
    this.name = "NspApiError";
    this.status = status;
    this.code = payload.code;
    this.details = payload.details;
  }
}

async function parseError(res: Response): Promise<NspApiError> {
  let payload: ApiError = { message: `HTTP ${res.status} ${res.statusText}` };
  try {
    const text = await res.text();
    if (text) {
      const parsed: unknown = JSON.parse(text);
      if (parsed && typeof parsed === "object") {
        const p = parsed as Record<string, unknown>;
        payload = {
          message:
            (typeof p.message === "string" && p.message) ||
            payload.message,
          code: typeof p.code === "string" ? p.code : undefined,
          details:
            p.details && typeof p.details === "object"
              ? (p.details as Record<string, unknown>)
              : undefined,
        };
      }
    }
  } catch {
    // non-JSON body — keep fallback message
  }
  return new NspApiError(res.status, payload);
}

function trim(url: string): string {
  return url.replace(/\/$/, "");
}

export interface ApiClient {
  baseUrl: string;
  getHealth(signal?: AbortSignal): Promise<HealthResponse>;
  getVersion(signal?: AbortSignal): Promise<VersionResponse>;
  listInstances(
    params?: { limit?: number; cursor?: string },
    signal?: AbortSignal,
  ): Promise<InstancesPage>;
  getInstance(id: string, signal?: AbortSignal): Promise<NspInstance>;
  createInstance(body: NspInstance, signal?: AbortSignal): Promise<NspInstance>;
  deleteInstance(id: string, signal?: AbortSignal): Promise<void>;
  postSolve(body: SolveRequest, signal?: AbortSignal): Promise<SolveAccepted>;
  getSolution(jobId: string, signal?: AbortSignal): Promise<SolveResponse>;
  cancelSolve(jobId: string, signal?: AbortSignal): Promise<SolveResponse>;
  streamUrl(jobId: string): string;
}

export function makeApiClient(rawBaseUrl: string): ApiClient {
  const baseUrl = trim(rawBaseUrl);

  async function json<T>(
    path: string,
    init: RequestInit = {},
    signal?: AbortSignal,
  ): Promise<T> {
    const res = await fetch(`${baseUrl}${path}`, {
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...(init.headers ?? {}),
      },
      signal,
      ...init,
    });
    if (!res.ok) {
      throw await parseError(res);
    }
    if (res.status === 204) {
      return undefined as T;
    }
    return (await res.json()) as T;
  }

  return {
    baseUrl,
    getHealth: (signal) => json<HealthResponse>("/health", {}, signal),
    getVersion: (signal) => json<VersionResponse>("/version", {}, signal),
    listInstances: (params, signal) => {
      const qs = new URLSearchParams();
      if (params?.limit) qs.set("limit", String(params.limit));
      if (params?.cursor) qs.set("cursor", params.cursor);
      const q = qs.toString();
      return json<InstancesPage>(
        `/instances${q ? `?${q}` : ""}`,
        {},
        signal,
      );
    },
    getInstance: (id, signal) =>
      json<NspInstance>(`/instances/${encodeURIComponent(id)}`, {}, signal),
    createInstance: (body, signal) =>
      json<NspInstance>(
        "/instances",
        { method: "POST", body: JSON.stringify(body) },
        signal,
      ),
    deleteInstance: (id, signal) =>
      json<void>(
        `/instances/${encodeURIComponent(id)}`,
        { method: "DELETE" },
        signal,
      ),
    postSolve: (body, signal) =>
      json<SolveAccepted>(
        "/solve",
        { method: "POST", body: JSON.stringify(body) },
        signal,
      ),
    getSolution: (jobId, signal) =>
      json<SolveResponse>(
        `/solution/${encodeURIComponent(jobId)}`,
        {},
        signal,
      ),
    cancelSolve: (jobId, signal) =>
      json<SolveResponse>(
        `/solve/${encodeURIComponent(jobId)}/cancel`,
        { method: "POST" },
        signal,
      ),
    streamUrl: (jobId) =>
      `${baseUrl}/solutions/${encodeURIComponent(jobId)}/stream`,
  };
}
