import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type Backend = "python" | "kotlin";

interface BackendContextValue {
  backend: Backend;
  setBackend: (backend: Backend) => void;
  baseUrl: string;
}

const BackendContext = createContext<BackendContextValue | null>(null);

const STORAGE_KEY = "cp-deep-dive:backend";

function readStoredBackend(): Backend {
  if (typeof window === "undefined") return "python";
  try {
    const stored = window.localStorage?.getItem(STORAGE_KEY);
    return stored === "kotlin" ? "kotlin" : "python";
  } catch {
    return "python";
  }
}

function writeStoredBackend(next: Backend): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage?.setItem(STORAGE_KEY, next);
  } catch {
    // no-op — private browsing, quota, test envs without storage, etc.
  }
}

export function BackendProvider({ children }: { children: ReactNode }) {
  const [backend, setBackendState] = useState<Backend>("python");

  // Hydrate from localStorage on the client after mount to keep SSR output stable.
  useEffect(() => {
    setBackendState(readStoredBackend());
  }, []);

  const value = useMemo<BackendContextValue>(() => {
    const pyUrl =
      import.meta.env.VITE_PY_API_URL ?? "http://localhost:8000";
    const ktUrl =
      import.meta.env.VITE_KT_API_URL ?? "http://localhost:8080";
    return {
      backend,
      setBackend: (next) => {
        setBackendState(next);
        writeStoredBackend(next);
      },
      baseUrl: backend === "python" ? pyUrl : ktUrl,
    };
  }, [backend]);

  return (
    <BackendContext.Provider value={value}>{children}</BackendContext.Provider>
  );
}

export function useBackend(): BackendContextValue {
  const ctx = useContext(BackendContext);
  if (!ctx) {
    throw new Error("useBackend must be used inside <BackendProvider>");
  }
  return ctx;
}
