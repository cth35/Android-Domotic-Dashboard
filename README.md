# Android-Domotic-Dashboard
Android Dashboard HomeHabit-like.

Android skeleton (Kotlin + Jetpack Compose) reusing the principles of the old
HomeHabit app: JSON-configurable dashboard, free-form grid of resizable
widgets, dark theme by default, screen kept always on.

## Current state

- Free-form grid engine (`engine/GridEngine.kt`): `x, y, w, h` placement,
  collision detection, search for the first free slot. Gaps allowed, no
  automatic rearrangement.
- JSON config model (`model/DashboardConfig.kt`) + example file
  (`app/src/main/assets/dashboard_config.json`).
- **Real Domoticz client** (`data/domoticz/`):
  - `DomoticzClient.kt` — HTTP calls to `/json.htm` (device read, switch
    on/off, dimmer, shutter open/close/stop/set level, thermostat setpoint),
    optional Basic auth.
    **Important**: all reads use `type=command&param=getdevices` rather than
    the old `type=devices` — the latter was deprecated in 2023.2 and
    **completely removed (404) since Domoticz 2025.1**, so it's mandatory to
    stay up to date for any recent version (2026.x included). The write
    commands (`switchlight`, `setsetpoint`) already used the correct format
    from the start.
  - `DomoticzRepository.kt` — device state now comes from a real-time
    WebSocket push channel rather than periodic polling; only SCENE
    widgets still use a 5s REST poll (`getscenes`). See the dedicated
    "Real-time device updates via Domoticz WebSocket" section below for
    the full picture.
  - `DomoticzConfig.kt` — host/port/credentials, derived from `AppSettings`
    (persisted, editable from the settings screen — see dedicated section
    below). Default values `192.168.1.10:8080` unless changed.
  - The light widget is tappable in the dashboard and sends a real
    `switchlight` command to the configured Domoticz server.
- `DashboardViewModel` merges real Domoticz states with demo values
  (`FakeStateProvider`) for non-Domoticz widgets (weather, camera).
- `DashboardScreen` positions each widget in pixels based on its grid
  coordinates (square cells, width = height).

### Edit mode with drag, resize & widget customization (`DashboardScreen.kt` → `EditOverlay` & `EditWidgetNameDialog.kt`)

- Pencil button in the top-right corner toggles `isEditMode`.
- In edit mode, each widget shows a blue outline with interactive handles:
  - **Move**: Dragging the body moves the widget (`x, y`).
  - **Resize**: Dragging the bottom-right blue handle resizes it (`w, h`), with a minimum of 1×1.
  - **Delete**: Tapping the top-left red button (`×`) removes the widget.
  - **Customize**: Tapping the top-right blue pencil button (`edit`) opens the widget parameters dialog (`EditWidgetNameDialog.kt`).
- **Widget Customization**:
  - **Custom Label**: Change the widget's title or reset it to revert to the default Domoticz device name (`fallbackName`).
  - **Custom Background Color**: Pick a background color from a 30-preset responsive palette (Neutrals, Reds, Greens, Blues, Yellows/Violets) or enter a custom RGB `#HEX` code.
  - **Opacity / Alpha Slider**: Adjust card background transparency from `0%` (fully transparent) to `100%` (fully opaque).
  - **Live Preview Box**: Real-time visualization of card color, transparency, and text inside the dialog.
- The candidate placement is recalculated on every drag event and validated
  via `GridEngine.isValidPlacement` (free-form system: no automatic rearrangement). If invalid, the widget stays
  snapped to its last valid position/size until the finger returns to a free
  zone — there's no separate "ghost" widget; the real widget *is* the
  preview.
- The "+" button (add widget) only appears in edit mode.
- Normal taps (light toggle) are disabled during editing to avoid accidental
  triggers during a drag.

`WidgetCard` displays 7 types: weather, light, thermostat, shutter, lock,
camera, selector (Material Design icons as placeholders, to be replaced with
Android-Iconics + FontAwesome). Dark theme (`ui/theme/`) taken from the
approved mockups. `MainActivity` sets the `FLAG_KEEP_SCREEN_ON` flag (wall
display). `usesCleartextTraffic="true"` is set in the manifest, necessary
because Domoticz typically runs over plain HTTP on the local network
(blocked by default since Android 9).

### Testing with a real Domoticz server

1. Launch the app, open the gear button (always visible, top-right corner)
   and enter host/port/credentials — no more need to edit the code (see the
   "Domoticz settings screen" section below).
2. Update the `deviceId` values (`"idx:12"`, etc.) in
   `assets/dashboard_config.json` with the real idx of your devices (visible
   in Domoticz > Setup > Devices), or use the "+" button in edit mode to
   auto-discover them.

## Real-time device updates via Domoticz WebSocket

Domoticz devices (every widget type except SCENE) are no longer refreshed
by periodic REST polling. Instead, the app connects to Domoticz's push
channel and applies updates as they happen, falling back to a bulk REST
fetch only at startup, on reconnect, and whenever the widget list changes.
This trades a small window of possible staleness (see "Known limitations"
below) for near-instant UI updates and a lot less network/CPU load than a
5s poll loop.

