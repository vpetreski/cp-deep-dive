import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/_index.tsx"),
  route("instances", "routes/instances._index.tsx"),
  route("instances/:instanceId", "routes/instances.$instanceId._index.tsx"),
  route("instances/:instanceId/solve", "routes/instances.$instanceId.solve.tsx"),
  route("jobs/:jobId", "routes/jobs.$jobId._index.tsx"),
  route("jobs/:jobId/schedule", "routes/jobs.$jobId.schedule.tsx"),
  route("jobs/:jobId/infeasibility", "routes/jobs.$jobId.infeasibility.tsx"),
  route("about", "routes/about.tsx"),
  route("health-check", "routes/health-check.tsx"),
] satisfies RouteConfig;
