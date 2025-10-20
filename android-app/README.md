# Proxy Bridge Android App

This Android application replicates the behavior of the original `dark_server_cors.js` bridge by hosting both an HTTP server and a WebSocket server directly on the device. Incoming HTTP traffic is forwarded to the first connected browser client through the WebSocket channel and streamed back to the HTTP caller, maintaining full CORS coverage and request timeout handling.

## Key Features

- Embedded HTTP server with CORS headers that mirrors the Node.js implementation
- Embedded WebSocket server for relaying requests to a connected browser automation client
- Streaming response support with keep-alive packets for `text/event-stream`
- Foreground service with partial wake lock and Wi-Fi lock management to keep the bridge reliable
- Local log broadcasting for visibility inside the UI

## Building

```bash
gradle -p android-app assembleDebug
```

The repository does not include the Gradle wrapper binaries. Install Gradle 8.7 (or newer) locally, or rely on the provided GitHub Actions workflow which sets up the toolchain automatically. When the workflow runs on GitHub, it now publishes the generated `app-debug.apk` as an artifact named **`proxy-bridge-debug-apk`** so you can download the build output directly from the Actions run summary.
