import { useState, useMemo } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeftIcon, ChevronRightIcon, UploadIcon } from "lucide-react";

import type { Route } from "./+types/instances._index";
import { AppShell } from "~/components/app-shell";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import { Input } from "~/components/ui/input";
import { Skeleton } from "~/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "~/components/ui/table";
import { EmptyState } from "~/components/empty-state";
import { ErrorDisplay } from "~/components/error-display";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import { relativeTime } from "~/lib/format";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "Instances — NSP Scheduler" },
    {
      name: "description",
      content: "Uploaded Nurse Scheduling Problem instances.",
    },
  ];
}

const PAGE_SIZE = 20;

export default function InstancesList() {
  const api = useApi();
  const { backend, baseUrl } = useBackend();
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const [search, setSearch] = useState("");

  const cursor = cursorStack[cursorStack.length - 1];
  const query = useQuery({
    queryKey: queryKeys.instances(backend, baseUrl, {
      limit: PAGE_SIZE,
      cursor,
    }),
    queryFn: ({ signal }) =>
      api.listInstances({ limit: PAGE_SIZE, cursor }, signal),
    enabled: typeof window !== "undefined",
  });

  const filtered = useMemo(() => {
    const items = query.data?.items ?? [];
    if (!search) return items;
    const q = search.toLowerCase();
    return items.filter(
      (i) =>
        i.id.toLowerCase().includes(q) ||
        (i.name ?? "").toLowerCase().includes(q),
    );
  }, [query.data, search]);

  return (
    <AppShell>
      <section className="flex flex-col gap-6">
        <header className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Instances</h1>
            <p className="text-sm text-muted-foreground">
              All Nurse Scheduling Problem instances available on the{" "}
              <span className="font-mono">{backend}</span> backend.
            </p>
          </div>
          <Button asChild>
            <Link to="/">
              <UploadIcon />
              Upload new
            </Link>
          </Button>
        </header>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <Input
            data-shortcut="instance-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter by id or name (press /)"
            className="max-w-sm"
            aria-label="Filter instances"
          />
          <span className="text-xs text-muted-foreground">
            Page {cursorStack.length + 1}
          </span>
        </div>

        {query.isPending ? (
          <LoadingTable />
        ) : query.isError ? (
          <ErrorDisplay
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            title={
              search
                ? "No matching instances"
                : "No instances yet"
            }
            description={
              search
                ? "Clear the filter to see all instances, or upload a new one."
                : "Upload a ward file on the home page or pick a bundled example to populate this list."
            }
            action={
              <Button asChild>
                <Link to="/">
                  <UploadIcon />
                  Upload an instance
                </Link>
              </Button>
            }
          />
        ) : (
          <div className="rounded-xl border border-border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Id</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead className="text-right">Horizon</TableHead>
                  <TableHead className="text-right">Nurses</TableHead>
                  <TableHead className="text-right">Slots</TableHead>
                  <TableHead className="text-right">Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((i) => (
                  <TableRow
                    key={i.id}
                    className="cursor-pointer"
                  >
                    <TableCell className="font-mono text-xs">
                      <Link
                        to={`/instances/${encodeURIComponent(i.id)}`}
                        className="hover:underline"
                      >
                        {i.id}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Link
                        to={`/instances/${encodeURIComponent(i.id)}`}
                        className="hover:underline"
                      >
                        {i.name ?? "—"}
                      </Link>
                    </TableCell>
                    <TableCell>
                      {i.source ? (
                        <Badge variant="outline" className="font-mono text-[10px]">
                          {i.source}
                        </Badge>
                      ) : (
                        "—"
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {i.horizonDays}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {i.nurseCount}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {i.coverageSlots ?? "—"}
                    </TableCell>
                    <TableCell className="text-right text-xs text-muted-foreground">
                      {relativeTime(i.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <Button
            variant="outline"
            size="sm"
            disabled={cursorStack.length === 0}
            onClick={() => setCursorStack((s) => s.slice(0, -1))}
          >
            <ChevronLeftIcon />
            Prev
          </Button>
          <span className="text-xs text-muted-foreground">
            {filtered.length} shown
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={!query.data?.nextCursor}
            onClick={() => {
              if (query.data?.nextCursor) {
                setCursorStack((s) => [...s, query.data!.nextCursor!]);
              }
            }}
          >
            Next
            <ChevronRightIcon />
          </Button>
        </div>
      </section>
    </AppShell>
  );
}

function LoadingTable() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
    </div>
  );
}
