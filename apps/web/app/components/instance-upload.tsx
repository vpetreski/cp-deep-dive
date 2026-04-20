import { useCallback, useRef, useState } from "react";
import {
  FileUpIcon,
  FilePlusIcon,
  Loader2Icon,
  XIcon,
} from "lucide-react";
import { Button } from "~/components/ui/button";
import { Textarea } from "~/components/ui/textarea";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "~/components/ui/tabs";
import { cn } from "~/lib/utils";
import { parseInstanceJson, type ValidationError } from "~/lib/schema";
import type { NspInstance } from "~/lib/types";

export interface InstanceUploadProps {
  onSubmit: (instance: NspInstance) => void | Promise<void>;
  pending?: boolean;
}

/**
 * Drag-and-drop OR paste JSON. Validates against the wire-format schema
 * client-side before calling onSubmit.
 */
export function InstanceUpload({ onSubmit, pending }: InstanceUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [mode, setMode] = useState<"upload" | "paste">("upload");
  const [errors, setErrors] = useState<ValidationError[]>([]);
  const [fileName, setFileName] = useState<string | null>(null);
  const [paste, setPaste] = useState("");
  const [dragActive, setDragActive] = useState(false);

  const clearErrors = () => setErrors([]);

  const handleInstance = useCallback(
    async (text: string, sourceLabel?: string) => {
      const result = parseInstanceJson(text);
      if (!result.valid || !result.data) {
        setErrors(result.errors);
        return;
      }
      clearErrors();
      if (sourceLabel) setFileName(sourceLabel);
      await onSubmit(result.data);
    },
    [onSubmit],
  );

  const onFiles = useCallback(
    async (files: FileList | null) => {
      if (!files || files.length === 0) return;
      const file = files[0];
      if (!file.name.toLowerCase().endsWith(".json")) {
        setErrors([{ path: "/", message: "File must have a .json extension." }]);
        return;
      }
      const text = await file.text();
      await handleInstance(text, file.name);
    },
    [handleInstance],
  );

  return (
    <div className="flex flex-col gap-4">
      <Tabs
        value={mode}
        onValueChange={(v) => {
          clearErrors();
          setMode(v as typeof mode);
        }}
      >
        <TabsList className="w-full">
          <TabsTrigger value="upload">Upload file</TabsTrigger>
          <TabsTrigger value="paste">Paste JSON</TabsTrigger>
        </TabsList>
        <TabsContent value="upload">
          <div
            className={cn(
              "group flex min-h-44 flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-border bg-muted/30 px-6 py-10 text-center transition-colors",
              dragActive && "border-primary/50 bg-primary/5",
            )}
            onDragEnter={(e) => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragOver={(e) => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragLeave={() => setDragActive(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragActive(false);
              void onFiles(e.dataTransfer.files);
            }}
          >
            <FileUpIcon className="size-8 text-muted-foreground" />
            <div className="flex flex-col gap-1">
              <p className="text-sm font-medium">
                Drop a JSON instance here, or click to browse.
              </p>
              <p className="text-xs text-muted-foreground">
                Validated against the NSP instance schema before upload.
              </p>
            </div>
            <Button
              type="button"
              disabled={pending}
              onClick={() => fileInputRef.current?.click()}
            >
              {pending ? (
                <>
                  <Loader2Icon className="animate-spin" />
                  Uploading…
                </>
              ) : (
                <>
                  <FilePlusIcon />
                  Browse files…
                </>
              )}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".json,application/json"
              className="hidden"
              onChange={(e) => {
                void onFiles(e.target.files);
                // Reset input value so the same file can be re-picked after fixing.
                e.target.value = "";
              }}
            />
            {fileName && (
              <p className="text-xs text-muted-foreground">
                Selected: <span className="font-mono">{fileName}</span>
              </p>
            )}
          </div>
        </TabsContent>
        <TabsContent value="paste">
          <div className="flex flex-col gap-3">
            <Textarea
              value={paste}
              onChange={(e) => setPaste(e.target.value)}
              placeholder='Paste a JSON body, e.g. {"id":"ward-a","horizonDays":7,...}'
              rows={12}
              className="font-mono text-xs"
              aria-label="Instance JSON"
            />
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setPaste("");
                  clearErrors();
                }}
              >
                Clear
              </Button>
              <Button
                type="button"
                disabled={pending || paste.trim().length === 0}
                onClick={() => void handleInstance(paste, "pasted.json")}
              >
                {pending ? (
                  <>
                    <Loader2Icon className="animate-spin" />
                    Uploading…
                  </>
                ) : (
                  <>Submit JSON</>
                )}
              </Button>
            </div>
          </div>
        </TabsContent>
      </Tabs>

      {errors.length > 0 && (
        <div className="flex flex-col gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
          <div className="flex items-center gap-2 font-medium text-destructive">
            <XIcon className="size-4" />
            Schema validation failed
          </div>
          <ul className="flex list-disc flex-col gap-1 pl-5 text-xs text-destructive/90">
            {errors.slice(0, 10).map((err, i) => (
              <li key={i}>
                <span className="font-mono">
                  {err.path || "/"}
                </span>{" "}
                — {err.message}
              </li>
            ))}
            {errors.length > 10 && (
              <li className="text-muted-foreground">
                … and {errors.length - 10} more.
              </li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