### Client (`data/domoticz/DomoticzWebSocketClient.kt`)

- Connects to `ws(s)://<host>:<port>/json` — same host/port as the REST
  API, Domoticz multiplexes both on the same listener.
- Announces the `Sec-WebSocket-Protocol: domoticz` subprotocol, hardcoded
  on the Domoticz server side (`#define websocket_protocol "domoticz"` in
  its `cWebem.cpp`).
- Sends HTTP Basic auth (if configured) in the initial handshake request —
  Domoticz rejects the upgrade otherwise.
- Uses plain OkHttp (`com.squareup.okhttp3:okhttp:4.12.0`) rather than
  Ktor's websocket plugin, so as not to touch the Android engine that
  `DomoticzClient` (REST) already relies on.
- `pingInterval(30s)` on the underlying `OkHttpClient`: keeps the
  connection alive across routers/NAT, and — just as importantly — lets
  OkHttp detect a silently-dead connection (no TCP reset, e.g. an expired
  NAT mapping) within roughly one ping interval, triggering `onFailure`
  and therefore a reconnect.
- Automatic reconnection with exponential backoff (1s → 2s → 5s → 10s →
  capped at 30s), reset to 1s as soon as a connection succeeds.
- Emits a `DomoticzWsEvent` sealed class: `Connected`, `Disconnected`,
  `Failed`, `DeviceUpdate(device)`. Every raw message is logged at debug
  level (tag `DomoticzWebSocket`) before parsing, to make it possible to
  inspect the actual payload shape on a given Domoticz version via
  Logcat.

**Known limitation — message format is not officially documented.**
Domoticz doesn't publish a schema for what it pushes on this channel; the
current parsing (`DomoticzDeviceDto`, shared with the REST client) is a
best-effort guess based on the official web client and the Dashticz
project. `kotlinx.serialization` is configured with `ignoreUnknownKeys =
true` and graceful per-field fallbacks, so a mismatch degrades silently
rather than crashing — but that also means it can go unnoticed without
checking the raw Logcat output on your actual server version.

### Repository (`data/domoticz/DomoticzRepository.kt`)

- `fetchInitialDeviceStates(widgets)` — one bulk REST call
  (`getUsedDevices`, plus a per-widget fallback for "unused" devices),
  used to seed the initial state. The websocket only pushes on *change*,
  so without this call a widget would stay empty until its first change
  after the app opens.
- `observeLiveUpdates(widgets)` — wraps `DomoticzWebSocketClient`, filters
  incoming device updates against the current widget list (matched by
  `idx`), and maps them through the same `mapDeviceToState` /
  `parseDomoticzLastUpdate` helpers already used by the REST path (a
  single source of mapping truth). Emits `DomoticzLiveEvent.StateUpdate`
  (a partial map — one or more widgets sharing the same `idx`) and
  `DomoticzLiveEvent.ConnectionChanged` (surfaced to the UI, see below).
- `observeScenePolling(widgets)` — the only remaining periodic REST poll
  (5s, `getscenes`). Kept separate because nothing confirms scenes and
  groups are pushed over the same websocket channel — it's a distinct
  Domoticz REST resource from `getdevices`.

### ViewModel orchestration (`ui/dashboard/DashboardViewModel.kt`)

Three independent, restartable coroutine jobs, all recreated by
`updateDomoticzSettings()` whenever the server config changes:

- `startDomoticzInitialFetch()` — the one-shot bulk fetch above, re-run
  whenever the Domoticz widget list changes (widget added/removed, or
  first load).
- `startDomoticzPolling()` — scenes only, unchanged 5s REST loop.
- `startDomoticzLiveUpdates()` — the websocket stream. Tracks the
  connection transition: on a *reconnect* (not the very first connection,
  already covered by the initial fetch above), it re-runs
  `fetchInitialDeviceStates()` to catch up on anything that changed while
  disconnected (e.g. a light toggled by a wall switch while Domoticz was
  restarting for maintenance) — otherwise that widget would stay frozen
  on its last known value indefinitely.
- All three funnel into a single `mergeDomoticzStates()` helper: an
  incoming state only replaces the current one if it's newer (2s
  clock-skew margin). This applies uniformly to REST polling, the initial
  fetch, the websocket delta, *and* the reconnect resync — so a resync
  that completes right after a user-triggered optimistic update (e.g.
  `toggleLight`) can't overwrite it with a slightly older REST snapshot.

### Connection status badge (`ui/dashboard/DashboardScreen.kt` → `ConnectionStatusBadge`)

- Small red pill, top-left corner, "Domoticz offline" with a `CloudOff`
  icon, bound to `DashboardViewModel.isDomoticzLiveConnected`.
- Unlike the edit/settings FABs (hidden by default, revealed on touch),
  this badge stays visible as long as the websocket is down — appropriate
  for a wall display nobody actively touches to "check" anything.
- 8s grace period before appearing, to avoid a flash on app startup while
  the very first handshake is still in progress.
- Reflects the websocket connection only, not `observeScenePolling()`'s
  REST failures (currently retried silently, not surfaced) — a
  non-issue if you don't use SCENE widgets, otherwise a scene-specific
  REST outage wouldn't show up in this badge.
