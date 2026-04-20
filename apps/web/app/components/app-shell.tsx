import { Link, NavLink } from "react-router";
import type { ReactNode } from "react";
import { BackendSwitch } from "~/components/backend-switch";
import { ThemeToggle } from "~/components/theme-toggle";
import { cn } from "~/lib/utils";
import { APP_VERSION } from "~/lib/version";

const NAV_ITEMS: { to: string; label: string }[] = [
  { to: "/", label: "Home" },
  { to: "/instances", label: "Instances" },
  { to: "/about", label: "About" },
];

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-background text-foreground flex flex-col">
      <AppHeader />
      <main className="flex-1 mx-auto w-full max-w-6xl px-4 sm:px-6 py-6 sm:py-10">
        {children}
      </main>
      <AppFooter />
    </div>
  );
}

function AppHeader() {
  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-4 px-4 sm:px-6 h-14">
        <Link
          to="/"
          className="flex items-center gap-2 font-heading text-sm font-semibold tracking-tight"
        >
          <span className="inline-flex h-7 w-7 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground">
            NSP
          </span>
          <span className="hidden sm:inline">NSP Scheduler</span>
        </Link>
        <nav className="hidden md:flex items-center gap-1 text-sm">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                cn(
                  "rounded-md px-2.5 py-1.5 text-muted-foreground transition-colors hover:text-foreground hover:bg-muted",
                  isActive && "text-foreground bg-muted",
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <BackendSwitch />
          <ThemeToggle />
        </div>
      </div>
      <MobileNav />
    </header>
  );
}

function MobileNav() {
  return (
    <nav
      className="md:hidden flex items-center gap-1 overflow-x-auto border-t border-border px-3 py-1.5 text-xs"
      aria-label="Primary"
    >
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === "/"}
          className={({ isActive }) =>
            cn(
              "whitespace-nowrap rounded-md px-2 py-1 text-muted-foreground transition-colors hover:text-foreground hover:bg-muted",
              isActive && "text-foreground bg-muted",
            )
          }
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

function AppFooter() {
  return (
    <footer className="mt-8 border-t border-border text-xs text-muted-foreground">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-2 px-4 sm:px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-3">
          <a
            href="/openapi.yaml"
            className="hover:text-foreground transition-colors"
          >
            OpenAPI
          </a>
          <a
            href="https://developers.google.com/optimization/cp/cp_solver"
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground transition-colors"
          >
            CP-SAT docs
          </a>
          <a
            href="https://github.com/"
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground transition-colors"
          >
            GitHub
          </a>
          <Link
            to="/health-check"
            className="hover:text-foreground transition-colors"
          >
            Health
          </Link>
        </div>
        <span>cp-deep-dive · v{APP_VERSION}</span>
      </div>
    </footer>
  );
}
