# gadget

Spring Boot public-entity insight aggregator.

## What It Does

- Accepts a public query such as `雷军`, a brand name, or an organization name.
- Optionally accepts an image as supporting context.
- Aggregates:
  - entity summary
  - media news mentions
  - public web references
- Supports multiple external providers behind a normalized service layer.

## What It Does Not Do

- No face recognition
- No person re-identification
- No reverse-image person lookup
- No social-account discovery for private individuals

The image input is treated as non-biometric supporting context only.

## Endpoint

- `POST /api/public-insight/analyze`
  - multipart field `query`: required
  - multipart field `photo`: optional

## Providers

The project includes provider adapters for:

- Wikipedia summary API
- GNews API
- NewsAPI
- SerpApi web search

Each provider can be enabled or disabled independently in [`application.yml`](/D:/ideaProject/gadget/src/main/resources/application.yml).

## Run

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Example

Use a public query like `雷军` to aggregate public summaries and related media mentions.

## Test

```bash
mvn clean test
```
