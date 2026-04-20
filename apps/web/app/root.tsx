import { useState } from "react";
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { Tooltip as TooltipPrimitive } from "radix-ui";

import type { Route } from "./+types/root";
import "./app.css";
import { BackendProvider } from "./lib/backend";
import { ThemeProvider } from "./lib/theme";
import { KeyboardShortcutsProvider } from "./lib/keyboard";
import { makeQueryClient } from "./lib/query-client";
import { Toaster } from "./components/ui/sonner";
import { AppShell } from "./components/app-shell";
import { Button } from "./components/ui/button";
import { Link } from "react-router";

export const links: Route.LinksFunction = () => [
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&display=swap",
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        {/* Apply stored theme before first paint to avoid a flash. */}
        <script
          // eslint-disable-next-line react/no-danger
          dangerouslySetInnerHTML={{
            __html: `
(function(){
  try {
    var t = localStorage.getItem('cp-deep-dive:theme') || 'system';
    var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    var resolved = (t === 'system') ? (prefersDark ? 'dark' : 'light') : t;
    if (resolved === 'dark') document.documentElement.classList.add('dark');
    document.documentElement.setAttribute('data-theme', resolved);
  } catch (e) {}
})();
`,
          }}
        />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  // One client per browser session; kept stable with useState.
  const [queryClient] = useState(() => makeQueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <BackendProvider>
          <TooltipPrimitive.Provider delayDuration={200}>
            <KeyboardShortcutsProvider>
              <Outlet />
              <Toaster position="top-right" richColors closeButton />
            </KeyboardShortcutsProvider>
          </TooltipPrimitive.Provider>
        </BackendProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let title = "Something went wrong";
  let details = "An unexpected error occurred.";
  let status: number | undefined;
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    status = error.status;
    title = error.status === 404 ? "Page not found" : `Error ${error.status}`;
    details =
      error.status === 404
        ? "The requested page could not be found."
        : error.statusText || error.data || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <AppShell>
      <section className="mx-auto flex max-w-xl flex-col gap-4 py-16 text-center">
        <p className="text-sm font-medium uppercase tracking-widest text-muted-foreground">
          {status ? status : "Error"}
        </p>
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="text-muted-foreground">{details}</p>
        <div className="flex justify-center gap-3 pt-2">
          <Button asChild>
            <Link to="/">Back home</Link>
          </Button>
          <Button asChild variant="outline">
            <Link to="/instances">Instances</Link>
          </Button>
        </div>
        {stack && (
          <pre className="mt-8 max-h-96 overflow-auto rounded-md border border-border bg-muted p-4 text-left text-xs">
            <code>{stack}</code>
          </pre>
        )}
      </section>
    </AppShell>
  );
}
