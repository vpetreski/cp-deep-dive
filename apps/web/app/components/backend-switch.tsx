import { Button } from "~/components/ui/button";
import { useBackend, type Backend } from "~/lib/backend";
import { cn } from "~/lib/utils";

const OPTIONS: { id: Backend; label: string; hint: string }[] = [
  { id: "python", label: "Python", hint: "FastAPI" },
  { id: "kotlin", label: "Kotlin", hint: "Ktor 3" },
];

export function BackendSwitch({ className }: { className?: string }) {
  const { backend, setBackend } = useBackend();

  return (
    <div
      className={cn(
        "inline-flex items-center gap-1 rounded-lg border border-border bg-background p-1 text-xs",
        className,
      )}
      data-slot="button-group"
      role="radiogroup"
      aria-label="Active backend"
    >
      {OPTIONS.map((opt) => {
        const active = backend === opt.id;
        return (
          <Button
            key={opt.id}
            type="button"
            role="radio"
            aria-checked={active}
            variant={active ? "default" : "ghost"}
            size="sm"
            onClick={() => setBackend(opt.id)}
          >
            <span className="font-medium">{opt.label}</span>
            <span className="text-muted-foreground">{opt.hint}</span>
          </Button>
        );
      })}
    </div>
  );
}
