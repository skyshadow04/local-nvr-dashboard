# Local NVR Dashboard

Local NVR Dashboard is a self-hosted dashboard for viewing IP cameras connected
to Hikvision or Uniview NVRs. It discovers camera channels, registers their
streams with MediaMTX, and plays them in the browser using WebRTC. The dashboard
is intended for use on a trusted local network; it does not require a cloud
video service.

## How it works

1. The Next.js dashboard lets an operator enter NVR connection details and
   choose camera channels and stream profiles.
2. The dashboard server configures MediaMTX with the camera's RTSP stream.
   RTSP is pulled on demand, so an unselected camera does not use a stream.
3. The browser negotiates a WebRTC connection with MediaMTX and displays the
   selected video feeds. Camera names are saved in the dashboard's data volume.

The main parts of the project are:

- `app/page.tsx` — dashboard interface, camera selection, and layouts.
- `components/VideoPlayer.tsx` — browser WebRTC player and reconnect handling.
- `app/api/stream/route.ts` — camera discovery and MediaMTX stream management.
- `app/api/whep/[streamId]/route.ts` — proxies WebRTC (WHEP) negotiation.
- `mediamtx.yml` — MediaMTX settings, including the WebRTC address advertised
  to browsers.
- `docker-compose.yml` and `Dockerfile` — run the dashboard and MediaMTX.

## Requirements

- Docker Engine and Docker Compose.
- A machine that can reach the NVRs over the local network (RTSP on TCP port
  `554`).
- Browser client devices and the Docker host on a network that can pass WebRTC
  traffic.
- H.264 camera streams. Browser WebRTC playback may not support H.265/HEVC.

## Run on a local network

1. Find the LAN IPv4 address of the machine that will run Docker. On Windows,
   run `ipconfig` and use the IPv4 address of the network adapter connected to
   the same network as the client PCs.
2. Edit `mediamtx.yml` and set `webrtcAdditionalHosts` to that address:

   ```yaml
   webrtcAdditionalHosts: [192.168.1.50]
   ```

   Replace the example address with the Docker host's actual LAN address. Keep
   this setting up to date if that address changes; a DHCP reservation or static
   LAN address is recommended.
3. Start the services from the project directory:

   ```bash
   docker compose up -d --build
   ```

4. On another PC on the same LAN, open `http://<DOCKER_HOST_LAN_IP>:3001`.
   Do not use `localhost` or `127.0.0.1` on that PC; those addresses refer to
   the PC itself.
5. Enter the NVR's IP address, username, password, channel range, and stream
   profile in the dashboard, then connect the cameras.

Allow these network paths through the Docker host's firewall:

| Port | Protocol | Purpose |
| --- | --- | --- |
| `3001` | TCP | Dashboard web page and API |
| `8190` | UDP | WebRTC camera media between browsers and MediaMTX |
| `554` | TCP | RTSP from the Docker host to each NVR |

The dashboard and MediaMTX also communicate over Docker's internal network
using MediaMTX API port `9997` and WebRTC/WHEP port `8889`. Browser clients
should use the dashboard URL above; they do not need to access the MediaMTX API.

## Troubleshooting

### Dashboard does not open from another PC

- Confirm both PCs are on the same LAN and use the Docker host's current LAN IP
  in the URL.
- Confirm the dashboard container is running with `docker compose ps` and check
  its logs with `docker compose logs dashboard`.
- Allow inbound TCP port `3001` on the Docker host firewall.
- Some guest Wi-Fi networks isolate clients from each other. Use a network that
  allows the browser PC to reach the Docker host.

### Cameras keep loading or fail to connect

- Confirm `webrtcAdditionalHosts` in `mediamtx.yml` matches the Docker host's
  LAN IPv4 address, not the NVR address or a container address.
- Allow inbound UDP port `8190` on the Docker host firewall. Web pages can load
  successfully even when this separate WebRTC media path is blocked.
- Restart MediaMTX after changing its configuration:

  ```bash
  docker compose restart mediamtx
  ```

- Confirm the Docker host can reach each NVR on TCP port `554`, and verify the
  NVR address, credentials, channel, and stream profile.
- Use an H.264 stream profile. H.265/HEVC streams may be rejected by the
  browser's WebRTC decoder.
- Check service logs with `docker compose logs mediamtx` and
  `docker compose logs dashboard`.

If the dashboard is accessed from outside the local network, the advertised
WebRTC address and media traffic must also be routable from that client. This
setup is designed for a trusted LAN; do not expose it directly to the public
internet.

## Native Android viewer

The `android-app` directory contains a native Kotlin Android viewer for the
camera streams already registered in the dashboard. It lists configured
cameras and opens one live camera at a time; use the web dashboard to add or
remove camera streams.

1. Install Tailscale on the Android phone and sign in to the same tailnet as the
   Docker host.
2. Open `android-app` in Android Studio, install Android SDK 35 if prompted,
   and run the `app` configuration on a phone or emulator.
3. In the app, enter the dashboard address reachable over Tailscale, for
   example `http://<DOCKER_HOST_TAILSCALE_IP>:3001`, then tap **Connect**.
4. Select a camera to start its WebRTC stream. Keep Tailscale connected while
   viewing.

The app uses the dashboard's `/api/stream` and `/api/whep` endpoints. It does
not connect directly to the MediaMTX management API or store NVR credentials.
The current setup permits HTTP for private-network compatibility; use it only
over Tailscale or a trusted LAN, never over the public internet. For a public
or app-store deployment, configure HTTPS and application-level authentication
before exposing the dashboard.

## Native iOS viewer

The `ios-app` directory contains a native SwiftUI viewer for the same cameras.
It lists streams configured in the web dashboard and plays one camera at a
time using WebRTC. Camera setup remains in the web dashboard.

1. On a Mac with Xcode 16 or newer, open `ios-app/LocalNVR.xcodeproj`.
2. Let Xcode download the pinned WebRTC Swift package and select your Apple
   Developer signing team for the `LocalNVR` target.
3. Install Tailscale on the iPhone and sign in to the same tailnet as the
   Docker host.
4. Run the app on the phone, enter `http://<DOCKER_HOST_TAILSCALE_IP>:3001`,
   tap **Connect / Refresh**, and select a camera.

The iOS app allows unencrypted HTTP to support the current private Tailscale
setup. Use it only on a trusted LAN or tailnet. Before distributing the app or
using it outside Tailscale, configure HTTPS and application-level
authentication, then remove the broad HTTP exception from `Info.plist`.

## Stop

```bash
docker compose down
```

Camera names and related dashboard data are kept in the `dashboard-data`
Docker volume when the containers are stopped.