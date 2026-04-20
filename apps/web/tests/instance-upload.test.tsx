import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { InstanceUpload } from "~/components/instance-upload";
import { exampleToy01 } from "~/lib/examples";

async function switchToPasteTab() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("tab", { name: /Paste JSON/i }));
  return user;
}

describe("InstanceUpload", () => {
  it("validates pasted JSON and calls onSubmit for a valid instance", async () => {
    const onSubmit = vi.fn();
    render(<InstanceUpload onSubmit={onSubmit} />);
    const user = await switchToPasteTab();

    const textarea =
      await screen.findByLabelText<HTMLTextAreaElement>("Instance JSON");
    await user.clear(textarea);
    await user.click(textarea);
    await user.paste(JSON.stringify(exampleToy01));
    await user.click(screen.getByRole("button", { name: /Submit JSON/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1);
    });
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ id: "toy-01" });
  });

  it("shows a schema validation error list for invalid JSON", async () => {
    const onSubmit = vi.fn();
    render(<InstanceUpload onSubmit={onSubmit} />);
    const user = await switchToPasteTab();

    const textarea =
      await screen.findByLabelText<HTMLTextAreaElement>("Instance JSON");
    await user.click(textarea);
    await user.paste(JSON.stringify({ id: "" }));
    await user.click(screen.getByRole("button", { name: /Submit JSON/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/Schema validation failed/i),
      ).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejects non-JSON input with a parser error", async () => {
    const onSubmit = vi.fn();
    render(<InstanceUpload onSubmit={onSubmit} />);
    const user = await switchToPasteTab();

    const textarea =
      await screen.findByLabelText<HTMLTextAreaElement>("Instance JSON");
    await user.click(textarea);
    await user.paste("not json!");
    await user.click(screen.getByRole("button", { name: /Submit JSON/i }));

    await waitFor(() => {
      expect(screen.getByText(/invalid JSON/i)).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
