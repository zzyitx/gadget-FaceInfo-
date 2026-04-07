from __future__ import annotations

import sys
import uuid
from collections import deque
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Protocol

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from schemas import DetectionResult, DetectedFace, FaceBoundingBox, encode_data_uri

try:  # pragma: no cover - optional dependency
    from facenet_pytorch import MTCNN  # type: ignore
except (ImportError, ModuleNotFoundError):  # pragma: no cover - optional dependency
    MTCNN = None

try:  # pragma: no cover - optional dependency
    import cv2  # type: ignore
except (ImportError, ModuleNotFoundError):  # pragma: no cover - optional dependency
    cv2 = None


class DetectorError(RuntimeError):
    pass


@dataclass(slots=True)
class _FaceCandidate:
    bbox: FaceBoundingBox
    confidence: float


class _DetectionBackend(Protocol):
    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        ...

    @property
    def name(self) -> str:
        ...


class _MTCNNBackend:
    name = "mtcnn"

    def __init__(self) -> None:
        self._mtcnn = MTCNN(keep_all=True, device="cpu")

    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        boxes, probs = self._mtcnn.detect(image)
        if boxes is None or probs is None:
            return []

        candidates: list[_FaceCandidate] = []
        for box, prob in zip(boxes, probs):
            if box is None or prob is None:
                continue
            x1, y1, x2, y2 = box
            x = max(0, int(round(x1)))
            y = max(0, int(round(y1)))
            width = max(1, int(round(x2 - x1)))
            height = max(1, int(round(y2 - y1)))
            candidates.append(
                _FaceCandidate(
                    bbox=FaceBoundingBox(x=x, y=y, width=width, height=height),
                    confidence=round(float(prob), 3),
                )
            )
        return candidates