- Deliberately **not** the same signal as the per-widget "last updated"
  badge (see below): a widget can show "3h ago" simply because it hasn't
  changed, which looks identical to a real outage. Only this connection
  badge distinguishes "nothing happened" from "I can't reach the server".

## Multi-dashboard (swipeable pages)

**Breaking schema change.** The root of the config JSON no longer has flat
`grid`/`widgets` fields: it's now a list of `pages`, each with its own grid
and its own widgets.

```json
{
  "pages": [
    { 
      "id": "page_accueil", 
      "name": "Home", 
      "grid": { "columns": 4 }, 
      "widgets": [
        {
          "id": "lumiere_1",
          "type": "light",
          "x": 0, "y": 0, "w": 1, "h": 1,
          "label": "Salon",
          "backgroundColor": "#1E293B",
          "backgroundAlpha": 0.85
        }
      ] 
    }
  ]
}
```

A `dashboard_config.json` already persisted in internal storage with the old
format (before multi-page) **will not be migrated automatically** —
`pages` will fall back to its default (one empty page) and the old
`grid`/`widgets` fields will simply be ignored by the parser
(`ignoreUnknownKeys = true`).

- **Additive Widget Properties** (`WidgetConfig` in `model/DashboardConfig.kt`):
  - `backgroundColor`: `#HEX` color string (e.g. `"#1E293B"`). Default is `null` (falls back to theme default).
  - `backgroundAlpha`: Float value from `0.0` (transparent) to `1.0` (opaque). Default is `null` (`1.0`).
  - **100% Backwards Compatible**: Existing JSON files without these fields load without errors or data loss.

- **Navigation** (`DashboardScreen.kt`): `HorizontalPager` (Compose
  Foundation). Horizontal swipe between pages.
- **Tab bar** (`PageTabsBar.kt`): one tab per page at the top of the screen,
  tap to switch, long-press in edit mode to open `PageManageDialog`.
- **Always at least one page**: `removePage()` silently refuses if it's the
  last remaining page.
- **Live states are global, not per-page**: Domoticz/weather polling runs on
  ALL widgets across ALL pages continuously.

## CI/CD & Automated Release Pipeline (`.github/workflows/build-apk.yml`)

The repository includes a complete GitHub Actions workflow for automated building and release distribution:
- **Builds**: Compiles `debug` and `release-unsigned` APKs on Ubuntu using Java 17 and Gradle 8.7.
- **Triggers**:
  - Manual trigger via **Workflow Dispatch** (generates downloadable `apk.zip` artifacts).
  - Automated trigger on Git **Tag push** (`v1.0.0`, `v1.1.0`, etc.).
- **Automated Releases**: Generates a GitHub Release attached with individual `.apk` binary files, ready for installation on Android tablets or phones.

## Weather client (Open-Meteo)

- `data/weather/OpenMeteoClient.kt` — calls `https://api.open-meteo.com/v1/forecast` (free, no API key).
- `data/weather/WeatherRepository.kt` — 5-minute polling interval.
- `data/weather/WeatherCodeMapper.kt` — translates WMO weather codes to French labels.

## 7-day forecast widget (FORECAST)

- Displays 7-day forecast row via Open-Meteo with horizontally scrollable days.

## Météo-France Vigilance Integration

- `data/weather/MeteoFranceClient.kt` — fetches official Météo-France vigilance text bulletins (`/textesvigilance/encours`) using an API key (`meteoFranceApiKey` in global `AppSettings`).
- Configurable per weather widget with the `department` field (e.g. `"department": "35"`).
- Displays color-coded vigilance status (`Vig. Jaune`, `Vig. Orange`, etc.) directly on the weather card.
- Tapping the weather card opens a scrollable details modal showing official Météo-France bulletin sections (*Faits nouveaux*, *Situation générale*) with validity time periods (`start_time` / `end_time`).

## 24h Sparkline History Graphs

- Supported on `THERMOSTAT` widgets and `SENSOR` widgets (explicitly enabled in JSON via `"showSparkline": true` in `WidgetSource`).
- Fetches 24-hour historical graph data from Domoticz (`param=graph`, supporting `sensor=Percentage`, `counter`, `general`, `temp`) and renders a smooth sparkline.
- Fully dynamic: modifying the JSON configuration immediately triggers a data fetch without reloading the app.

## Domoticz interactions in the UI

- **Light**: tap = toggle on/off.
- **Shutter**: `"buttons"` (Open/Stop/Close) or `"toggle"` style.
- **Lock**: tap = toggle locked/unlocked.
- **Thermostat**: tap opens setpoint adjust modal.
- **Selector switch**: opens level selection modal or direct button row.

## Embedded HTTP server (browser-based editing)

- Single source of truth (`data/ConfigRepository.kt`).
- Ktor CIO server on port **8090**: `GET /`, `GET /config`, `POST /config`.
- Token-authenticated endpoints.

## Opening the project

Open the root folder in Android Studio (Koala or newer). Gradle wrapper version: **8.7**, `compileSdk = 34`, `minSdk = 24`.
