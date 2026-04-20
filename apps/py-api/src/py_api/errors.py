"""Error envelope + FastAPI exception handlers.

The spec at specs/nsp-app/06-api-contract.md fixes the shape:

    {"code": "...", "message": "...", "details": {...}}

The canonical error codes (request.malformed, instance.invalid, ...) live in
``ErrorCode``; handlers translate Python exceptions into that shape.
"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Mapping
from typing import Any

from fastapi import FastAPI, Request, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger("py_api.errors")


class ErrorCode:
    """Canonical error codes from specs/nsp-app/06-api-contract.md."""

    REQUEST_MALFORMED = "request.malformed"
    INSTANCE_INVALID = "instance.invalid"
    INSTANCE_TOO_LARGE = "instance.tooLarge"
    INSTANCE_NOT_FOUND = "instance.notFound"
    JOB_NOT_FOUND = "job.notFound"
    JOB_CONFLICT = "job.conflict"
    SOLVER_POOL_FULL = "solver.poolFull"
    SOLVER_MODEL_INVALID = "solver.modelInvalid"
    INTERNAL_UNKNOWN = "internal.unknown"


class ApiError(Exception):
    """Internal exception → ``Error`` envelope."""

    def __init__(
        self,
        *,
        status_code: int,
        code: str,
        message: str,
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.details: dict[str, Any] = dict(details or {})


def error_body(
    *,
    code: str,
    message: str,
    details: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    body: dict[str, Any] = {"code": code, "message": message}
    if details:
        body["details"] = dict(details)
    return body


def error_response(
    *,
    status_code: int,
    code: str,
    message: str,
    details: Mapping[str, Any] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content=error_body(code=code, message=message, details=details),
    )


async def _api_error_handler(request: Request, exc: Exception) -> JSONResponse:
    err = exc if isinstance(exc, ApiError) else ApiError(
        status_code=500,
        code=ErrorCode.INTERNAL_UNKNOWN,
        message="Unhandled error.",
    )
    del request  # not used — the structured logger picks up request_id elsewhere
    return error_response(
        status_code=err.status_code,
        code=err.code,
        message=err.message,
        details=err.details,
    )


async def _http_exception_handler(
    request: Request, exc: Exception
) -> JSONResponse:
    """Translate Starlette HTTPException (including FastAPI's 404/405) into our envelope."""
    assert isinstance(exc, StarletteHTTPException)
    del request
    detail = exc.detail
    # FastAPI-style dict details — pass through.
    if isinstance(detail, dict) and "code" in detail and "message" in detail:
        return JSONResponse(status_code=exc.status_code, content=detail)
    message = str(detail) if detail is not None else ""
    code = {
        status.HTTP_404_NOT_FOUND: "not_found",
        status.HTTP_405_METHOD_NOT_ALLOWED: "method_not_allowed",
        status.HTTP_415_UNSUPPORTED_MEDIA_TYPE: "unsupported_media_type",
    }.get(exc.status_code, "http_error")
    if not message:
        message = f"HTTP {exc.status_code}"
    return error_response(
        status_code=exc.status_code,
        code=code,
        message=message,
    )


async def _validation_handler(
    request: Request, exc: Exception
) -> JSONResponse:
    """FastAPI request-level validation → 400 request.malformed."""
    del request
    assert isinstance(exc, RequestValidationError | ValidationError)
    errors = exc.errors()
    first = errors[0] if errors else {}
    loc = first.get("loc") or ()
    pointer = "/" + "/".join(str(p) for p in loc if p != "body")
    if pointer == "/":
        pointer = ""
    msg = first.get("msg") or "Request body failed validation."
    return error_response(
        status_code=status.HTTP_400_BAD_REQUEST,
        code=ErrorCode.REQUEST_MALFORMED,
        message=msg,
        details={"pointer": pointer, "errors": jsonable_encoder(errors)},
    )


async def _unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
    trace_id = uuid.uuid4().hex
    log.exception("unhandled error trace_id=%s path=%s", trace_id, request.url.path)
    return error_response(
        status_code=500,
        code=ErrorCode.INTERNAL_UNKNOWN,
        message="Unexpected server error.",
        details={"traceId": trace_id},
    )


def install(app: FastAPI) -> None:
    """Attach all handlers to ``app``."""
    app.add_exception_handler(ApiError, _api_error_handler)
    app.add_exception_handler(StarletteHTTPException, _http_exception_handler)
    app.add_exception_handler(RequestValidationError, _validation_handler)
    app.add_exception_handler(ValidationError, _validation_handler)
    app.add_exception_handler(Exception, _unhandled_handler)
