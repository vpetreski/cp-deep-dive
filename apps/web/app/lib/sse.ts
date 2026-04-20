import { useEffect, useRef, useState } from "react";
import type { SolveResponse } from "./types";

export type SseState = "idle" | "connecting" | "open" | "closed" | "error";

export interface SseResult {
  state: SseState;
  latest?: SolveResponse;
  events: SolveResponse[];
  error?: string;
  close: () => void;
}

/**
 * Subscribe to a Server-Sent Events stream of SolveResponse payloads.
 *
 * The backend emits events named `solution` and (on terminal status) closes
 * the stream. When jobId or url changes we open a fresh connection.
 *
 * `events` keeps the full history (used by the objective-over-time chart).
 */
export function useSolveStream(
  url: string | null,
  options: { enabled?: boolean; maxEvents?: number } = {},
): SseResult {
  const { enabled = true, maxEvents = 500 } = options;
  const [state, setState] = useState<SseState>("idle");
  const [latest, setLatest] = useState<SolveResponse | undefined>();
  const [events, setEvents] = useState<SolveResponse[]>([]);
  const [error, setError] = useState<string | undefined>();
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled || !url) return;
    if (typeof window === "undefined" || typeof EventSource === "undefined") {
      return;
    }
    setState("connecting");
    setError(undefined);

    const src = new EventSource(url);
    sourceRef.current = src;

    const onOpen = () => setState("open");

    const onMessage = (ev: MessageEvent<string>) => {
      try {
        const data = JSON.parse(ev.data) as SolveResponse;
        setLatest(data);
        setEvents((prev) => {
          const next = [...prev, data];
          return next.length > maxEvents
            ? next.slice(next.length - maxEvents)
            : next;
        });
        // Terminal statuses close the server side — also close client.
        if (
          data.status &&
          data.status !== "pending" &&
          data.status !== "running"
        ) {
          src.close();
          setState("closed");
        }
      } catch (err) {
        setError(`Failed to parse SSE payload: ${String(err)}`);
      }
    };

    const onError = () => {
      // EventSource auto-retries; mark error but don't forcibly close.
      setError("Connection error while streaming solve updates.");
      setState("error");
    };

    src.addEventListener("open", onOpen);
    src.addEventListener("solution", onMessage as EventListener);
    src.addEventListener("message", onMessage);
    src.addEventListener("error", onError);

    return () => {
      src.removeEventListener("open", onOpen);
      src.removeEventListener("solution", onMessage as EventListener);
      src.removeEventListener("message", onMessage);
      src.removeEventListener("error", onError);
      src.close();
      sourceRef.current = null;
      setState("closed");
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, enabled]);

  return {
    state,
    latest,
    events,
    error,
    close: () => {
      sourceRef.current?.close();
      setState("closed");
    },
  };
}
