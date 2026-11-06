# Maintenance sync

Reminder details support Record service, Reschedule, Edit, and Remove reminder.
Service logging defaults to leaving the reminder unchanged. Scheduling requires explicit
absolute date/mileage targets; an additional distance is calculated from the service
odometer when saving. Permanent removal has its own confirmation and includes any
server recurrence. Advanced recurring settings and automatic recurring completion stay
in LubeLogger; the [public API updates existing records](https://github.com/hargata/lubelog/blob/main/Controllers/API/ReminderController.cs)
without exposing recurrence configuration.

Room v3 adds reminder intents and service upload receipts. Saving linked work commits
the expense, upload, and optional dependent reminder intent in one transaction. Receipts
retain confirmed server IDs. One shared write lock serializes workers, sign-in refresh,
manual sync, and queued edits/cancellation. Adding work appends background sync rather
than cancelling an active upload. A read-only connection check leaves unsent work queued
while offline. Caller cancellation cannot interrupt dispatched writes.

The service must be confirmed before its reminder action can run. A lost service response
or interrupted dispatch requires review: select a matching server record or explicitly
confirm a retry after checking LubeLogger. Matching uses vehicle, date, odometer,
description, and cost; the user chooses among any matching records. Confirmed creations
are never replayed to repair a reminder or failed display refresh.

Reminder updates fetch current values, preserve fields outside the queued changes, and
pause if the same fields changed remotely. Use server version cancels the local intent;
Apply my change explicitly resolves the conflict. Removed server reminders cannot be
recreated offline. Interrupted writes recheck their target first, including an already
absent reminder after deletion. Permission/unsupported-endpoint errors pause for review.

Home and Maintenance observe Room. Pending cards show Saved on this device alongside
Waiting for service upload, Reminder change pending, or Needs attention. Confirmed
receipts show Synced with LubeLogger. Queued reminder changes can be edited from details
or cancelled from Maintenance. Deleting an unsent linked expense cancels its dependency.
Dispatched/confirmed services are managed in LubeLogger to retain their upload receipt.

Logout clears credentials and memory caches, retaining queued work. A hash of server URL
and API-key identity scopes new operations; credentials remain in encrypted preferences.
The v2→v3 migration preserves existing expenses and operations. Older expense operations
have no known owner, so Expenses asks the user to review and assign them before dispatch.
Switching keys does not transfer another identity's work.

## Validation

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

JVM tests cover reopen/reconnect ordering, partial failures, lost responses, repeated and
concurrent sync, rapid saves, cancellation, interrupted dispatch, conflicts/deletion,
identity isolation, rejected/unsupported writes, and v1/v2 migration preservation.

The opt-in `MaintenanceWorkflowInstrumentedTest` uses the installed app's secure session
and only a vehicle with make **Test** and model **Test**. It exercises service logging,
scheduling, editing, rescheduling, and permanent removal through the real UI, verifies
notes/tags and a single service after repeated sync, then removes only its own test data.
A second opt-in case disables emulator Wi-Fi/mobile data, saves linked work, recreates the
activity, verifies Home's persisted waiting state, and reconnects for automatic worker sync.
It restores the emulator's previous network settings afterward.
Run direct instrumentation to preserve the emulator installation and sign-in:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.lesicnik.wrench.ui.maintenance.MaintenanceWorkflowInstrumentedTest \
  -e maintenanceLive true \
  com.lesicnik.wrench.test/androidx.test.runner.AndroidJUnitRunner
```

The test skips without explicit opt-in, a signed-in session, or the controlled vehicle.
Gradle's connected-test cleanup can uninstall the target app and clear its data; use the
direct commands above for an existing signed-in emulator.
