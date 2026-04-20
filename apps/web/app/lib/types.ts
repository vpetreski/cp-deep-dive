// Wire-format types mirroring apps/shared/schemas/*.json.
// Keep in sync with apps/shared/openapi.yaml.

export type NspSource = "toy" | "nsplib" | "inrc1" | "inrc2" | "custom";

export interface Shift {
  id: string;
  label: string;
  startMinutes?: number;
  durationMinutes?: number;
  skill?: string;
}

export interface Preference {
  day: number;
  shiftId: string;
  weight: number;
}

export interface Nurse {
  id: string;
  name?: string;
  skills?: string[];
  maxShiftsPerWeek?: number;
  minShiftsPerWeek?: number;
  maxConsecutiveWorkingDays?: number;
  preferences?: Preference[];
  unavailable?: number[];
}

export interface CoverageRequirement {
  day: number;
  shiftId: string;
  required: number;
  skill?: string;
}

export interface NspInstance {
  id: string;
  name?: string;
  source?: NspSource;
  horizonDays: number;
  shifts: Shift[];
  nurses: Nurse[];
  coverage: CoverageRequirement[];
  forbiddenTransitions?: [string, string][];
  metadata?: Record<string, unknown>;
}

export interface InstanceSummary {
  id: string;
  name?: string;
  source?: NspSource;
  horizonDays: number;
  nurseCount: number;
  shiftCount?: number;
  coverageSlots?: number;
  createdAt?: string;
}

export interface InstancesPage {
  items: InstanceSummary[];
  nextCursor?: string;
}

export interface SolverParams {
  maxTimeSeconds?: number;
  numSearchWorkers?: number;
  randomSeed?: number;
  linearizationLevel?: number;
  enableHints?: boolean;
  objectiveWeights?: ObjectiveWeights;
}

export interface ObjectiveWeights {
  SC1?: number;
  SC2?: number;
  SC3?: number;
  SC4?: number;
  SC5?: number;
}

export interface SolveRequest {
  instance: NspInstance;
  params?: SolverParams;
}

export interface SolveAccepted {
  jobId: string;
}

export type SolveStatus =
  | "pending"
  | "running"
  | "feasible"
  | "optimal"
  | "infeasible"
  | "unknown"
  | "modelInvalid"
  | "cancelled"
  | "error";

export interface Assignment {
  nurseId: string;
  day: number;
  shiftId?: string | null;
}

export interface Violation {
  code: string;
  message: string;
  severity?: "hard" | "soft";
  nurseId?: string;
  day?: number;
  penalty?: number;
}

export interface Schedule {
  instanceId: string;
  generatedAt?: string;
  assignments: Assignment[];
  violations?: Violation[];
}

export interface SolveResponse {
  jobId: string;
  status: SolveStatus;
  schedule?: Schedule;
  objective?: number;
  bestBound?: number;
  gap?: number;
  solveTimeSeconds?: number;
  error?: string;
}

export interface HealthResponse {
  status: "ok" | "degraded";
  service: string;
}

export interface VersionResponse {
  version: string;
  ortools: string;
  runtime?: string;
  service?: string;
}

export interface ApiError {
  code?: string;
  message: string;
  details?: Record<string, unknown>;
}

// UI-only types
export interface ObjectivePoint {
  t: number; // seconds
  objective: number;
  bestBound?: number;
}
