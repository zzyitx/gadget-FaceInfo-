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
    # 参考 CompreFace，把“是否同脸”先映射为 0..1 的 similarity，再做阈值判定。
    FACE_SIMILARITY_THRESHOLD = 0.5
    FACE_SIMILARITY_COSINE_PIVOT = 0.95
    FACE_SIMILARITY_COSINE_SCALE = 30.0

    def __init__(
        self,
        prefer_mtcnn: bool = True,
        backends: list[_DetectionBackend] | None = None,
        crop_padding: int = 8,
        max_pixels: int = 12_000_000,
        confidence_threshold: float = 0.55,
        allow_non_mtcnn_fallback: bool = False,
        mtcnn_quality_gate: bool = True,
        non_mtcnn_quality_gate: bool = True,
    ) -> None:
        self.crop_padding = crop_padding
        self.max_pixels = max_pixels
        self.confidence_threshold = max(0.0, min(1.0, confidence_threshold))
        self.allow_non_mtcnn_fallback = allow_non_mtcnn_fallback
        self.mtcnn_quality_gate = mtcnn_quality_gate
        self.non_mtcnn_quality_gate = non_mtcnn_quality_gate
        self._eye_classifier = self._init_eye_classifier() if mtcnn_quality_gate else None
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
        candidates = self._post_process_candidates(candidates, image=image, width=width, height=height)
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
        image: Image.Image,
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
            should_check_quality = (
                (candidate.backend == "mtcnn" and self.mtcnn_quality_gate)
                or (
                    candidate.backend in {"opencv-haar", "connected-component"}
                    and self.non_mtcnn_quality_gate
                )
            )
            if should_check_quality:
                if not self._passes_face_quality_gate(image, x, y, box_width, box_height, candidate.confidence):
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
            if self._should_keep_candidate(image=image, candidate=candidate, selected=selected):
                selected.append(candidate)
        return sorted(selected, key=lambda item: (item.bbox.y, item.bbox.x))

    def _should_keep_candidate(
        self,
        image: Image.Image,
        candidate: _FaceCandidate,
        selected: list[_FaceCandidate],
    ) -> bool:
        for kept in selected:
            if not self._should_compare_similarity(candidate.bbox, kept.bbox):
                continue
            similarity = self._try_compute_face_similarity(image=image, left=candidate.bbox, right=kept.bbox)
            if similarity is None:
                if self._compute_iou(candidate.bbox, kept.bbox) >= 0.42:
                    return False
                continue
            if similarity >= self.FACE_SIMILARITY_THRESHOLD:
                return False
        return True

    def _should_compare_similarity(self, left: FaceBoundingBox, right: FaceBoundingBox) -> bool:
        left_center_x = left.x + (left.width / 2.0)
        left_center_y = left.y + (left.height / 2.0)
        right_center_x = right.x + (right.width / 2.0)
        right_center_y = right.y + (right.height / 2.0)
        center_distance = float(
            np.hypot(left_center_x - right_center_x, left_center_y - right_center_y)
        )
        max_dimension = max(left.width, left.height, right.width, right.height)
        return center_distance <= (max_dimension * 0.75)

    def _try_compute_face_similarity(
        self,
        image: Image.Image,
        left: FaceBoundingBox,
        right: FaceBoundingBox,
    ) -> float | None:
        try:
            return self._compute_face_similarity(image=image, left=left, right=right)
        except Exception as exc:  # pragma: no cover - 仅用于降级保护
            LOGGER.warning("Face similarity failed and will fallback to IoU. error=%s", exc)
            return None

    def _compute_face_similarity(
        self,
        image: Image.Image,
        left: FaceBoundingBox,
        right: FaceBoundingBox,
    ) -> float:
        left_signature = self._extract_face_signature(image=image, bbox=left)
        right_signature = self._extract_face_signature(image=image, bbox=right)
        left_norm = float(np.linalg.norm(left_signature))
        right_norm = float(np.linalg.norm(right_signature))
        if left_norm == 0.0 or right_norm == 0.0:
            raise RuntimeError("face signature is empty")
        cosine_similarity = float(np.dot(left_signature, right_signature) / (left_norm * right_norm))
        return self._cosine_to_similarity(cosine_similarity)

    def _extract_face_signature(self, image: Image.Image, bbox: FaceBoundingBox) -> np.ndarray:
        crop = self._crop_face(image, bbox).convert("RGB")
        resized_rgb = crop.resize((16, 16), Image.Resampling.BILINEAR)
        rgb_vector = np.asarray(resized_rgb, dtype=np.float32).reshape(-1) / 255.0

        resized_gray = crop.convert("L").resize((8, 8), Image.Resampling.BILINEAR)
        gray_vector = np.asarray(resized_gray, dtype=np.float32).reshape(-1) / 255.0
        gray_vector = gray_vector - float(np.mean(gray_vector))
        gray_norm = float(np.linalg.norm(gray_vector))
        if gray_norm > 0:
            gray_vector = gray_vector / gray_norm

        histogram_parts: list[np.ndarray] = []
        for channel in range(3):
            channel_histogram, _ = np.histogram(
                np.asarray(resized_rgb, dtype=np.float32)[:, :, channel],
                bins=8,
                range=(0, 255),
            )
            histogram = channel_histogram.astype(np.float32)
            histogram_sum = float(np.sum(histogram))
            if histogram_sum > 0:
                histogram = histogram / histogram_sum
            histogram_parts.append(histogram)
        histogram_vector = np.concatenate(histogram_parts, dtype=np.float32)

        return np.concatenate((rgb_vector, gray_vector.astype(np.float32), histogram_vector), dtype=np.float32)

    def _cosine_to_similarity(self, cosine_similarity: float) -> float:
        similarity = (
            np.tanh(
                (cosine_similarity - self.FACE_SIMILARITY_COSINE_PIVOT)
                * self.FACE_SIMILARITY_COSINE_SCALE
            )
            + 1.0
        ) / 2.0
        return float(max(0.0, min(1.0, similarity)))

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

    def _init_eye_classifier(self):
        if cv2 is None:
            return None
        try:
            eye_cascade_path = str(Path(cv2.data.haarcascades) / "haarcascade_eye_tree_eyeglasses.xml")
            classifier = cv2.CascadeClassifier(eye_cascade_path)
            if classifier.empty():
                return None
            return classifier
        except Exception:  # pragma: no cover - 初始化容错
            return None

    def _passes_face_quality_gate(
        self,
        image: Image.Image,
        x: int,
        y: int,
        box_width: int,
        box_height: int,
        confidence: float,
    ) -> bool:
        crop = np.asarray(image.crop((x, y, x + box_width, y + box_height)).convert("RGB"), dtype=np.uint8)
        if crop.size == 0:
            return False

        skin_ratio = self._estimate_skin_ratio(crop)
        # 肤色比例足够高时直接放行，避免眼镜/偏头导致眼睛级联漏检。
        if skin_ratio >= 0.2:
            return True
        if confidence < 0.9 and skin_ratio < 0.08:
            return False
        if skin_ratio < 0.05:
            return False

        if self._eye_classifier is not None and min(box_width, box_height) >= 36:
            gray = cv2.cvtColor(crop, cv2.COLOR_RGB2GRAY)
            gray = cv2.equalizeHist(gray)
            eyes = self._eye_classifier.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=4,
                minSize=(8, 8),
            )
            if len(eyes) == 0 and confidence < 0.93:
                return False
        return True

    def _estimate_skin_ratio(self, crop: np.ndarray) -> float:
        if crop.ndim != 3 or crop.shape[2] != 3:
            return 0.0
        rgb = crop.astype(np.int16)
        r = rgb[:, :, 0]
        g = rgb[:, :, 1]
        b = rgb[:, :, 2]

        rgb_skin = (
            (r > 40)
            & (g > 20)
            & (b > 10)
            & ((np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)) > 12)
            & (np.abs(r - g) > 10)
            & (r > g)
            & (r > b)
        )

        ycrcb = cv2.cvtColor(crop, cv2.COLOR_RGB2YCrCb) if cv2 is not None else None
        if ycrcb is not None:
            cr = ycrcb[:, :, 1]
            cb = ycrcb[:, :, 2]
            ycrcb_skin = (cr >= 132) & (cr <= 180) & (cb >= 80) & (cb <= 135)
            skin_mask = rgb_skin | ycrcb_skin
        else:
            skin_mask = rgb_skin
        return float(np.count_nonzero(skin_mask)) / float(skin_mask.size)
