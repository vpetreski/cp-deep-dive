"""Placeholder test for Chapter 05 — skipped until the chapter is implemented."""

from __future__ import annotations

import pytest


@pytest.mark.skip(reason="Chapter 05 not yet implemented — see docs/chapters/05-*.md")
def test_placeholder() -> None:
    """Stub. Real tests land with the chapter."""
    raise AssertionError("should have been skipped")


def test_main_raises_not_implemented() -> None:
    """Until implemented, calling main() must raise NotImplementedError."""
    from py_cp_sat_ch05.main import main

    with pytest.raises(NotImplementedError):
        main()
