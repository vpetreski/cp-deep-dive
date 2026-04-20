import { useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "react-router";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Separator } from "~/components/ui/separator";

const CHORD_WINDOW_MS = 1_200;

function isTypingTarget(t: EventTarget | null): boolean {
  if (!t || !(t instanceof HTMLElement)) return false;
  const tag = t.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return true;
  if (t.isContentEditable) return true;
  return false;
}

/**
 * Global keyboard shortcuts for the NSP app.
 *
 * Implemented shortcuts:
 *   g h   Go home
 *   g i   Go to instances
 *   /     Focus the search input on the instances list (if present)
 *   ?     Open help dialog
 *   Esc   Close help dialog
 *
 * The prefix chord (`g`) is swallowed only while waiting for the second key
 * and times out after CHORD_WINDOW_MS.
 */
export function KeyboardShortcutsProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [helpOpen, setHelpOpen] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") return;
    let chord: "g" | null = null;
    let chordTimer: number | undefined;

    const clearChord = () => {
      chord = null;
      if (chordTimer !== undefined) {
        window.clearTimeout(chordTimer);
        chordTimer = undefined;
      }
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (isTypingTarget(e.target)) return;

      // Help dialog shortcut
      if (e.key === "?" || (e.shiftKey && e.key === "/")) {
        e.preventDefault();
        setHelpOpen(true);
        return;
      }

      // Focus the search input when on the instances page.
      if (e.key === "/" && !chord) {
        const input = document.querySelector<HTMLInputElement>(
          'input[data-shortcut="instance-search"]',
        );
        if (input) {
          e.preventDefault();
          input.focus();
          input.select();
          return;
        }
      }

      // g … chord
      if (chord === "g") {
        clearChord();
        if (e.key === "h") {
          e.preventDefault();
          navigate("/");
        } else if (e.key === "i") {
          e.preventDefault();
          navigate("/instances");
        } else if (e.key === "a") {
          e.preventDefault();
          navigate("/about");
        }
        return;
      }

      if (e.key === "g") {
        chord = "g";
        chordTimer = window.setTimeout(clearChord, CHORD_WINDOW_MS);
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      clearChord();
    };
  }, [navigate]);

  return (
    <>
      {children}
      <ShortcutsHelpDialog open={helpOpen} onOpenChange={setHelpOpen} />
    </>
  );
}

function ShortcutsHelpDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Keyboard shortcuts</DialogTitle>
          <DialogDescription>
            Single-key commands and two-key chords. Shortcuts are ignored
            while typing in an input.
          </DialogDescription>
        </DialogHeader>
        <Separator />
        <ul className="flex flex-col gap-2 text-sm">
          <ShortcutRow keys={["g", "h"]} label="Go to home" />
          <ShortcutRow keys={["g", "i"]} label="Go to instances" />
          <ShortcutRow keys={["g", "a"]} label="Go to about" />
          <ShortcutRow keys={["/"]} label="Focus search (instances list)" />
          <ShortcutRow keys={["?"]} label="Open this help" />
          <ShortcutRow keys={["Esc"]} label="Close dialogs" />
        </ul>
      </DialogContent>
    </Dialog>
  );
}

function ShortcutRow({ keys, label }: { keys: string[]; label: string }) {
  return (
    <li className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="flex items-center gap-1">
        {keys.map((k, i) => (
          <span key={i} className="flex items-center gap-1">
            <kbd className="inline-flex h-6 min-w-6 items-center justify-center rounded-md border border-border bg-muted px-1.5 font-mono text-xs">
              {k}
            </kbd>
            {i < keys.length - 1 && (
              <span className="text-xs text-muted-foreground">then</span>
            )}
          </span>
        ))}
      </span>
    </li>
  );
}
