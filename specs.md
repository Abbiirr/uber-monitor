Below are comprehensive implementation specs for adding the requested views and wiring them into the existing background scraping app.

Project snapshot (from current files)
- Uses Kotlin, AndroidX, Compose enabled, `minSdk=24`, `targetSdk=34`.
- `MainActivity` prompts for Accessibility Service and Usage Access.
- Service class `UberAccessibilityService` (in file `RideAccessibilityService.kt`) listens to Uber app events and captures screenshots via `MediaProjection`.
- `MediaProjectionSingleton` holds the permission `Intent` but no UI flow currently requests it.
- No UI yet for user profile or data review.

Goals
- Add a simple UI to:
    - Collect and persist user name and phone number.
    - Show a list of data collected so far (screenshots and/or scraped payloads).
- Keep background scraping unchanged; only add persistence and UI surfaces.
- Do not block background operation if the UI is never opened after first setup.

Functional requirements
1) Registration screen (collect user info)
- Fields:
    - Name: non-empty string; max 100 chars.
    - Phone number: numeric string; 8–15 digits; normalize to E.164 if country context is known, else store raw with basic validation.
- Actions:
    - Save button enabled only when inputs are valid.
    - Persist locally; editable later.
- UX:
    - Show current saved values (if any).
    - Inline validation errors.
    - Optional status section showing permission states: Accessibility, Usage Access, Screen Capture.
    - Button to request Screen Capture permission (MediaProjection), storing the obtained `Intent` in `MediaProjectionSingleton`.
- Storage:
    - Use Jetpack DataStore (Preferences) for `user_name` and `user_phone`.

2) Collected data screen (what’s been collected)
- Data sources:
    - Screenshot captures saved by the service (PNG file path + timestamp).
    - Scraped payloads (if/when implemented in service): JSON payload + timestamp + source app (uber, pathao) + delivery status.
- UI:
    - List with sections or filters by source app and type (Screenshot vs Payload).
    - Each row shows:
        - Title: source app + type.
        - Subtitle: readable timestamp.
        - For payloads: short preview (e.g., `trip_id`, `fare`, `discount`, etc. if present).
        - For screenshots: thumbnail and file size.
    - Tapping a row:
        - Payload: open a detail dialog with pretty-printed JSON and copy-to-clipboard.
        - Screenshot: open an image viewer.
- Controls:
    - Filter chips: All, Uber, Pathao; and All, Payloads, Screenshots.
    - Clear local cache button (payloads and/or screenshots), with confirm dialog.
    - Share single item (image or JSON) via ACTION\_SEND.
- Empty states:
    - If no local data: show explanatory message that background collection occurs when the driver app is active.

3) Navigation
- Single-activity app with Compose NavHost.
- Destinations:
    - `registration` (default if profile incomplete).
    - `collectedData` (default if profile exists).
- App bar with simple navigation between Registration and Collected Data.

Non‑functional requirements
- Persist and render efficiently for potentially hundreds of items.
- Avoid blocking UI thread; use coroutines for I/O.
- Handle missing permissions gracefully; background will collect only what is allowed.

Data model and storage
1) User profile (DataStore Preferences)
- Keys:
    - `user_name`: String
    - `user_phone`: String
    - `onboarding_completed`: Boolean

2) Collected items (Room)
- Tables:
    - `CollectedItem`
        - `id`: Long PK auto-gen
        - `timestamp`: Long (ms)
        - `readable_timestamp`: String (e.g., `yyyy-MM-dd HH:mm:ss`)
        - `source_app`: String enum-like: `uber`, `pathao`, `unknown`
        - `type`: String enum-like: `payload`, `screenshot`
        - `payload_json`: String nullable (for type=`payload`)
        - `file_path`: String nullable (for type=`screenshot`)
        - `send_status`: String enum-like: `sent`, `pending`, `failed`
        - `notes`: String nullable
- Indexes:
    - Index on `timestamp`
    - Composite index on `source_app`, `type`
- DAO:
    - Insert single/batch
    - Query paged by filters
    - Delete by id / clear all
- Migrations: start at version 1.

