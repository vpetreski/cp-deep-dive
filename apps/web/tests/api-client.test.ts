import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";

import { makeApiClient, NspApiError } from "~/lib/api";
import { exampleToy01 } from "~/lib/examples";
import type { SolveAccepted, SolveResponse } from "~/lib/types";

const BASE = "http://localhost:8000";

const server = setupServer(
  http.get(`${BASE}/health`, () =>
    HttpResponse.json({ status: "ok", service: "py-api" }),
  ),
  http.get(`${BASE}/version`, () =>
    HttpResponse.json({
      version: "1.0.0",
      ortools: "9.11.4210",
      runtime: "python 3.12.5",
      service: "py-api",
    }),
  ),
  http.post(`${BASE}/instances`, async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json(body, { status: 201 });
  }),
  http.post(`${BASE}/solve`, () =>
    HttpResponse.json({ jobId: "job-abc" } satisfies SolveAccepted, {
      status: 202,
    }),
  ),
  http.get(`${BASE}/solution/:jobId`, ({ params }) =>
    HttpResponse.json({
      jobId: params.jobId as string,
      status: "optimal",
      objective: 42,
      bestBound: 42,
      gap: 0,
      solveTimeSeconds: 1.2,
      schedule: {
        instanceId: exampleToy01.id,
        assignments: [{ nurseId: "N1", day: 0, shiftId: "D" }],
        violations: [],
      },
    } satisfies SolveResponse),
  ),
  http.get(`${BASE}/instances/404`, () =>
    HttpResponse.json(
      { code: "not_found", message: "Instance 404 not found." },
      { status: 404 },
    ),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("api client (msw integration)", () => {
  it("performs the upload → solve → view flow", async () => {
    const api = makeApiClient(BASE);

    // 1. Create instance
    const created = await api.createInstance(exampleToy01);
    expect(created.id).toBe(exampleToy01.id);

    // 2. Start solve
    const accepted = await api.postSolve({ instance: exampleToy01 });
    expect(accepted.jobId).toBe("job-abc");

    // 3. Fetch solution
    const solution = await api.getSolution(accepted.jobId);
    expect(solution.status).toBe("optimal");
    expect(solution.schedule?.assignments).toHaveLength(1);
  });

  it("builds the SSE stream URL", () => {
    const api = makeApiClient(BASE);
    expect(api.streamUrl("job-abc")).toBe(
      `${BASE}/solutions/job-abc/stream`,
    );
  });

  it("raises a typed NspApiError for 4xx responses", async () => {
    const api = makeApiClient(BASE);
    await expect(api.getInstance("404")).rejects.toBeInstanceOf(NspApiError);
    try {
      await api.getInstance("404");
    } catch (err) {
      expect(err).toBeInstanceOf(NspApiError);
      const e = err as NspApiError;
      expect(e.status).toBe(404);
      expect(e.code).toBe("not_found");
    }
  });

  it("reads health and version endpoints", async () => {
    const api = makeApiClient(BASE);
    const health = await api.getHealth();
    expect(health.status).toBe("ok");
    const v = await api.getVersion();
    expect(v.version).toBe("1.0.0");
  });
});
