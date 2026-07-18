# Backend Contract Fixes Status

Branch: `backend-contract-fixes`

## Done

- Created branch `backend-contract-fixes`.
- Commit `faace40` - `fix: align gateway identity and queue admission`
  - Standardized downstream user identity on `X-User-Id`.
  - Removed booking controller's unsafe default `"alice"` fallback.
  - Booking now requires `X-User-Id`.
  - Queue controller now uses gateway-injected `X-User-Id` instead of client `?userId=...`.
  - Gateway queue-pass filter now forwards `X-User-Id` and `X-Queue-Event-Id`.
  - Gateway rejects queue passes whose subject does not match the authenticated user.
  - Gateway accepts `queue_pass` query param for SSE compatibility.
  - Gateway auth filter accepts `access_token` query param only for `/api/v1/queue/stream` because browser `EventSource` cannot send custom headers.
  - `POST /api/v1/bookings` is queue-pass protected.
  - Checkout/status routes remain authenticated user-owned flows.
  - Seat stream is queue-pass protected when queue pressure exists, with the same low-load bypass as seatmap.
- Commit `6861818` - `feat: seed visual seat hierarchy`
  - Added `catalog-service/src/main/resources/db/changelog/db.changelog-1.5-seat-visual-hierarchy.yaml`.
  - Seeded Wembley latitude/longitude.
  - Updated seeded seats into VIP floor, lower bowl, and balcony bands with x/y coordinates.
- Wrote detailed walkthrough at `notes/walkthrough_backend_contract_fixes.md`, but it is not committed because `notes/` is ignored and `git add -f` did not stage it in this environment.

## Verified

Initial sandboxed test runs failed because Mockito/Byte Buddy could not self-attach to the JVM.

These passed when rerun outside the sandbox:

- `mvn test -pl gateway-service -Dtest=QueuePassValidationGatewayFilterFactoryTests`
- `mvn test -pl queue-service -Dtest=QueueControllerTests`
- `mvn test -pl booking-service -Dtest=BookingIntegrationTests`
- `mvn test -pl catalog-service -Dtest=CatalogControllerTests`

## Left

- Commit documentation. Current issue: `notes/` is ignored and the new walkthrough could not be staged. Options:
  - move the walkthrough to a non-ignored path, or
  - update `.gitignore`, or
  - append to an already tracked notes file if any notes are tracked in your local repo.
- Decide whether to push branch. No push has been done yet.
- Review existing unrelated dirty files that were present before this work:
  - `booking-service/pom.xml`
  - `catalog-service/src/main/resources/application.yml`
  - `catalog-service/src/main/resources/db/changelog/db.changelog-1.4-seed-data.yaml`
  - `docker/postgres/init.sql`
  - untracked payment service/scripts/output files
- Consider disabling Eureka during booking tests to avoid noisy connection-refused logs. Tests still pass.

## Frontend Design Decision Captured

Use hybrid Next.js:

- SSR/server components for public read-mostly pages like event search/detail.
- Client-side Gateway calls for queue, seat stream, seat selection, reserve, checkout.
- Java remains source of truth for auth, queue admission, bookings, payment, and seat status.
- SSE connects from browser to Gateway using narrow query-token fallback:
  - queue stream: `?access_token=...`
  - seat stream: `?queue_pass=...`
