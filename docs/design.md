# Stride design and module contract

Stride is the app in repo Rick-Pro/bt-control-treadmill: a one-screen Android remote for Ricardo's PitPat
walking pad (model HY-BA06B1, advertises as "BA06-73F6", BLE service 0xFBA0). It replaces the PitPat app for
the only things Ricardo needs: start, pause, stop, speed up, speed down, live numbers, and a history of past walks.
No account, no internet, no ads, no telemetry.

App label: **Stride** (debug builds: **Stride Dev**). Package `io.github.rickpro.stride`. Repo name stays
`bt-control-treadmill`. Renaming the label later is one string.

## Where things come from
- Repo shape, CI, release flow, allow-lists, scripts, debug keystore rule, AGENTS.md rules: **Rick-Pro/kestrel-android**
  (Ricardo's app that already passes Play Protect). Same Gradle layout: `:core` pure Kotlin with tests, `:app` Android.
- BLE protocol, frame reassembly, command scheduler, session safety rules, step estimator, GATT connection,
  scanner, manager: ported from **Franrodr10/pitpat-controller** (MIT). Keep its MIT notice in THIRD_PARTY_NOTICES.md.
  Translate every user-facing or log string to English. Package rename to `io.github.rickpro.stride.core.*` and
  `io.github.rickpro.stride.*`.

## Hard rules (from kestrel AGENTS.md, carried over)
- Kotlin, Jetpack Compose, Material 3, AndroidX only. No other library. No Room, no serialization plugin, no KSP.
- `core/` has no Android imports and is unit-tested with JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`).
  `tasks.test { useJUnit() }`. No kotlin-test dependency.
- Android calls live only in `app/`. Compose renders state that `core/` and the session layer produce.
- minSdk 33, targetSdk 37, compileSdk 37, AGP 9.4.0, Kotlin 2.4.21, Compose BOM 2026.09.00 (same as kestrel).
- Permissions: only BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT, VIBRATE, plus the androidx.core
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. **No INTERNET.** CI fails on anything else.
- No em dashes anywhere. Plain, short English. No secrets.
- Release APK 8 MB or less.

## Units
Protocol is km/h and km. The UI shows **mph and miles by default** (Ricardo is in the US; the PitPat app shows mph)
with a toggle to km. All conversion and formatting lives in `core` (`units/Units.kt`) and is tested.
Internal speed granularity is 0.1 km/h (the treadmill's own step). A UI tap on + or - changes the target by one
treadmill step (0.1 km/h); the display rounds to one decimal in the chosen unit.

## core module API (package io.github.rickpro.stride.core)

### protocol/
Port of pitpat-controller `TreadmillProtocol`, `PitPatProtocol`, `FrameReassembler`, `StatusDecoder`,
`RunState`, `TreadmillStatus`, `uuid16`, `toHex`. Keep names and signatures exactly as in the MIT source, package
renamed. `PitPatProtocol.speedRange = 1.0..6.0` (capped at runtime by the max the treadmill reports).
Add to `PitPatProtocol`:
- `fun beeperFrame(on: Boolean): ByteArray` : the settings packet captured on a BA09-B
  (on: `6A 17 F1 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 E7 43`,
  off: `6A 17 F1 00 00 00 00 00 00 00 00 00 10 00 00 00 00 00 00 00 00 01 F7 43`). Marked experimental in docs.
- `namePrefixes = listOf("PitPat", "BA0")` so "BA06-73F6" also matches by name, not only by service UUID.

### control/
Port of `CommandScheduler`, `Command`, `CommandKind`, `SessionController`, `StepEstimator` (labels in English:
"pause", "start 2.0 km/h", "speed 2.5 km/h", "STOP").

### units/Units.kt
```kotlin
enum class MeasureUnit { METRIC, IMPERIAL }
object Units {
    const val KM_PER_MILE = 1.609344
    fun kmToMi(km: Double): Double
    fun kmhToMph(kmh: Double): Double
    fun mphToKmh(mph: Double): Double
    fun speedText(kmh: Double, unit: MeasureUnit): String          // "2.5" one decimal, in the unit
    fun speedUnitLabel(unit: MeasureUnit): String                   // "mph" or "km/h"
    fun distanceText(km: Double, unit: MeasureUnit): String         // "0.58" two decimals
    fun distanceUnitLabel(unit: MeasureUnit): String                // "mi" or "km"
    fun durationText(seconds: Long): String                  // "1:02:07" or "45:33"
    fun paceText(kmh: Double, unit: MeasureUnit): String            // "24'00\"" min per mile or km; "--" when speed is 0
}
```

### history/
```kotlin
data class SpeedSample(val atSeconds: Long, val kmh: Double)
data class Workout(
    val id: String,                 // "w-" + startedAtEpochMs
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationSeconds: Long,      // from the treadmill's own counter at the end
    val distanceKm: Double,
    val kcal: Int,
    val steps: Long,
    val stepsEstimated: Boolean,
    val avgSpeedKmh: Double,        // distance / duration
    val maxSpeedKmh: Double,
    val samples: List<SpeedSample>, // one per 5 s of belt time, max 2000, for the chart
)
```
`WorkoutRecorder` (pure logic): feed it `(status: TreadmillStatus, steps: StepEstimator.Steps, nowEpochMs: Long)`
on every status and `onLinkDown()` on disconnect. It returns a `Workout?` when a walk ends. Rules:
- A walk starts when state becomes RUNNING (or STARTING then RUNNING) and the treadmill's duration counter is
  below the last seen one or no walk is open.
- A walk ends when state is STOPPED for 2 consecutive statuses, or the counter resets to 0 while a walk is open,
  or `onLinkDown()` after 60 s without data. PAUSED keeps the walk open.
- Walks shorter than 30 s or 0 distance are dropped (returned as null).
- Samples every 5 s of belt time.

`WorkoutJson`: `fun encode(w: Workout): String` (one JSON object, one line, no newlines inside) and
`fun decode(line: String): Workout?` (null on any parse problem). Hand-written, flat, no library. Tested round trip.

`HistoryStats`: `fun totals(workouts: List<Workout>): Totals(walks: Int, seconds: Long, km: Double, kcal: Int)`,
`fun thisWeek(workouts, nowEpochMs, zoneOffsetSeconds)`, `fun streakDays(...)`. Tested.

## app module (package io.github.rickpro.stride)

### ble/
Port of `BlePermissions` (minSdk 33 so only BLUETOOTH_SCAN and BLUETOOTH_CONNECT; drop the location branch),
`BleScanner`, `GattConnection` (English messages).

### session/
- `Settings` (SharedPreferences): deviceAddress, deviceName, targetKmh (default 2.0), unit (default IMPERIAL),
  keepAliveEnabled (default true), keepScreenOn (default true), presetsKmh (default 1.6, 2.4, 3.2, 4.0 which are
  1.0, 1.5, 2.0, 2.5 mph).
- `Haptics`, `DiagnosticsLog` as in the source.
- `TreadmillManager`: port, English, plus:
  - holds a `WorkoutRecorder` and a `HistoryStore`; every decoded status goes to the recorder; a finished
    `Workout` is appended to the store.
  - `fun setBeeper(on: Boolean)` writes `PitPatProtocol.beeperFrame(on)` as a CONTROL command.
  - `State` gains `val unit: MeasureUnit` and `val presetsKmh: List<Double>` and `val recording: Boolean`.
  - `fun setUnit(unit: MeasureUnit)`.
- `HistoryStore(context)`: file `filesDir/workouts.jsonl`, one `WorkoutJson` line per walk. `val workouts:
  StateFlow<List<Workout>>` newest first. `fun add(w)`, `fun delete(id)`, `fun exportText(): String` (CSV of all
  walks for Share). All file IO on `Dispatchers.IO`.

### ui/
One main screen (`ControlScreen`) and two secondary ones reached by tapping, never a bottom bar:
- **Setup** (first run or no remembered device): one card: "Turn on the treadmill, then tap Find". Scans 15 s,
  lists matches with signal strength, tapping one saves it and connects. If exactly one treadmill is found, it is
  picked automatically. Permission request inline. "Bluetooth is off" state with a button that opens the
  Bluetooth settings panel (`Settings.ACTION_BLUETOOTH_SETTINGS`).
- **Control** (the home): top status line (treadmill name, Connected / Connecting / Reconnecting / Not found,
  small dot). Big speed number in the chosen unit with the unit label. Four stats: time, distance, calories,
  steps (steps marked "est." when estimated). Then: SPD - and SPD + big round buttons either side of the
  Start/Pause button; STOP as a wide red button below. Then 4 preset chips. Then a small row: units toggle
  (mph / km), History, Settings (gear). Buttons disabled with a clear reason when not connected.
  Keep the screen on while the belt moves (window flag, no permission). Link-lost banner in orange, with
  Reconnect. Overlay alerts for LINK_LOST_WHILE_MOVING and STOP_NOT_CONFIRMED ("Use the treadmill's own remote").
- **History**: list grouped by day, each row: date, duration, distance, avg speed, calories. Tap for detail
  (all numbers plus the speed chart drawn with Canvas). Swipe or long-press row to Delete with a confirm
  dialog. Totals card at the top (this week and all time). Share as CSV button.
- **Settings** (sheet): units, keep screen on, treadmill beeper on/off (experimental), presets (4 fields),
  Forget treadmill, Diagnostics log (view and share), About (version, licenses).

Theme: dark only, like Kestrel. Background `#0f1419`, surface `#171d24`, text `#e6edf3`, muted `#909dab`,
accent (green) `#3fb950`, stop red `#f85149`, warning `#d29922`. Big numerals use the default font with
`FontWeight.SemiBold` and tabular figures. Everything is thumb-reachable: the control cluster sits in the
lower half of the screen. Minimum touch target 56 dp for SPD and Start, 64 dp tall for STOP.

## Safety
- STOP always goes first in the write queue and is resent until the belt stops; failure shows an alert.
- Nothing is resent after a reconnect. On link loss while moving: vibrate and show the alert.
- Speed commands go only while RUNNING with a fresh status (3 s).
