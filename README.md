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

### Edit mode with drag & resize (`DashboardScreen.kt` → `EditOverlay`)

- Pencil button in the top-right corner toggles `isEditMode`.
- In edit mode, each widget shows a blue outline; dragging the body moves the
  widget (`x, y`), dragging the bottom-right handle resizes it (`w, h`), with
  a minimum of 1×1.
- The candidate placement is recalculated on every drag event and validated
  via `GridEngine.isValidPlacement` (free-form system, as discussed
  previously: no automatic rearrangement). If invalid, the widget stays
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

### Known limitations / not yet validated on a real device

- **No test session yet covering a real, extended Domoticz outage** (a
  multi-day run including an actual server restart). The reconnect/resync
  logic above is code-reviewed, not field-tested.
- **No distinction between a transient network failure and a permanent
  one** (e.g. wrong credentials) — both trigger the same backoff/retry
  loop and the same "offline" badge, with no indication of *why*.
- Android's Doze/background restrictions are not a concern for an
  always-on, always-foreground wall tablet (the primary use case here),
  but would need revisiting if the app is ever run on a device that
  leaves the foreground for long periods.

## Multi-dashboard (swipeable pages)

**Breaking schema change.** The root of the config JSON no longer has flat
`grid`/`widgets` fields: it's now a list of `pages`, each with its own grid
and its own widgets.

```json
{
  "pages": [
    { "id": "page_accueil", "name": "Home", "grid": { "columns": 4 }, "widgets": [...] },
    { "id": "page_chambre", "name": "Bedroom", "grid": { "columns": 4 }, "widgets": [...] }
  ]
}
```

A `dashboard_config.json` already persisted in internal storage with the old
format (before this change) **will not be migrated automatically** —
`pages` will fall back to its default (one empty page) and the old
`grid`/`widgets` fields will simply be ignored by the parser
(`ignoreUnknownKeys = true`). If you had already launched the app before
this update: uninstall/reinstall, or clear the app's storage, to fall back
to the default asset.

- **Navigation** (`DashboardScreen.kt`): `HorizontalPager` (Compose
  Foundation, no accompanist dependency needed — stable for a long time in
  the compose-bom version used). Horizontal swipe between pages.
- **Tab bar** (`PageTabsBar.kt`): one tab per page at the top of the screen,
  tap to switch (animates the pager rather than a hard jump), long-press in
  edit mode to open `PageManageDialog` (rename or delete). "+" tab only
  visible in edit mode.
- **Always at least one page**: `removePage()` silently refuses if it's the
  last remaining page — you can never end up with zero pages.
- **Live states are global, not per-page**: Domoticz/weather polling runs on
  ALL widgets across ALL pages continuously (`cfg.allWidgets()` in the
  ViewModel), not just the visible page. A light turned on in the "Bedroom"
  page stays up to date even while you're looking at "Home". Only the
  sparkline follows the same logic (eligibility computed across all
  widgets, regardless of page).
- **Adding a widget**: always happens on the page currently visible in the
  pager (`DashboardViewModel.currentPageIndex`, synced from
  `HorizontalPager` via `LaunchedEffect`).
- **Domoticz discovery**: the "already used" filter checks idx values across
  **all** pages, not just the current one — no risk of accidentally
  suggesting the same device twice on two different pages (nothing prevents
  doing so intentionally by editing the JSON by hand, but the UI won't
  suggest it).
- **Drag & resize logic unchanged**, just re-scoped to the displayed page:
  `GridEngine` remains entirely page-agnostic (it only knows about lists of
  widgets + a column count); it's the caller (`DashboardScreen`, inside the
  pager) that passes it the right page each time.
