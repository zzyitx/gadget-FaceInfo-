import asyncio
import sys
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import app


class FakeUploadFile:
    def __init__(self, data: bytes):
        self.filename = "sample.png"
        self._data = data

    async def read(self) -> bytes:
        return self._data


def _build_single_face_like_image() -> bytes:
    image = Image.new("RGB", (120, 100), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((28, 20, 84, 78), fill="black")
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def test_detect_route_is_registered():
    assert "/detect" in app.local_routes


def test_detect_route_returns_expected_payload_shape():
    payload = asyncio.run(app.local_routes["/detect"](FakeUploadFile(_build_single_face_like_image())))

    assert payload["detection_id"]
    assert payload["preview_image"].startswith("data:image/png;base64,")
    assert len(payload["faces"]) == 1
    assert payload["faces"][0]["face_id"]
    assert payload["faces"][0]["crop_preview"].startswith("data:image/png;base64,")
    assert payload["faces"][0]["content_type"] == "image/png"


def test_detect_route_returns_error_payload_for_invalid_image():
    payload = asyncio.run(app.local_routes["/detect"](FakeUploadFile(b"not-an-image")))

    assert payload["status_code"] == 400
    assert "Invalid image payload" in payload["detail"]
