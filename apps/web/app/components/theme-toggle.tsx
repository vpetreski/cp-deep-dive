import { MoonIcon, SunIcon } from "lucide-react";
import { Button } from "~/components/ui/button";
import { useTheme } from "~/lib/theme";

export function ThemeToggle() {
  const { resolvedTheme, toggle } = useTheme();
  const label =
    resolvedTheme === "dark"
      ? "Switch to light theme"
      : "Switch to dark theme";
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      onClick={toggle}
      aria-label={label}
      title={label}
    >
      {resolvedTheme === "dark" ? <SunIcon /> : <MoonIcon />}
    </Button>
  );
}
