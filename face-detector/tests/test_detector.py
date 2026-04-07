import sys
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from detector import FaceDetector, DetectorError


def _build_two_face_like_image() -> bytes:
    image = Image.new("RGB", (180, 120), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((18, 22, 58, 78), fill="black")
    draw.rectangle((108, 30, 152, 86), fill="black")
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def test_detects_multiple_regions_and_returns_public_payload():
    detector = FaceDetector(prefer_mtcnn=False)

    result = detector.detect_bytes(_build_two_face_like_image())

    assert result.detection_id
    assert result.preview_image.startswith("data:image/png;base64,")
    assert len(result.faces) == 2

    first_face = result.faces[0]
    assert first_face.face_id
    assert first_face.bbox.width > 0
    assert first_face.bbox.height > 0
    assert first_face.crop_preview.startswith("data:image/png;base64,")
    assert first_face.crop_bytes


def test_raises_when_no_face_like_region_is_found():
    detector = FaceDetector(prefer_mtcnn=False)
    image = Image.new("RGB", (120, 80), "white")
    buffer = BytesIO()
    image.save(buffer, format="PNG")

    try:
        detector.detect_bytes(buffer.getvalue())
    except DetectorError as exc:
        assert "No face" in str(exc)
    else:
        raise AssertionError("Expected DetectorError for a blank image")


def test_rejects_image_when_pixel_count_exceeds_limit():
    detector = FaceDetector(prefer_mtcnn=False, max_pixels=100)
    image = Image.new("RGB", (20, 20), "white")
    buffer = BytesIO()
    image.save(buffer, format="PNG")

    try:
        detector.detect_bytes(buffer.getvalue())
    except DetectorError as exc:
        assert "too large" in str(exc)
    else:
        raise AssertionError("Expected DetectorError for an oversized image")
