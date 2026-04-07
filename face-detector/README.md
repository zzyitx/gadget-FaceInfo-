# face-detector

Local Python sidecar for face detection preview and crop generation.

## What it does

- `POST /detect`
- Accepts `multipart/form-data` with an `image` field
- Returns:
  - `detection_id`
  - `preview_image`
  - `faces[]`
- Each face includes:
  - `face_id`
  - `bbox`
  - `confidence`
  - `crop_preview`
  - `content_type`

The response keeps the public payload small, while the internal detector objects also keep raw crop bytes for the Java sidecar handoff path.

## Install

```bash
cd face-detector
python -m pip install -r requirements.txt
```

If you want the heavier MTCNN backend, install `facenet-pytorch` separately after you confirm the environment can support it.

## Run

From the `face-detector/` directory:

```bash
uvicorn app:app --host 127.0.0.1 --port 8091 --reload
```

If `fastapi` is not installed, the module still exposes the detector and a lightweight in-process route registry for tests, but it will not start an HTTP server until the FastAPI stack is available.

Current image output format is PNG. The response includes `content_type`, and Java callers should preserve that MIME type when forwarding cropped faces into later upload or recognition steps.

## Test

```bash
python -m pytest face-detector/tests -q
```
