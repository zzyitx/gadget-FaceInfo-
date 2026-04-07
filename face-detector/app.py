from __future__ import annotations

import sys
from pathlib import Path
from typing import Any
import inspect

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from detector import DetectorError, FaceDetector

try:  # pragma: no cover - optional dependency
    from fastapi import FastAPI, File, UploadFile  # type: ignore
except (ImportError, ModuleNotFoundError):  # pragma: no cover - optional dependency
    FastAPI = None
    UploadFile = Any

    def File(default: Any = None, **_: Any) -> Any:
        return default


class _MiniApp:
    def __init__(self, title: str) -> None:
        self.title = title
        self.local_routes: dict[str, Any] = {}

    def post(self, path: str):
        def decorator(func):
            self.local_routes[path] = func
            return func

        return decorator


async def _read_upload_bytes(image: Any) -> bytes:
    reader = getattr(image, "read", None)
    if reader is None:
        if isinstance(image, (bytes, bytearray)):
            return bytes(image)
        raise DetectorError("Upload payload is missing a read method.")

    data = reader()
    if inspect.isawaitable(data):
        data = await data
    if not isinstance(data, (bytes, bytearray)):
        raise DetectorError("Upload payload did not return bytes.")
    return bytes(data)


def create_app(detector: FaceDetector | None = None):
    detector = detector or FaceDetector()

    if FastAPI is None:
        app = _MiniApp(title="face-detector")

        @app.post("/detect")
        async def detect(image: Any):
            image_bytes = await _read_upload_bytes(image)
            try:
                return detector.detect_bytes(image_bytes).to_public_dict()
            except DetectorError as exc:
                return {"status_code": 400, "detail": str(exc)}

        app.detect = detect
        return app

    app = FastAPI(title="face-detector")

    @app.post("/detect")
    async def detect(image: UploadFile = File(...)):  # type: ignore[valid-type]
        image_bytes = await image.read()
        try:
            return detector.detect_bytes(image_bytes).to_public_dict()
        except DetectorError as exc:
            from fastapi import HTTPException  # type: ignore

            raise HTTPException(status_code=400, detail=str(exc)) from exc

    app.local_routes = {"/detect": detect}
    return app


app = create_app()
