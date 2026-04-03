# Face Detection Selection Design

## Goal

Add local face detection to `gadget` by embedding a lightweight Python detection module in the repository. The new flow must detect multiple faces from one uploaded image, let the user choose the target face, crop that face, and then pass the cropped face image into the existing recognition and aggregation pipeline.

## Scope

In scope:
- Add a local Python HTTP module for face detection and cropping.
- Add Java-side detection orchestration, session storage, and controller endpoints.
- Support multi-face preview, user selection, and cropped-face handoff into the current `FaceRecognitionService` flow.
- Preserve the current aggregation pipeline after face selection.
- Add tests for detection session lifecycle, controller behavior, and main service handoff.

Out of scope:
- Replacing the current recognition/search providers.
- Adding persistent storage for detection sessions.
- Returning full raw detector outputs to clients.
- Reworking existing `FaceInfoResponse` unless needed for the new endpoints.

## User Flow

### Step 1: Detect faces

Client uploads the original image to `POST /api/face2info/detect`.

The system:
- validates the image in Java;
- forwards the image to the local Python detector;
- detects all visible faces;
- generates an annotated preview image with bounding boxes;
- creates cropped previews for each detected face;
- stores a short-lived detection session in Java memory;
- returns a response containing `detection_id`, annotated preview, and `faces[]` metadata.

Each face item includes:
- `face_id`
- bounding box coordinates
- confidence score
- cropped preview image

### Step 2: Select a face and run the main pipeline

Client sends `detection_id` and `face_id` to `POST /api/face2info/process-selected`.

The system:
- loads the detection session from memory;
- validates that the selected face belongs to the session;
- obtains the final cropped face image from the stored session;
- passes the cropped image into the existing `FaceRecognitionService.recognize()`;
- continues into `InformationAggregationService.aggregate()` unchanged;
- returns the existing `FaceInfoResponse`.

## Architecture

### Python sidecar module

Add a repository-local module, for example `face-detector/`, implemented as a lightweight Python HTTP service.

Responsibilities:
- receive uploaded images;
- run face detection;
- detect multiple faces;
- produce bounding boxes and confidence scores;
- render one annotated preview image;
- produce one crop preview per face;
- return image bytes or base64 payloads needed by Java.

Non-responsibilities:
- no web search;
- no person recognition;
- no aggregation;
- no database access;
- no long-lived session ownership.

Recommended initial stack:
- `facenet-pytorch` MTCNN for face detection;
- `Pillow` for drawing boxes and cropping;
- a minimal HTTP framework such as `FastAPI`.

Reasoning:
- lighter than replicating full CompreFace services;
- more robust than OpenCV Haar cascades;
- easy to package as a local companion service.

### Java integration

Add the following Java components:

- `FaceDetectionClient`
  Calls the local Python detector over HTTP.

- `FaceDetectionService`
  Owns the detection workflow, session creation, face selection validation, and cropped image retrieval.

- `DetectionSessionStore`
  Stores short-lived detection sessions in memory with expiration.

- `FaceSelectionController` or expanded `FaceInfoController`
  Exposes the new detect/select endpoints.

The existing `Face2InfoService` remains the owner of the recognition + aggregation main flow. It should receive the already-cropped selected face image for the second step.

## Data Model

### Internal Java models

Add internal models for detection workflow only, for example:
- `DetectedFace`
- `FaceBoundingBox`
- `FaceCropResult`
- `DetectionSession`
- `SelectedFaceRequest`
- `DetectionResponse`

Key session fields:
- `detectionId`
- original image metadata
- annotated preview image
- detected face list
- selected-face crop payloads
- creation time
- expiration time

### Public API contract

#### `POST /api/face2info/detect`

Request:
- `multipart/form-data`
- field: `image`

Response fields:
- `detection_id`
- `preview_image`
- `faces[]`

Each face includes:
- `face_id`
- `bbox` with `x`, `y`, `width`, `height`
- `confidence`
- `crop_preview`

#### `POST /api/face2info/process-selected`

Request fields:
- `detection_id`
- `face_id`

Response:
- existing `FaceInfoResponse`

## Data Flow

### Detect request

1. Controller receives uploaded image.
2. `ImageUtils` validates size and MIME type.
3. `FaceDetectionService` calls Python detector.
4. Python detector returns all faces plus previews.
5. Java creates a `DetectionSession` and stores it in memory.
6. Java returns `detection_id` and detection response payload.

### Select request

1. Controller receives `detection_id` and `face_id`.
2. `FaceDetectionService` loads and validates the session.
3. `FaceDetectionService` converts the selected crop into a `MultipartFile`-compatible object or equivalent internal image object.
4. Existing `FaceRecognitionService.recognize()` is called with the selected crop.
5. Existing aggregation runs unchanged.
6. Existing `FaceInfoResponse` is returned.

## Session Strategy

Detection sessions should be stored in memory first.

Initial behavior:
- use a concurrent in-memory map;
- set short TTL, for example 10 minutes;
- remove expired sessions lazily during lookup and optionally during periodic cleanup;
- reject expired or missing sessions with a recoverable business error.

Why not database first:
- faster to ship;
- sessions are transient UI workflow state;
- no persistent audit requirement has been stated.

## Error Handling

### Detect step

Return business errors for:
- no face detected;
- unsupported image format;
- invalid image content;
- Python detector unavailable;
- detector timeout.

### Select step

Return business errors for:
- missing `detection_id`;
- unknown or expired session;
- selected `face_id` not found in session;
- selected face crop missing or unreadable.

Error style requirements:
- use existing unified exception handling;
- avoid leaking raw Python stack traces or internal payloads;
- return actionable user-facing messages where recovery is possible.

## Compatibility

Keep current `POST /api/face2info` unchanged for now.

New UI flows should use the new two-step detect/select process.

This avoids breaking current clients while letting the frontend adopt multi-face selection incrementally.

A later phase may decide whether the legacy single-step endpoint should automatically select one face or be deprecated.

## Testing Strategy

### Java tests

Add tests for:
- `FaceDetectionService` successful detection session creation;
- detect failure when no face is found;
- session lookup success;
- session expiration failure;
- invalid `face_id` failure;
- `Face2InfoServiceImpl` receiving selected cropped face and passing it to `FaceRecognitionService`;
- controller tests for `/detect` and `/process-selected` success and failure paths.

### Python tests

Add tests for:
- single-face image;
- multi-face image;
- no-face image;
- bounding box serialization;
- crop generation;
- annotated preview generation.

## Operational Notes

Configuration must be externalized in `application.yml` and mirrored in `application-git.yml`.

Expected Java config additions:
- detector base URL
- connect timeout
- read timeout
- session TTL

Expected Python runtime config:
- host/port
- detector thresholds if exposed
- optional max image dimension guard

## Risks And Mitigations

### Risk: Python sidecar process adds deployment complexity
Mitigation:
- keep the module very small;
- document local startup clearly;
- isolate all detector-specific config in one place.

### Risk: session loss on Java restart
Mitigation:
- acceptable for first version because sessions are transient;
- return a recoverable session-expired error and let the UI restart detection.

### Risk: large images increase latency
Mitigation:
- keep upload validation in Java;
- optionally downscale before detector call in a later iteration if needed.

## Recommended Implementation Order

1. Define API contracts and Java internal models.
2. Add controller tests for the new endpoints.
3. Add `FaceDetectionService` tests for session lifecycle.
4. Build the Python detector service with test fixtures.
5. Implement Java detector client and in-memory session store.
6. Wire selected crop into the existing recognition flow.
7. Add configuration and documentation updates.
8. Run full verification across Java tests.