class _OpenCvHaarBackend:
    name = "opencv-haar"

    def __init__(self, scale_factor: float = 1.08, min_neighbors: int = 4) -> None:
        if cv2 is None:
            raise RuntimeError("OpenCV is not available.")
        cascade_path = str(Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml")
        self._classifier = cv2.CascadeClassifier(cascade_path)
        if self._classifier.empty():
            raise RuntimeError(f"Failed to load OpenCV cascade model: {cascade_path}")
        self._scale_factor = scale_factor
        self._min_neighbors = min_neighbors

    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        bgr = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
        grayscale = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        grayscale = cv2.equalizeHist(grayscale)
        width, height = image.size
        min_size = max(18, int(min(width, height) * 0.08))
        faces = self._classifier.detectMultiScale(
            grayscale,
            scaleFactor=self._scale_factor,
            minNeighbors=self._min_neighbors,
            minSize=(min_size, min_size),
        )

        candidates: list[_FaceCandidate] = []
        for x, y, w, h in faces:
            candidates.append(
                _FaceCandidate(
                    bbox=FaceBoundingBox(
                        x=max(0, int(x)),
                        y=max(0, int(y)),
                        width=max(1, int(w)),
                        height=max(1, int(h)),
                    ),
                    confidence=0.72,
                )
            )
        return candidates


class _ConnectedComponentBackend:
    name = "connected-component"

    def __init__(self, threshold: int = 240, min_area: int = 150) -> None:
        self.threshold = threshold
        self.min_area = min_area

    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        grayscale = image.convert("L")
        width, height = grayscale.size
        pixels = grayscale.load()
        visited = [[False for _ in range(width)] for _ in range(height)]
        components: list[_FaceCandidate] = []

        for y in range(height):
            for x in range(width):
                if visited[y][x] or pixels[x, y] > self.threshold:
                    continue
                queue = deque([(x, y)])
                visited[y][x] = True
                min_x = max_x = x
                min_y = max_y = y
                dark_pixels = 0

                while queue:
                    current_x, current_y = queue.popleft()
                    dark_pixels += 1
                    min_x = min(min_x, current_x)
                    max_x = max(max_x, current_x)
                    min_y = min(min_y, current_y)
                    max_y = max(max_y, current_y)

                    for next_x, next_y in (
                        (current_x - 1, current_y),
                        (current_x + 1, current_y),
                        (current_x, current_y - 1),
                        (current_x, current_y + 1),
                    ):
                        if not (0 <= next_x < width and 0 <= next_y < height):
                            continue
                        if visited[next_y][next_x] or pixels[next_x, next_y] > self.threshold:
                            continue
                        visited[next_y][next_x] = True
                        queue.append((next_x, next_y))

                bbox_width = max_x - min_x + 1
                bbox_height = max_y - min_y + 1
                area = bbox_width * bbox_height
                if area < self.min_area:
                    continue

                coverage = dark_pixels / area
                confidence = round(min(0.99, 0.55 + (coverage * 0.35) + (area / (width * height) * 0.1)), 3)
                components.append(
                    _FaceCandidate(
                        bbox=FaceBoundingBox(
                            x=min_x,
                            y=min_y,
                            width=bbox_width,
                            height=bbox_height,
                        ),
                        confidence=confidence,
                    )
                )

        components.sort(key=lambda item: (item.bbox.y, item.bbox.x))
        return components


class FaceDetector:
    def __init__(
        self,
        prefer_mtcnn: bool = True,
        backends: list[_DetectionBackend] | None = None,
        crop_padding: int = 8,
        max_pixels: int = 12_000_000,
        confidence_threshold: float = 0.55,
    ) -> None:
        self.crop_padding = crop_padding
        self.max_pixels = max_pixels
        self.confidence_threshold = max(0.0, min(1.0, confidence_threshold))
        self._backends = backends or self._resolve_backends(prefer_mtcnn=prefer_mtcnn)

    def _resolve_backends(self, prefer_mtcnn: bool) -> list[_DetectionBackend]:
        resolved: list[_DetectionBackend] = []
        if prefer_mtcnn and MTCNN is not None:
            resolved.append(_MTCNNBackend())
        if cv2 is not None:
            try:
                resolved.append(_OpenCvHaarBackend())
            except RuntimeError:
                pass
        resolved.append(_ConnectedComponentBackend())
        return resolved

    def detect_bytes(self, image_bytes: bytes) -> DetectionResult:
        try:
            image = Image.open(BytesIO(image_bytes))
            image = ImageOps.exif_transpose(image).convert("RGB")
        except Exception as exc:  # pragma: no cover - depends on bad input
            raise DetectorError("Invalid image payload.") from exc
        return self.detect_image(image)

    def detect_image(self, image: Image.Image) -> DetectionResult:
        width, height = image.size
        if width * height > self.max_pixels:
            raise DetectorError("Image is too large for face detection.")

        candidates = self._detect_with_fallbacks(image)
        candidates = [candidate for candidate in candidates if candidate.confidence >= self.confidence_threshold]
        if not candidates:
            raise DetectorError("No face-like region was found in the uploaded image.")

        detection_id = f"det-{uuid.uuid4().hex[:12]}"
        annotated_image = image.copy()
        draw = ImageDraw.Draw(annotated_image)
        try:
            font = ImageFont.load_default()
        except Exception:  # pragma: no cover - defensive fallback
            font = None

        faces: list[DetectedFace] = []
        for index, candidate in enumerate(candidates, start=1):
            bbox = candidate.bbox
            crop = self._crop_face(image, bbox)
            crop_bytes = self._to_png_bytes(crop)
            face_id = f"face-{index}"
            label = f"{face_id} {candidate.confidence:.2f}"
            left = bbox.x
            top = bbox.y
            right = bbox.x + bbox.width
            bottom = bbox.y + bbox.height

            draw.rectangle((left, top, right, bottom), outline="red", width=3)
            if font is not None:
                text_box = draw.textbbox((left, top), label, font=font)
                text_background = (
                    text_box[0] - 2,
                    text_box[1] - 2,
                    text_box[2] + 2,
                    text_box[3] + 2,
                )
                draw.rectangle(text_background, fill="red")
                draw.text((left, top), label, fill="white", font=font)

            faces.append(
                DetectedFace(
                    face_id=face_id,
                    bbox=bbox,
                    confidence=candidate.confidence,
                    crop_preview=encode_data_uri(crop_bytes),
                    content_type="image/png",
                    crop_bytes=crop_bytes,
                )
            )

        preview_bytes = self._to_png_bytes(annotated_image)
        return DetectionResult(
            detection_id=detection_id,
            preview_image=encode_data_uri(preview_bytes),
            faces=faces,
        )

    def _crop_face(self, image: Image.Image, bbox: FaceBoundingBox) -> Image.Image:
        width, height = image.size
        left = max(0, bbox.x - self.crop_padding)
        top = max(0, bbox.y - self.crop_padding)
        right = min(width, bbox.x + bbox.width + self.crop_padding)
        bottom = min(height, bbox.y + bbox.height + self.crop_padding)
        return image.crop((left, top, right, bottom))

    def _to_png_bytes(self, image: Image.Image) -> bytes:
        buffer = BytesIO()
        image.save(buffer, format="PNG")
        return buffer.getvalue()

    def _detect_with_fallbacks(self, image: Image.Image) -> list[_FaceCandidate]:
        for backend in self._backends:
            candidates = backend.detect(image)
            if candidates:
                return candidates
        return []
