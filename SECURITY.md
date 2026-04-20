# Security Policy

## Supported versions

`cp-deep-dive` is a learning repository plus a set of first-class artifacts:

| Artifact | Location | Supported versions |
|---|---|---|
| `cpsat-kt` Kotlin DSL | `libs/cpsat-kt/` | latest `main` |
| `nsp-app` backends + web | `apps/py-api`, `apps/kt-api`, `apps/web` | latest `main` |
| Chapter example code | `apps/py-cp-sat/`, `apps/kt-cp-sat/` | latest `main` |

Released artifacts are not yet published. When they are, this table will list
supported minor versions.

## Reporting a vulnerability

Please do **not** open a public GitHub issue for security problems.

Email a private report to:

> **vanja@petreski.co**

Include, as far as you can:

- A description of the issue and its impact
- The affected artifact and version / commit SHA
- A minimal reproducer (sample input, request, or snippet)
- Any suggested remediation

We aim to:

- Acknowledge receipt within **three working days**
- Provide an initial assessment within **ten working days**
- Coordinate a fix and disclosure timeline together — the default window is
  **90 days** from report to public disclosure, extendable by mutual agreement

## Scope

In scope for this policy:

- Logic bugs in `cpsat-kt` that could be exploited by a malicious solver input
- Vulnerabilities in the NSP app backends (`py-api`, `kt-api`) that affect
  anyone running the default development configuration (authentication,
  injection, denial-of-service, data exposure)
- Vulnerabilities in the web frontend (XSS, CSRF, secret leakage) when served
  on its documented dev ports

Out of scope:

- Issues that require a malicious maintainer or compromised developer
  workstation to exploit
- Denial-of-service purely by submitting very large CP-SAT instances — CP-SAT
  is a heavy solver and large inputs are expected to be slow; wrap it behind
  your own rate limits in production
- Missing hardening features (CSP, HSTS, TLS, auth) on the local dev server —
  the default configuration is explicitly for local learning, not production

## Disclosure

Once a fix is released, the reporter is credited in `CHANGELOG.md` unless they
request otherwise. There is no monetary bounty.

## PGP

If you prefer encrypted mail, ask for the current key fingerprint in your
initial message.
