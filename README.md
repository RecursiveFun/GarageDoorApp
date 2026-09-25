# Garage Door Android App

Geofence-triggered garage door control for the ESP32-C6 controller via your public Cloudflare tunnel.

## Features

- Manual open/close and live status from `/api/status`
- **Custom polygon boundary** on OpenStreetMap (tap corners)
- **Coarse circular geofence** wakes the app when you are nearby (battery-friendly)
- **Auto-open** when you enter the polygon (no confirmation)
- Works over **cellular** via your Cloudflare tunnel hostname
- Encrypted API key storage

## Setup

1. Open the project in **Android Studio** (Ladybug or newer recommended).
2. Copy `local.properties.example` → `local.properties` (gitignored).
3. Set `sdk.dir` to your Android SDK path.
4. Build and install on your phone:

   ```bash
   cd GarageDoorApp
   .\gradlew.bat installDebug
   ```

   Or use Android Studio **Run**.

## First-run wizard

1. **API URL** — your Cloudflare tunnel hostname (e.g. `https://garage-api.example.com`)
2. **API key** — same as `API_KEY` in ESP32 `include/config.secrets.h`
3. **Draw boundary** — tap 3+ corners around your driveway / street approach
4. Enable **Auto-open on arrival** (on by default — test manually first and draw a tight boundary)

Tap **Test & save credentials** to verify the connection before relying on auto-open.

## Authentication

The app sends your API key directly as a Bearer token:

```
Authorization: Bearer <API_KEY>
```

This works with the Cloudflare tunnel, which should expose **only** `/api/status` and `/api/trigger`. The app does **not** call `/api/auth/token` (that path is blocked by the tunnel and is only needed for the ESP32 web UI on LAN).

The ESP32 accepts either the API key or a JWT on protected routes. The Android app always uses the API key.

## How auto-open works

```
At home (disarmed)
  → Notification: “Auto-open ready”
  → GPS completely off

Leave neighborhood (coarse EXIT)
  → Arms for next approach
  → Notification: “Auto-open armed”
  → GPS still off

Approach home while armed (coarse ENTER)
  → Brief high-accuracy GPS (~90 s)
  → Enter drawn polygon → POST /api/trigger
  → Back to disarmed (GPS off)

If still near home after arrival window without entering polygon
  → Low-power GPS every ~15 s until trigger or you leave again
```

## Permissions

- Fine + **background** location (works with the app closed)
- Notifications (required for the persistent status notification)

## Battery notes

- **No location polling while disarmed** (sitting at home).
- **No location polling while armed and away** — only the coarse geofence.
- GPS runs only when armed and approaching / near home.

## Project structure

```
app/src/main/java/com/garagedoor/app/
├── data/           Settings, API repository
├── network/        Retrofit + Bearer API key auth
├── geofence/       Geofence + polygon arrival processor
└── ui/             Compose screens
```

## ESP32 API (tunnel-exposed)

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/status` | `Bearer <API_KEY>` |
| POST | `/api/trigger` | `Bearer <API_KEY>` |

Cloudflare tunnel ingress should allow only these paths; everything else (web UI, OTA, auth token) returns 404:

```yaml
ingress:
  - hostname: garage-api.example.com
    path: /api/status
    service: http://ESP32_LAN_IP:80
  - hostname: garage-api.example.com
    path: /api/trigger
    service: http://ESP32_LAN_IP:80
  - hostname: garage-api.example.com
    service: http_status:404
  - service: http_status:404
```

## Safety

- Auto-open sends a **toggle pulse** — it does not know if the door is open or closed.
- Draw a tight polygon around your actual arrival path, not your whole property.
- False GPS drift can still trigger if the boundary is too large.
- Disable auto-open in the app menu if you see unexpected triggers.
