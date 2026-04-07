from __future__ import annotations

import base64
from dataclasses import dataclass, field
from typing import Any


def encode_data_uri(image_bytes: bytes, mime_type: str = "image/png") -> str:
    encoded = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


@dataclass(slots=True)
class FaceBoundingBox:
    x: int
    y: int
    width: int
    height: int

    def to_dict(self) -> dict[str, int]:
        return {
            "x": self.x,
            "y": self.y,
            "width": self.width,
            "height": self.height,
        }


@dataclass(slots=True)
class DetectedFace:
    face_id: str
    bbox: FaceBoundingBox
    confidence: float
    crop_preview: str
    content_type: str = "image/png"
    crop_bytes: bytes = field(repr=False, default=b"")

    def to_public_dict(self) -> dict[str, Any]:
        return {
            "face_id": self.face_id,
            "bbox": self.bbox.to_dict(),
            "confidence": self.confidence,
            "crop_preview": self.crop_preview,
            "content_type": self.content_type,
        }


@dataclass(slots=True)
class DetectionResult:
    detection_id: str
    preview_image: str
    faces: list[DetectedFace]

    def to_public_dict(self) -> dict[str, Any]:
        return {
            "detection_id": self.detection_id,
            "preview_image": self.preview_image,
            "faces": [face.to_public_dict() for face in self.faces],
        }
