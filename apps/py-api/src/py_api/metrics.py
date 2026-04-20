"""Prometheus metrics for py-api.

Uses a dedicated ``CollectorRegistry`` so tests can inspect state without
leaking into the process-wide default registry.
"""

from __future__ import annotations

from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram


class Metrics:
    """Typed container around the Prometheus collectors."""

    def __init__(self, registry: CollectorRegistry | None = None) -> None:
        self.registry = registry or CollectorRegistry()
        self.solves = Counter(
            "nsp_solve_total",
            "Total number of solve operations by terminal status.",
            labelnames=("status",),
            registry=self.registry,
        )
        self.instances = Counter(
            "nsp_instances_total",
            "Total number of instances stored (cumulative uploads + seeds).",
            registry=self.registry,
        )
        self.solve_seconds = Histogram(
            "nsp_solve_seconds",
            "Wall-clock seconds per solve.",
            buckets=(0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0),
            registry=self.registry,
        )
        self.objective = Histogram(
            "nsp_solve_objective",
            "Objective value reported by solve (weighted-sum model).",
            buckets=(0, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10_000),
            registry=self.registry,
        )
        self.active_solves = Gauge(
            "nsp_solve_active",
            "Number of solves currently in the running state.",
            registry=self.registry,
        )
