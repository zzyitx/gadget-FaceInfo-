from __future__ import annotations

import sys
import uuid
import logging
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

LOGGER = logging.getLogger(__name__)

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
    backend: str = "unknown"


class _DetectionBackend(Protocol):
    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        ...

    @property
    def name(self) -> str:
        ...


class _MTCNNBackend:
    name = "mtcnn"

    def __init__(self) -> None:
        # 偏向精度的阈值配置，减少弱候选框带来的误检与抖动。
        self._mtcnn = MTCNN(
            keep_all=True,
            device="cpu",
            min_face_size=24,
            thresholds=[0.72, 0.82, 0.9],
            factor=0.709,
            post_process=False,
        )

    def detect(self, image: Image.Image) -> list[_FaceCandidate]:
        # facenet-pytorch 在部分环境下对 PIL 直接输入兼容性不稳定，统一转 uint8 RGB ndarray。
        rgb_array = np.asarray(image.convert("RGB"), dtype=np.uint8)
        try:
            boxes, probs, landmarks = self._mtcnn.detect(rgb_array, landmarks=True)
        except TypeError:  # pragma: no cover - 兼容旧版 facenet-pytorch API
            boxes, probs = self._mtcnn.detect(rgb_array)
            landmarks = None
        if boxes is None or probs is None:
            return []

        image_width, image_height = image.size
        candidates: list[_FaceCandidate] = []
        for index, (box, prob) in enumerate(zip(boxes, probs)):
            if box is None or prob is None:
                continue
            if float(prob) < 0.45:
                continue
            if landmarks is not None and index < len(landmarks) and landmarks[index] is not None:
                if not self._is_landmark_layout_valid(landmarks[index]):
                    continue
                box = self._refine_box_with_landmarks(box, landmarks[index], image_width, image_height)
            x1, y1, x2, y2 = box
            x = min(max(0, int(round(x1))), max(0, image_width - 1))
            y = min(max(0, int(round(y1))), max(0, image_height - 1))
            width = max(1, min(image_width - x, int(round(x2 - x1))))
            height = max(1, min(image_height - y, int(round(y2 - y1))))
            if width * height <= 0:
                continue
            candidates.append(
                _FaceCandidate(
                    bbox=FaceBoundingBox(x=x, y=y, width=width, height=height),
                    confidence=round(float(prob), 3),
                    backend=self.name,
                )
            )
        return self._non_max_suppression(candidates, iou_threshold=0.35)

    def _is_landmark_layout_valid(self, points: np.ndarray) -> bool:
        try:
            points_array = np.asarray(points, dtype=np.float32)
            if points_array.shape != (5, 2):
                return False
            left_eye, right_eye, nose, mouth_left, mouth_right = points_array
            if left_eye[0] >= right_eye[0]:
                return False
            eye_mid_y = float((left_eye[1] + right_eye[1]) / 2.0)
            mouth_mid_y = float((mouth_left[1] + mouth_right[1]) / 2.0)
            if nose[1] <= eye_mid_y or nose[1] >= mouth_mid_y:
                return False
            if mouth_left[1] <= eye_mid_y or mouth_right[1] <= eye_mid_y:
                return False
            return True
        except Exception:  # pragma: no cover - 兜底容错
            return False

    def _refine_box_with_landmarks(
        self,
        box: np.ndarray,
        points: np.ndarray,
        image_width: int,
        image_height: int,
    ) -> np.ndarray:
        try:
            points_array = np.asarray(points, dtype=np.float32)
            if points_array.shape != (5, 2):
                return box
            left_eye, right_eye, nose, mouth_left, mouth_right = points_array
            eye_distance = float(np.linalg.norm(left_eye - right_eye))
            if eye_distance <= 1:
                return box

            center_x = float(nose[0])
            center_y = float((left_eye[1] + right_eye[1] + mouth_left[1] + mouth_right[1]) / 4.0)
            side = max(float(box[2] - box[0]), float(box[3] - box[1]), eye_distance * 2.25)

            x1 = max(0.0, center_x - (side * 0.5))
            y1 = max(0.0, center_y - (side * 0.6))
            x2 = min(float(image_width), center_x + (side * 0.5))
            y2 = min(float(image_height), center_y + (side * 0.4))
            if x2 <= x1 or y2 <= y1:
                return box
            return np.array([x1, y1, x2, y2], dtype=np.float32)
        except Exception:  # pragma: no cover - 兜底容错
            return box

    def _non_max_suppression(self, candidates: list[_FaceCandidate], iou_threshold: float) -> list[_FaceCandidate]:
        if not candidates:
            return []
        ordered = sorted(candidates, key=lambda item: item.confidence, reverse=True)
        selected: list[_FaceCandidate] = []
        for candidate in ordered:
            if all(self._iou(candidate.bbox, kept.bbox) < iou_threshold for kept in selected):
                selected.append(candidate)
        return sorted(selected, key=lambda item: (item.bbox.y, item.bbox.x))

    def _iou(self, left: FaceBoundingBox, right: FaceBoundingBox) -> float:
        x1 = max(left.x, right.x)
        y1 = max(left.y, right.y)
        x2 = min(left.x + left.width, right.x + right.width)
        y2 = min(left.y + left.height, right.y + right.height)
        inter_w = max(0, x2 - x1)
        inter_h = max(0, y2 - y1)
        intersection = inter_w * inter_h
        if intersection == 0:
            return 0.0
        union = (left.width * left.height) + (right.width * right.height) - intersection
        return intersection / max(1, union)


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
                    backend=self.name,
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
                        backend=self.name,
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
        allow_non_mtcnn_fallback: bool = False,
    ) -> None:
        self.crop_padding = crop_padding
        self.max_pixels = max_pixels
        self.confidence_threshold = max(0.0, min(1.0, confidence_threshold))
        self.allow_non_mtcnn_fallback = allow_non_mtcnn_fallback
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
        candidates = self._post_process_candidates(candidates, width=width, height=height)
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
        for index, backend in enumerate(self._backends):
            try:
                candidates = backend.detect(image)
                if candidates:
                    return candidates
                is_mtcnn_first_backend = index == 0 and backend.name == "mtcnn"
                if is_mtcnn_first_backend and not self.allow_non_mtcnn_fallback:
                    # 精度优先：MTCNN 没有可靠结果时，不再使用低精度后备，避免误检。
                    return []
            except Exception as exc:
                LOGGER.warning("Face detection backend failed and will fallback. backend=%s error=%s", backend.name, exc)
        return []

    def _post_process_candidates(
        self,
        candidates: list[_FaceCandidate],
        width: int,
        height: int,
    ) -> list[_FaceCandidate]:
        filtered: list[_FaceCandidate] = []
        min_face_size = max(14, int(min(width, height) * 0.035))
        max_face_width = int(width * 0.98)
        max_face_height = int(height * 0.98)

        for candidate in candidates:
            x = min(max(0, candidate.bbox.x), max(0, width - 1))
            y = min(max(0, candidate.bbox.y), max(0, height - 1))
            box_width = min(max(1, candidate.bbox.width), width - x)
            box_height = min(max(1, candidate.bbox.height), height - y)
            if box_width < min_face_size or box_height < min_face_size:
                continue
            if box_width > max_face_width or box_height > max_face_height:
                continue
            aspect_ratio = box_width / max(1.0, float(box_height))
            if aspect_ratio < 0.45 or aspect_ratio > 1.9:
                continue
            filtered.append(
                _FaceCandidate(
                    bbox=FaceBoundingBox(x=x, y=y, width=box_width, height=box_height),
                    confidence=candidate.confidence,
                    backend=candidate.backend,
                )
            )

        filtered.sort(key=lambda item: item.confidence, reverse=True)
        selected: list[_FaceCandidate] = []
        for candidate in filtered:
            if all(self._compute_iou(candidate.bbox, kept.bbox) < 0.42 for kept in selected):
                selected.append(candidate)
        return sorted(selected, key=lambda item: (item.bbox.y, item.bbox.x))

    def _compute_iou(self, left: FaceBoundingBox, right: FaceBoundingBox) -> float:
        x1 = max(left.x, right.x)
        y1 = max(left.y, right.y)
        x2 = min(left.x + left.width, right.x + right.width)
        y2 = min(left.y + left.height, right.y + right.height)
        inter_w = max(0, x2 - x1)
        inter_h = max(0, y2 - y1)
        intersection = inter_w * inter_h
        if intersection == 0:
            return 0.0
        union = (left.width * left.height) + (right.width * right.height) - intersection
        return intersection / max(1, union)