- **No confirmation before deleting a page** — consistent with the rest of
  the app (`removeWidget` doesn't have one either), but a page can contain
  many widgets at once. Worth reconsidering if it turns out to be a source
  of errors in real use.
- **Not tested on a real device**, like the rest: the `HorizontalPager`
  swipe gesture may conflict with the repositioning drag in edit mode (both
  respond to a horizontal gesture) — needs to be validated in practice,
  potentially by disabling page swipe while a widget is being dragged.

## Weather client (Open-Meteo)

- `data/weather/OpenMeteoClient.kt` — calls
  `https://api.open-meteo.com/v1/forecast` (free, no API key): current
  temperature, WMO weather code, day's min/max. A single HTTP call per
  widget per cycle.
- `data/weather/WeatherRepository.kt` — same pattern as Domoticz (poll +
  `flatMapLatest` on config changes), but with a default interval of **15
  minutes** rather than 5 seconds: weather changes slowly, no need to hit
  the API more often.
- `data/weather/WeatherCodeMapper.kt` — translates the numeric WMO code
  returned by Open-Meteo (`weather_code`) into a French label ("Clear
  sky", "Showers", "Thunderstorm"...). Covers the most common codes; an
  unrecognized code falls back to "Unknown conditions" rather than
  displaying a raw number or crashing.
- `WidgetSource` extended with `latitude`/`longitude` (Open-Meteo works with
  coordinates, not city codes — the example `dashboard_config.json` uses
  Paris: `48.8566, 2.3522`, to be changed for your city).
- In the ViewModel, weather polling runs in its own job
  (`startWeatherPolling`), independent from Domoticz's
  (`startDomoticzPolling`, restartable — see the settings section) rather
  than being combined together.

## 7-day forecast widget (FORECAST)

Distinct from the `weather` widget (current temperature): `forecast`
displays the next 7 days in a row (day, icon, max/min), still via
Open-Meteo — **not the official Météo-France API**, ruled out early in the
project since it isn't easily accessible to third-party apps without extra
work (API key, limited access). "Météo France" here just means "weather
for a location in France," not the service of the same name.

- **Same Open-Meteo call as the `weather` widget**
  (`OpenMeteoClient.getForecast`), just with a different `forecastDays` (1
  vs 7) and the `daily` parameter extended with `weather_code` (absent
  before, needed to get a different icon per day rather than a single one
  for the whole week).
- **Distinction made in `WeatherRepository.observeStates`** based on
  `widget.widgetType`: `WEATHER` builds a `WidgetLiveState.Weather` as
  before, `FORECAST` builds a `WidgetLiveState.Forecast(days)` by zipping
  the `time`/`weather_code`/`temperature_2m_max`/`min` arrays returned by
  Open-Meteo (one array per requested variable, not one object per day —
  hence the manual zip rather than a simple direct mapping).
- **Day label already formatted on the repository side** (`formatDayLabel`,
  e.g. "Mon", "Tue") rather than passing a raw ISO date to the UI —
  `SimpleDateFormat("EEE", Locale.FRANCE)`, falls back to `"--"` if the
  Open-Meteo format ever changes.
- **Rendering** (`WidgetCard.kt` → `ForecastContent`): a **horizontally
  scrollable** row (`horizontalScroll`) rather than a fixed 7-column
  layout — stays usable regardless of the widget's size. Recommended at
  width `w=4` in the JSON to see several days without having to swipe
  (example: `prevision_paris` widget, "Home" page). **Not tested on a real
  device**: horizontal scrolling inside a widget could conflict with the
  `HorizontalPager`'s page swipe (both respond to a horizontal gesture), as
  already noted for repositioning drag vs. page swipe — needs to be
  validated in practice.
- **Icon mapping by WMO code** (in `WeatherIcon.kt`) —
  maps Open-Meteo codes to the Google Weather icons assets. This logic is
  centralized in a dedicated component for easier updates.

**Side effect**: `FakeStateProvider` now only contains the camera demo — the
old fake values for lights/thermostat/shutter/lock were removed since they
only served as a safety net before the Domoticz integration existed. This
means a Domoticz widget now shows nothing until the first poll has
responded (previously, it showed a fake value during that short moment).
Consistent with the goal of not leaving stale fake data around, but worth
keeping in mind if the screen looks "empty" for a fraction of a second on
startup.

## Domoticz interactions in the UI

- **Light**: tap = toggle on/off (already in place previously).
- **Shutter — style configurable per widget** (`source.shutterStyle` in the
  JSON: `"buttons"` by default, or `"toggle"`):
  - **`"buttons"`** (default): 3 dedicated buttons open/stop/close
    (`WidgetCard.kt` → `ShutterButtonsContent`/`ShutterButton`).
    `DomoticzClient.stopShutter()` already existed since the start of the
    project but was never called — a half-built feature, now wired up.
    Open/close remain optimistically updated (0%/100%); **stop
    intentionally has no optimistic update** — it's impossible to know the
    exact position where the shutter stops, the real value comes back via
    the Domoticz WebSocket push once the server reports it (see "Real-time
    device updates via Domoticz WebSocket" above). Tap zone deliberately small (20dp) to fit 3 buttons on
    a 1×1 widget — needs to be validated by touch on a real screen, more
    comfortable on a widget resized to 2×1.
  - **`"toggle"`**: tapping the whole widget toggles open/closed based on
    the current position (50% threshold, `ShutterToggleContent` +
    `viewModel.toggleShutter`), more compact but no accessible stop button
    in this mode. Example in `dashboard_config.json` (`volet_chambre`
    widget, "Bedroom" page).
  - No UI to change the style from within the app — only via the JSON
    (manual editing or embedded HTTP server). Consistent with the rest of
    the project's fine-grained settings (no dedicated screen yet at this
    level of per-widget detail).
- **Lock**: tap = toggle locked/unlocked (`toggleLock`), same optimistic
  logic as the light.
- **Thermostat**: tap opens `ThermostatAdjustDialog` — +/- buttons in 0.5°C
  steps (bounded 5-30°C), "Confirm" sends the new setpoint via
  `setThermostatSetpoint`.
- The others follow the same pattern as the light: optimistic UI update only
  if the Domoticz command actually succeeded (no display of a state that
  didn't actually happen if the request fails).

## Generic sensor (SENSOR)

**First things first: a dependency fix.** While building this widget, I
realized that `material-icons-extended` had never been added to
`build.gradle.kts`, even though several icons already used from the start
(`Thermostat`, `Blinds`, `Videocam`, `Palette`, `Cloud`,
`WbIncandescent`...) only exist in that module — `material-icons-core`
(included by default with material3) only contains a limited set (Add,
Check, Close, Edit, Search, Settings...). Without this fix, the project
probably hasn't compiled since these icons were added. Fixed.

Many Domoticz devices (temperature only, humidity, rain, wind, UV,
barometer, energy meters, custom sensors...) didn't fit into any category
and fell into `UNKNOWN`. A generic `sensor` type now covers this.

- **Detection** (`DomoticzTypeMapper.kt`): a wide range of keywords on the
  Domoticz `Type` field (Temp, Humidity, Rain, Wind, UV, Barometer,
  Percentage, Usage/kWh/Counter, Custom Sensor, Air Quality, Visibility,
  Solar Radiation, Soil Moisture, Leaf Wetness, General). As always with
  these heuristics, to be adjusted if specific sensors still fall into
  `UNKNOWN`.
- **Display** (`WidgetCard.kt` → `SensorContent`): icon based on category
  (`SensorKind`) + value displayed directly from Domoticz's `Data` field
  (already formatted with its unit by Domoticz itself, e.g. `"21.5 C"`,
  `"68 %"` — no custom reformatting).
- **Visual gauge deliberately limited**: a thin progress bar only appears
  for naturally 0-100 bounded quantities (humidity, percentage/battery).
  For everything else (rain, wind, energy...), no gauge — inventing an
  arbitrary scale would have been misleading rather than useful. If you
  want gauges for other quantities (e.g. temperature with a -10/40°C
  range), per-sensor bounds will need to be defined, probably configurable
  in the JSON rather than hardcoded.

## Temperature sparkline (SENSOR kind=TEMPERATURE and THERMOSTAT)

Mini-graph of the last 24h, drawn directly in the widget, in addition to
the current value.

- **Data source** (`DomoticzClient.getTempGraphDay`):
  `GET /json.htm?type=command&param=graph&sensor=temp&idx=IDX&range=day` —
  a dedicated Domoticz endpoint, distinct from `getdevices`. Returns about
  288 points over 24h (one point roughly every ~5min depending on Domoticz
  config), field `te` for each point's temperature.
- **Downsampling** (`DomoticzRepository.fetchTemperatureSparkline`): reduced
  to 48 points max before reaching the UI — plenty for a widget-sized
  sparkline, no need to send the raw 288 points.
- **Separate, infrequent polling** (`DashboardViewModel.startSparklinePolling`):
  every 10 minutes, independent of the main Domoticz poll (5s). This call
  is more expensive (fetches a full history rather than a point-in-time
  state) and purely decorative — doesn't need the same freshness as a
  light's on/off state.
- **Eligibility determined after the fact**: the function looks at each
  widget's already-known state (`WidgetLiveState.Sensor` with `kind ==
  TEMPERATURE`, or `WidgetLiveState.Thermostat`) rather than declaring a
  separate type — avoids an unnecessary network call to the graph API for
  a humidity or rain sensor, which has no relevant temperature curve at
  that idx.
- **Rendering** (`WidgetCard.kt` → `Sparkline`): a minimalist `Canvas`, a
  line normalized between the min and max of the provided series, no axes
  or labels — purely a trend indicator, not a real analytical chart.
  Replaces/complements the space used by the gauge in `SensorContent`
  (mutually exclusive: the gauge only shows for humidity/percentage, the
  sparkline only for temperature — never both at once on the same widget).
- **Silent degradation**: if the graph call fails or returns an empty
  result (device with no history, unresponsive server...), no sparkline is
  shown — just the value alone, as before. Consistent with the rest of the
  project (RTSP snapshot, Hue color): best-effort, never blocking.
- **Not tested on a real device**: the exact format of the points returned
  by `range=day` may vary slightly depending on the Domoticz version — to
  be validated once connected to a real server.

## Light types (LIGHT / DIMMER / COLOR_LIGHT)

Domoticz exposes very different capabilities depending on the light type
(simple switch, dimmer, Hue color), hence three distinct `WidgetType`
values rather than a single generic one:

- **`light`**: simple switch. Tap = toggle on/off. Rendering unchanged.
- **`dimmer`**: adds brightness. Tap = toggle on/off (as before),
  **long-press** = opens `LightAdjustDialog` with +/- in 10% steps. The
  widget shows the brightness percentage below the label when on.
- **`color_light`**: adds color on top of brightness. Same interaction as
  `dimmer` (tap/long-press), but the modal also shows a **palette of 9
  preset colors** (warm/cool white + 7 hues) rather than a real HSV color
  picker — simpler to use by touch on a wall display, more than enough for
  a Hue bulb. The widget's icon and background tint with the active color
  when known.

**Auto-close of `LightAdjustDialog` after inactivity**: brightness and
color already send their command immediately on each tap (no separate
"Confirm" button) — without auto-close, an extra tap on "Close" was needed
after each adjustment, tedious on a wall display. Closing on the very first
tap would have broken step-by-step 10% adjustment (tapping several times in
a row to reach the desired brightness), so closing is triggered after
**1.5s of inactivity** rather than immediately: each action (brightness
+/- or color selection) resets the delay, allowing taps to be chained
without the modal closing in between, while still closing on its own once
the user stops interacting. The "Close" button remains available for an
explicit immediate close.

**`ThermostatAdjustDialog` unchanged**: it already closes automatically on
"Confirm" (the +/- doesn't send anything until confirmed, unlike
brightness which is "live") — the inactivity auto-close mechanism had no
reason to be added there.

**Automatic detection** (`DomoticzTypeMapper.kt`): Domoticz exposes
RGB/RGBW lights (Hue and similar, regardless of protocol — Hue Bridge,
Zigbee...) under `Type = "Color Switch"` → `COLOR_LIGHT`. A `SwitchType`
containing "Dimmer" without being a color switch → `DIMMER`. The rest with
`Type` containing "Light"/"Switch" → simple `LIGHT`. As with the rest of
the project's Domoticz heuristics, this is a best-effort to be adjusted
once tested against real hardware.

**Color — an accepted best-effort** (`DomoticzColorParser.kt`): Domoticz
encodes color in a `Color` field which is itself a JSON string with a mode
(`m`: white, temperature, RGB, custom...). Only the explicit RGB mode is
handled for now — white/temperature modes fall back to "no color
displayed" rather than inventing an approximate hue. For writing,
`DomoticzClient.setColor()` always sends explicit RGB mode (`m=3`) via the
Domoticz `setcolbrightnessvalue` command, untested on a real Hue bridge.

## Selector switch (SELECTOR)

Specific support for Domoticz "Selector Switch" devices, which allow choosing between several named modes (e.g., "Off", "Cinema", "Games").

- **Detection** (`DomoticzTypeMapper.kt`): Devices identified as `Light/Switch` with the `Selector` switch type.
- **Automatic Decoding**: The mode names are automatically extracted and decoded from the Base64-encoded `LevelNames` field provided by the Domoticz API.
- **UI Interaction**:
  - Tapping the widget opens a `SelectorAdjustDialog` displaying the full list of available modes.
  - The current mode is highlighted, and the widget card displays its name.
  - Selection sends a `switchlight` command with the corresponding `level` (multiples of 10) to Domoticz.

## Consistent theme (icons + tinted backgrounds based on state)

Lights (light/dimmer/color) and scenes already tinted their icon and
widget background based on state — the shutter and lock didn't follow this
convention (icon always `TextPrimary`, no tinted background). Fixed, with
a color convention now documented directly in `ui/theme/Color.kt` rather
than left implicit:

- **`AccentGreen` (`+Surface`)**: active/engaged state — light on, shutter
  open (>50%), active scene/group. This is the only case where "something
  is currently happening" deserves a color.
- **`AccentRed` (`+Surface`, new)**: attention/alert state — used
  **only** for an **unlocked** lock. Deliberately inverted compared to the
  rest: unlike a light where "on" = green, here it's the "locked" state
  that stays neutral (nothing to flag) and "unlocked" that catches the eye
  (security).
- **`AccentOrange`**: heat/energy (temperature, thermostat, UV, power
  consumption) — already consistent before this pass.
- **`AccentBlueMuted`**: "cold"/informative quantities (humidity, rain,
  weather) — already consistent before this pass.
- **`TextSecondary`/`TextMuted`**: neutral/inactive/unknown state, never
  paired with a tinted background.

**Shutter**: icon + green background if `percentOpen > 50`, gray otherwise
— same threshold already used for the `"toggle"` style. Applies to both
styles (`buttons` and `toggle`).

**Lock**: icon (different glyph depending on state, already in place) +
red tint only if unlocked. Locked stays visually neutral — it's the
default/expected state, not an "active" state to highlight.

**Thermostat left untouched**: no binary on/off state to represent (just a
continuous value), a fixed `AccentOrange` remains appropriate and
consistent with the temperature sensor (`SensorKind.TEMPERATURE` already
uses the same color).

## Scene / Group widget (SCENE)

Triggers a Domoticz scene ("Movie night", "Leaving home"...) or toggles a
group on/off, in one tap.

- **Distinct Domoticz resource**: unlike devices (`getdevices`),
  scenes/groups come from `type=command&param=getscenes` (since stable
  2023.2 — same migration as devices, checked before writing the code this
  time). A single call per poll cycle is enough for **all** scene widgets,
  rather than one call per widget like for individual devices
  (`DomoticzRepository.observeStates` separates the two).
- **Scene vs Group, an important Domoticz distinction**: a **Scene** is a
  trigger with no persistent state — Domoticz only allows `switchcmd=On`,
  never `Off`. A **Group** is a real on/off toggle switch, like a light.
  `WidgetLiveState.Scene.isGroup` carries this distinction:
  `SceneContent` only shows "Active"/"Inactive" for a Group.
- **Tap**: `DashboardViewModel.triggerScene()` — toggles on/off if a Group,
  always "On" if a Scene. Optimistic update like the other widgets, but for
  a real Scene the next poll (5s) will likely bring the state back to "Off"
  (Domoticz doesn't keep a durable state): the widget's green background
  flashes briefly after the tap then fades — intentional, serves as a
  "triggered" visual feedback rather than a real state.
- **Discovery**: a separate section in `AddWidgetDialog` ("Scenes &
  groups" above "Devices"), with its own `discoverDomoticzScenes()` call.
  The "already used" filter is computed separately from the devices one
  (`widgetType == SCENE` vs `!= SCENE`) — scenes and devices are two
  distinct Domoticz tables that could theoretically share the same `idx`
  number, mixing the two filters could have wrongly hidden a legitimate
  device or scene.
- **Not tested on a real device**, like the rest of the Domoticz client.

## Night mode (dimming + scheduled screen-off)

Two clearly distinct levels, with very different guarantees — important
not to confuse them.

### Level 1 — Dimming (always reliable, no permissions)

`ui/dashboard/NightModeEffect.kt` adjusts
`window.attributes.screenBrightness` according to the configured schedule,
checked every 60s. Only touches the app window's brightness, no system
permission required, works 100% of the time as long as the app is in the
foreground (which is continuously the case given the wall-display use case
targeted by the project). `power/NightModeSchedule.kt` computes the time
range (handles the case where it crosses midnight, e.g. 10pm → 7am)
independently of any Android code — pure logic, easily testable.

### Level 2 — Actual screen-off + automatic wake-up (best-effort, opt-in)

Much more fragile, documented as such in the code
(`ScreenPowerController.kt`):

- **Requires "Device Administrator" rights** (Device Admin), requested via
  a standard system dialog from the settings screen — **no** need for ADB
  or "Device Owner" (which would require provisioning the device before
  any account is set up). Minimal policy declared
  (`res/xml/device_admin_policies.xml`): only `force-lock`, nothing else.
- **Screen-off**: `DevicePolicyManager.lockNow()` at the scheduled time —
  actually locks/turns off the screen, unlike dimming.
- **Scheduling via `AlarmManager`** (`ScreenPowerController` +
  `ScreenAlarmReceiver`), not a simple `delay()` in a coroutine: survives
  the app being backgrounded. Each trigger explicitly reschedules for the
  next day (`setExactAndAllowWhileIdle`, not `setRepeating`, more reliable
  on recent Android).
- **`BootReceiver`** reschedules after a device reboot (`AlarmManager`
  alarms don't survive a reboot).
- **Wake-up**: relaunches `MainActivity` with the `showWhenLocked`/
  `turnScreenOn` flags (manifest, API 27+) and their deprecated but
  functional `WindowManager.LayoutParams` equivalents for older versions
  (`minSdk = 23`).

**Caveats to take seriously before relying on this:**

- The `SCHEDULE_EXACT_ALARM` permission is declared in the manifest, but on
  Android 12+ some manufacturers still require manual authorization
  (`Settings > Apps > Special access > Alarms & reminders`) — not
  automatically guaranteed.
- Aggressive battery managers from some manufacturers (MIUI, Samsung, etc.)
  can kill the app in the background and prevent the trigger despite the
  system alarm.
- **Wake-up may land on the lock screen** if the device has a
  PIN/pattern set — recommended to have **no lock screen** on a device
  dedicated to wall display for this to work properly.
- If the user revokes admin rights from system settings, `lockNow()` fails
  silently (`SecurityException` caught) — the setting stays enabled on the
  app side but has no effect until rights are granted again. No automatic
  detection/alert for this state yet.
- **None of this has been tested on a real device** — like the rest of
  this project's platform integrations, but this one in particular touches
  mechanisms that vary a lot from one manufacturer to another.

### Settings

"Night mode" section in `SettingsDialog`: start/end times (1h steps via
`HourStepper`), night brightness (5% steps via `PercentStepper`), and a
separate "Real screen-off" toggle that triggers the admin rights request
only if not already granted.

**Bug fixed along the way**: `SettingsDialog.onSave` was rebuilding a full
`AppSettings` without ever including `httpAuthToken`, which silently fell
back to `""` every time Domoticz settings were saved — breaking the HTTP
server's auth until the next full app restart
(`ConfigRepository.ensureHttpAuthToken` only regenerates it on initial
load, not on every `updateConfig`). The token is now explicitly preserved
from `initial.httpAuthToken`.

## Domoticz settings screen

**Closes a real usability gap**: until now, Domoticz host/port/credentials
were hardcoded (`DomoticzConfig()` with default values) — nobody could use
the app without recompiling it. That's no longer the case.

- **Model**: `AppSettings` (in `model/DashboardConfig.kt`) — new
  `settings` field at the root of `DashboardConfig`, additive (has a
  default value, no breaking change for a file already in the multi-page
  format). Editable via the settings screen **or** directly in the JSON via
  the embedded HTTP server — both go through the same
  `repository.updateConfig()`.
- **UI** (`SettingsDialog.kt`): gear button always visible (not only in
  edit mode, stacked below the pencil button), host/port/username/password/
  HTTPS form. Minimal validation (non-empty host, numeric port) before
  saving.
- **Hot reconfiguration** (`DashboardViewModel.updateDomoticzSettings`):
  persists the new settings, **explicitly closes** the old `DomoticzClient`
  (avoids leaking the HTTP connection), recreates a new one with the new
  config, and restarts polling — without restarting the app. Weather
  polling doesn't need this mechanism (each widget carries its own
  lat/lon, no global setting to change).
- **Still no global lat/lon for weather** — each weather widget keeps its
  own coordinates in its `WidgetSource` (already flexible, allows several
  cities across several widgets), so there's no corresponding field in
  `AppSettings`.
- **Not tested on a real device**, like the rest: in particular the hot
  swap of the Domoticz client while a poll is in progress — the window
  between canceling the old job and starting the new one should be
  instantaneous, but needs practical validation.

## Not done yet (intentionally)

- Android-Iconics / FontAwesome integration (dependencies commented out in
  `app/build.gradle.kts`)
- Distinguishing a transient WebSocket failure from a permanent one (e.g.
  wrong credentials) — see "Real-time device updates via Domoticz
  WebSocket" above.
- Surfacing `observeScenePolling()`'s REST failures in the connection
  badge (currently retried silently) — not needed if you don't use SCENE
  widgets.

## Drag & drop repositioning (cascading rearrangement)

Finger-based moving no longer simply rejects an invalid placement — it now
**pushes widgets along the way downward**, masonry-style (like
react-grid-layout), with a real-time preview that only commits when the
finger is released.

- `GridEngine.resolvePushLayout()` (pure, no side effects): computes the
  full layout if the moved widget were placed at a candidate position.
  Other widgets are pushed **vertically only** (`y` increases, `x`/`w`/`h`
  never change for them) — simpler and more predictable than a full 2D
  repacking. Processed in original order (`y` then `x`) for a stable
  result, cascading if one push triggers another.
- During drag (`DashboardScreen.kt` → `EditOverlay`): the dragged widget
  follows the finger **without lag** (raw pixel delta, no animation) and
  drops to **60% opacity** (the requested "grayed out" effect). Other
  widgets animate (`animateDpAsState`) toward their previewed position from
  `resolvePushLayout`, recalculated on every drag event — so if the finger
  moves away from a zone before releasing, the widgets that were pushed
  naturally return to their original position (nothing special needs to be
  coded for this, it's a direct consequence of continuous recalculation).
- **The actual commit only happens on release** (`onDragEnd`): the last
  computed preview is sent to `DashboardViewModel.applyLayout()`, which
  persists all affected widgets (moved + pushed) via
  `repository.updateConfig()` in a single call.
- `onDragCancel` (system gesture interruption): cancels without
  committing, everything reverts to the pre-drag state.
- **This change only affects moving.** Resizing (bottom-right corner
  handle) keeps the previous behavior: immediate commit at each valid
  step, silent rejection on overlap — no cascading push for resize.

**Repositioning the "gaps allowed" concept**: this drag-push system
coexists with the free-form grid described above. A widget added via
`findFirstFreeSlot` or moved manually in the JSON via the HTTP server can
still leave gaps — only touch-based dragging now triggers automatic
rearrangement.

## Points to watch on the current drag & resize

- **Not tested on a real device** — the cascade push algorithm is covered
  logically but the tactile feel (smoothness of `animateDpAsState` with
  several widgets moving at once, drag trigger zone vs. simple tap) needs
  real hands-on validation.
- No distinct visual feedback (e.g. a halo) if the final position goes
  beyond the grid's width (currently just silently clamped in
  `resolvePushLayout`).
- The tap zone on the resize handle is small (22dp): needs to be tested by
  touch on a real screen, potentially enlarging the tap zone beyond the
  visual via negative `Modifier.padding` or a larger invisible zone. Same
  note for the delete button (top-left corner, same size).
- **Widget deletion**: red "×" button in the top-left corner of each widget
  in edit mode (`EditOverlay`, symmetric to the resize handle), calls
  `viewModel.removeWidget()`. **No confirmation** before deletion —
  consistent with the rest of the app (`removePage` doesn't have one
  either), but worth reconsidering if it turns out to cause accidents in
  real use: an unlucky tap loses the widget (and its position/config)
  immediately, with no "undo".
- The visual margin added at the bottom of the grid during a drag (4 extra
  rows in `totalHeightDp`) is arbitrary — to be adjusted if deeper pushes
  turn out to be common in real use.

## Embedded HTTP server (browser-based editing)

- `data/ConfigRepository.kt` is now the **single source of truth**: a
  `StateFlow<DashboardConfig>` persisted in the app's internal storage
  (`filesDir/dashboard_config.json`), copied from the embedded asset on
  the very first launch.
- `server/ConfigHttpServer.kt` (Ktor CIO, started in
  `HomeHabitApp.onCreate`) exposes on port **8090**:
  - `GET /` — minimal JSON editing page (`server/ConfigEditorHtml.kt`)
  - `GET /config` — current JSON
  - `POST /config` — replaces the config (validated by parsing before
    writing)
- The ViewModel and the server share the **same instance** of
  `ConfigRepository` (exposed by `HomeHabitApp`), so any change — whether
  from touch drag & resize or from a `POST /config` from a browser — is
  immediately visible on both sides, with no manual synchronization.

### Authentication (simple token)

All routes now require a token — no more free read/write access for any
device on the network, which matters now that the config contains the
Domoticz password and drives real shutters/locks.

- **Generation** (`ConfigRepository.ensureHttpAuthToken`): 8 characters,
  restricted alphabet without ambiguous characters (no `0`/`O`, `1`/`I`/`L`)
  since the user might need to retype it. Generated once on the very first
  launch, never regenerated afterward as long as it isn't empty —
  persists with the rest of the config.
- **Verification** (`ConfigHttpServer.isAuthorized`): accepts the token
  either as a query param (`?token=...`) or in the
  `Authorization: Bearer ...` header. If no token is configured (shouldn't
  happen anymore), **denies everything by default** rather than opening
  access wide.
- **`GET /`** only accepts the query param (a regular page load can't carry
  an `Authorization` header), and **injects the token into the served
  page's JS** (`configEditorHtml(token)`) — subsequent `fetch()` calls to
  `/config` send it automatically via the header, the user only needs to
  enter it once in the URL.
- **In the app**, in edit mode, the displayed URL (top-left corner) already
  includes the token (`http://<ip>:8090/?token=XXXXXXXX`) — **tap to
  copy** to the clipboard (`LocalClipboardManager` + confirmation `Toast`),
  no more need to retype it by hand.
- **Still no HTTPS** (consistent with `usesCleartextTraffic="true"`
  already in place for Domoticz): the token travels in clear text on the
  local network. Consistent with the assumed trust level (home LAN), but
  **to be seriously revisited** if the app is ever exposed beyond the LAN
  — a cleartext token on an untrusted network provides essentially no real
  protection.
- No screen to regenerate the token from within the app — only by clearing
  the app's data (the next launch generates a new one) or by editing the
  JSON directly.

## Camera stream (RTSP)

- `camera/RtspPlayer.kt` — wrapper around libVLC 3.7.0. One instance = one
  playback session, created/released with the modal's lifecycle: **no
  background RTSP playback**, the stream only runs while the modal is
  open (important for CPU/network load given the screen is on
  continuously).
- `ui/dashboard/CameraStreamModal.kt` — full-screen modal: tapping a camera
  widget (only if `source.rtspUrl` is set in the config) → opens
  immediately, RTSP connection started in parallel. While connecting,
  shows `source.url` (snapshot) desaturated to black and white
  (`ColorMatrix.setToSaturation(0f)` via Coil) as a blurred background,
  with a "Connecting to stream..." indicator. As soon as libVLC reaches
  the `Playing` state, it **waits an additional 300ms** before considering
  the stream "visually ready" and starting the fade (600ms) to the color
  video. This delay exists because libVLC's `Playing` event signals an
  internal state change, not that a frame has actually been rendered on
  screen (RTSP negotiation, waiting for a keyframe, hardware decoding
  startup) — without it, the fade risked starting before there was an
  actual image, causing a black flash between the poster and the stream.
  Same precautionary logic already used in `RtspThumbnailGrabber` for
  snapshot capture. Not tested on a real device — the 300ms value is an
  estimate, not a measurement.
- Current libVLC options: `--no-audio` (no sound), `--rtsp-tcp` (RTSP over
  TCP, more reliable than UDP on a home network), network cache reduced to
  300ms to limit latency.

### Snapshot in the widget (implemented)

`WidgetCard.kt` → `CameraContent` now handles three cases, in this priority
order:

1. **`source.url` set** → `SnapshotImage` loads the image via Coil and
   refreshes it every `source.refreshSeconds` seconds (5s minimum), with
   simple cache-busting (`?_t=<timestamp>`) to force a reload rather than
   serving a cached version.
2. **`source.url` empty but `source.rtspUrl` set** →
   `RtspFallbackThumbnail` attempts a *best-effort* capture of a frame
   from the RTSP stream via `RtspThumbnailGrabber` (see
   `camera/RtspThumbnailGrabber.kt`). Deliberately less reliable than a
   real HTTP snapshot: the capture opens a real RTSP connection
   (expensive), and the method depends on libVLC's internal rendering
   (`TextureView.getBitmap()` or `PixelCopy` depending on the device —
   `PixelCopy` unavailable before Android 7.0/API 24). On failure, it
   simply falls back to the generic placeholder, without blocking
   display. The actual interval is capped at a 30s minimum regardless of
   `refreshSeconds`, to avoid opening an RTSP connection in a tight loop.
3. **Neither one nor the other** → generic placeholder (icon).

To be tested on a real device: RTSP capture behavior (case 2) varies
significantly depending on the camera's firmware/codec and the Android
device — this is an accepted best-effort, not a guarantee.

### What's still missing on the camera side

- No automatic reconnection handling if the RTSP stream drops while being
  viewed in the full-screen modal (`ERROR` state shown, but no retry).
- No testing on a real device with a real RTSP camera — libVLC's behavior
  varies significantly depending on the camera's codec/firmware (H.264 vs
  H.265, profiles, etc.), needs practical validation.

## "Last updated" badge

- Each widget now shows a small, discreet badge in the top-right corner
  (`WidgetCard.kt` → `LastUpdateBadge`): "just now" / "Xmin ago" / "Xh ago"
  below 24h, then `DD/MM` beyond that.
- `data/WidgetStateEntry.kt` associates each `WidgetLiveState` with its
  timestamp — introduced without touching the existing sealed class, so as
  not to propagate the change into every variant (Weather, Light, etc.).
- **Domoticz widgets**: the timestamp comes from the server's real
  `LastUpdate` field (not the local poll time), parsed from Domoticz's
  `yyyy-MM-dd HH:mm:ss` format. **Known limitation**: this format contains
  no timezone, so it's assumed the Domoticz server and the phone are in
  the same timezone (normal case on a home network, but worth keeping in
  mind).
- **Demo widgets** (weather, camera): timestamp = the moment the app
  loaded, since there isn't yet a real refreshing data source for them.
- The badge refreshes itself every 30s (`LaunchedEffect` local to
  `LastUpdateBadge`) so the relative text stays accurate without waiting
  for a new business event.
- **Not a connectivity indicator**: this badge only reflects when the
  *value itself* last changed, so a widget untouched for hours looks
  identical whether everything is fine or the server is unreachable. See
  the `ConnectionStatusBadge` described in "Real-time device updates via
  Domoticz WebSocket" above for that distinction.

## Opening the project

Open the `homehabit/` folder in Android Studio (Koala or newer). Gradle
will generate the wrapper on the first sync. `minSdk = 23` (Android 6.0),
`compileSdk = 34`.

**No launcher icon yet**: `AndroidManifest.xml` no longer references
`android:icon` (deliberately removed, no mipmap resource exists yet in the
project — referencing it without providing one would have made
`processDebugResources` fail). The app will use Android's default icon
until a real icon set (`res/mipmap-*/ic_launcher.png` or an adaptive icon)
is added.

**Notes on the manifest**: two fixes were made after the fact — the
`xmlns:android` namespace mistakenly pointed to `res-auto` instead of
`res/android` (preventing any `android:*` attribute from resolving
correctly, the cause of the first `processDebugMainManifest` failure), and
`android:extractNativeLibs="true"` + `tools:replace="android:extractNativeLibs"`
were added because libVLC embeds its own manifest that forces this value,
conflicting with AGP's default. Since then, night mode has added
`RECEIVE_BOOT_COMPLETED` and `SCHEDULE_EXACT_ALARM` (permissions), plus
three `<receiver>` entries (`HomeHabitDeviceAdminReceiver`,
`ScreenAlarmReceiver`, `BootReceiver` — see dedicated section above) and
`showWhenLocked`/`turnScreenOn` on `MainActivity` (wake-up after scheduled
screen-off).

## Project status

All the building blocks listed in the initial request are covered:
Domoticz (read + write + discovery), weather (current + 7-day forecast),
camera (snapshot + RTSP), configurable dashboard (JSON + authenticated
HTTP server + native settings screen), multi-page with swipe, drag &
resize with cascading rearrangement, consistent theme, and night mode.
Domoticz device state is now driven by a real-time WebSocket channel
(automatic reconnect + resync, connection status badge) instead of
periodic polling — see "Real-time device updates via Domoticz WebSocket".
