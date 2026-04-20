import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { BackendProvider, useBackend } from "~/lib/backend";
import { BackendSwitch } from "~/components/backend-switch";

function Probe() {
  const { backend, baseUrl } = useBackend();
  return (
    <div>
      <span data-testid="backend">{backend}</span>
      <span data-testid="url">{baseUrl}</span>
    </div>
  );
}

describe("BackendSwitch", () => {
  it("toggles between python and kotlin and updates the probed base URL", () => {
    render(
      <BackendProvider>
        <BackendSwitch />
        <Probe />
      </BackendProvider>,
    );

    expect(screen.getByTestId("backend")).toHaveTextContent("python");
    expect(screen.getByTestId("url")).toHaveTextContent(
      "http://localhost:8000",
    );

    fireEvent.click(screen.getByRole("radio", { name: /Kotlin/i }));

    expect(screen.getByTestId("backend")).toHaveTextContent("kotlin");
    expect(screen.getByTestId("url")).toHaveTextContent(
      "http://localhost:8080",
    );
  });
});
