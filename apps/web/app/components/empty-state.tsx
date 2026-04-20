import type { ReactNode } from "react";
import { cn } from "~/lib/utils";

export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-muted/20 px-6 py-12 text-center",
        className,
      )}
    >
      <h2 className="text-lg font-medium">{title}</h2>
      {description && (
        <p className="max-w-md text-sm text-muted-foreground">
          {description}
        </p>
      )}
      {action}
    </div>
  );
}
