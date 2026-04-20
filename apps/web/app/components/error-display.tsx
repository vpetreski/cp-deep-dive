import { TriangleAlertIcon } from "lucide-react";
import { Button } from "~/components/ui/button";
import { NspApiError } from "~/lib/api";

export function ErrorDisplay({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  const { title, message, detail } = describeError(error);
  return (
    <div className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm">
      <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />
      <div className="flex-1 space-y-1">
        <p className="font-medium text-destructive">{title}</p>
        <p className="text-destructive/90">{message}</p>
        {detail && (
          <p className="text-xs text-destructive/70 font-mono">{detail}</p>
        )}
        {onRetry && (
          <div className="pt-2">
            <Button size="sm" variant="outline" onClick={onRetry}>
              Retry
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

function describeError(error: unknown): {
  title: string;
  message: string;
  detail?: string;
} {
  if (error instanceof NspApiError) {
    return {
      title:
        error.status >= 500
          ? "Backend error"
          : error.status === 404
            ? "Not found"
            : "Request failed",
      message: error.message,
      detail: error.code ? `Code: ${error.code}` : undefined,
    };
  }
  if (error instanceof Error) {
    return {
      title: "Unexpected error",
      message: error.message,
    };
  }
  return {
    title: "Unexpected error",
    message: String(error),
  };
}
