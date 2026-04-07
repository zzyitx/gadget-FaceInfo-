import sys
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from detector import FaceDetector, DetectorError
from schemas import FaceBoundingBox


class _FakeBackend:
    def __init__(self, candidates):
        self._candidates = candidates

    @property
    def name(self):
        return "fake"

    def detect(self, image):
        return self._candidates


class _FailingBackend:
    @property
    def name(self):
        return "failing"

    def detect(self, image):
        raise RuntimeError("backend crashed")


class _FakeCandidate:
    def __init__(self, bbox: FaceBoundingBox, confidence: float, backend: str = "fake"):
        self.bbox = bbox
        self.confidence = confidence
        self.backend = backend


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


def test_fallback_to_next_backend_when_primary_has_no_face():
    detector = FaceDetector(
        backends=[
            _FakeBackend([]),
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=20, height=20),
                        confidence=0.9,
                    )
                ]
            ),
        ],
    )
    image = Image.new("RGB", (80, 80), "white")
    payload = detector.detect_image(image)

    assert len(payload.faces) == 1


def test_confidence_threshold_filters_low_confidence_faces():
    detector = FaceDetector(
        backends=[
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=20, height=20),
                        confidence=0.4,
                    )
                ]
            )
        ],
        confidence_threshold=0.5,
    )
    image = Image.new("RGB", (80, 80), "white")

    try:
        detector.detect_image(image)
    except DetectorError as exc:
        assert "No face" in str(exc)
    else:
        raise AssertionError("Expected DetectorError when all candidates are filtered out")


def test_fallback_to_next_backend_when_primary_raises_exception():
    detector = FaceDetector(
        backends=[
            _FailingBackend(),
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=12, y=12, width=24, height=24),
                        confidence=0.91,
                    )
                ]
            ),
        ],
    )
    image = Image.new("RGB", (80, 80), "white")

    payload = detector.detect_image(image)

    assert len(payload.faces) == 1


def test_post_process_removes_heavily_overlapped_candidates():
    detector = FaceDetector(
        backends=[
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=40, height=40),
                        confidence=0.95,
                    ),
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=14, y=14, width=40, height=40),
                        confidence=0.7,
                    ),
                ]
            )
        ],
        confidence_threshold=0.5,
    )
    image = Image.new("RGB", (100, 100), "white")

    payload = detector.detect_image(image)

    assert len(payload.faces) == 1
    assert payload.faces[0].bbox.x == 10
    assert payload.faces[0].bbox.y == 10


def test_post_process_filters_out_invalid_aspect_ratio_candidates():
    detector = FaceDetector(
        backends=[
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=80, height=16),
                        confidence=0.92,
                    )
                ]
            )
        ],
        confidence_threshold=0.5,
    )
    image = Image.new("RGB", (100, 100), "white")

    try:
        detector.detect_image(image)
    except DetectorError as exc:
        assert "No face" in str(exc)
    else:
        raise AssertionError("Expected DetectorError when candidate aspect ratio is invalid")


def test_default_behavior_does_not_fallback_when_mtcnn_returns_no_face():
    class _FakeMtcnnBackend:
        @property
        def name(self):
            return "mtcnn"

        def detect(self, image):
            return []

    detector = FaceDetector(
        backends=[
            _FakeMtcnnBackend(),
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=24, height=24),
                        confidence=0.95,
                    )
                ]
            ),
        ],
    )
    image = Image.new("RGB", (80, 80), "white")

    try:
        detector.detect_image(image)
    except DetectorError as exc:
        assert "No face" in str(exc)
    else:
        raise AssertionError("Expected DetectorError because non-MTCNN fallback is disabled by default")


def test_can_enable_non_mtcnn_fallback_explicitly():
    class _FakeMtcnnBackend:
        @property
        def name(self):
            return "mtcnn"

        def detect(self, image):
            return []

    detector = FaceDetector(
        backends=[
            _FakeMtcnnBackend(),
            _FakeBackend(
                [
                    _FakeCandidate(
                        bbox=FaceBoundingBox(x=10, y=10, width=24, height=24),
                        confidence=0.95,
                    )
                ]
            ),
        ],
        allow_non_mtcnn_fallback=True,
    )
    image = Image.new("RGB", (80, 80), "white")

    payload = detector.detect_image(image)

    assert len(payload.faces) == 1