Background data pipeline integration
- On screenshot save in `UberAccessibilityService.saveBitmap(...)`:
    - After file write success, insert a `CollectedItem` with:
        - `type = screenshot`
        - `source_app = uber` (based on current event’s packageName)
        - `file_path = saved path`
        - `timestamp = System.currentTimeMillis()`
        - `readable_timestamp = formatted string`
        - `send_status` remains unrelated unless screenshots are also uploaded.
- For future payload scraping:
    - When creating the JSON payload and sending to backend, also persist a `CollectedItem` with:
        - `type = payload`
        - `payload_json = compact JSON string`
        - `send_status = sent/pending/failed` depending on HTTP result.

Permissions and onboarding flow
- On app start in `MainActivity.onResume()`:
    - Continue prompting for Accessibility and Usage Access as implemented.
    - Add a prompt or button in Registration screen to request Screen Capture permission if `MediaProjectionSingleton.projectionData` is null.
- MediaProjection:
    - Start the capture request via `MediaProjectionManager.createScreenCaptureIntent()` from the Registration screen.
    - Store the returned `Intent` into `MediaProjectionSingleton`.
    - Handle denial gracefully; collection continues with what is available.

Error handling and edge cases
- DataStore/Room errors: log and show a non-blocking toast/snackbar.
- File missing for a screenshot row: mark item as stale and allow deletion.
- Large lists: use paging (Paging 3) if item count > 500; otherwise a simple LazyColumn is acceptable.

Security and privacy
- All data remains on-device except what is already sent to backend by the collection logic.
- No extra permissions beyond current manifest; screenshots saved to app-scoped external files directory.
- Allow users to clear local data from the Collected Data screen.

Testing
- Unit tests:
    - Validation of name and phone.
    - DataStore read/write.
    - Room DAO insert/query/delete.
- Instrumented tests:
    - Registration flow saves and restores values.
    - Collected list renders screenshots and payloads.
    - Delete/clear actions update UI and storage.
- Manual:
    - Permissions flows on Android 8–14.
    - Large list scrolling performance.

Acceptance criteria
- User can enter and save name and phone; values persist across restarts.
- User can request and grant screen capture from UI.
- Collected Data screen shows:
    - Newly saved screenshots as list items with timestamp and thumbnail.
    - Payload entries (when service writes them) with readable timestamp and key fields shown.
- Filters and item detail views work.
- Clear local cache removes items and updates UI.
- Background service continues functioning regardless of UI state.

Files to create or modify
- Modify `app/src/main/java/com/example/uber_monitor/MainActivity.kt`
    - Host a Compose `NavHost` for `registration` and `collectedData`.
    - Keep existing permission prompts; surface navigation to new screens.
- Create `app/src/main/java/com/example/uber_monitor/ui/RegistrationScreen.kt`
    - Compose screen with two `TextField`s, validation, Save button, permission status, and “Enable Screen Capture” button.
- Create `app/src/main/java/com/example/uber_monitor/ui/CollectedDataScreen.kt`
    - Compose list screen with filters, item rows, detail dialogs, and clear cache action.
- Create `app/src/main/java/com/example/uber_monitor/data/UserPreferences.kt`
    - DataStore wrapper for profile fields.
- Create `app/src/main/java/com/example/uber_monitor/data/db/AppDatabase.kt`
    - Room database with `CollectedItem` entity and DAO.
- Create `app/src/main/java/com/example/uber_monitor/data/db/CollectedItem.kt`
    - Entity + enum constants for `type`, `source_app`, `send_status`.
- Create `app/src/main/java/com/example/uber_monitor/data/db/CollectedItemDao.kt`
    - DAO interfaces for inserts/queries/clear.
- Create `app/src/main/java/com/example/uber_monitor/data/Repository.kt`
    - Repository exposing flows for UI and insert functions for the service.
- Modify `app/src/main/java/com/example/uber_monitor/RideAccessibilityService.kt`
    - After saving a screenshot, insert a `CollectedItem` record via the repository.
- Optional: Create `app/src/main/java/com/example/uber_monitor/ui/PreviewUtils.kt`
    - Compose previews with fake data.

Notes on existing naming
- The file `app/src/main/java/com/example/uber_monitor/RideAccessibilityService.kt` defines class `UberAccessibilityService`; the manifest references `.UberAccessibilityService`. This is valid; no change required.

This spec enables a minimal, robust UI for user registration and collected data inspection while keeping the background scraping pipeline intact.