# Tileborn

Online top-down game built with libGDX for desktop, Android, and a dedicated authoritative server.

## Modules

- `shared` — platform-independent protocol and shared data.
- `client` — client application, rendering, input, and presentation.
- `server` — dedicated authoritative server.
- `desktop` — desktop client entry point (LWJGL3).
- `android` — Android client entry point.

## Documentation

- [Architecture](./ARCHITECTURE.md)
- [Codex/agent rules](./AGENTS.md)

## Build

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`.

## MVP checkpoint

The first technical MVP includes a dedicated authoritative server, desktop and Android
clients, Tiled/Box2D wall collision, local prediction, remote interpolation, reconnect,
and the F3 debug overlay (FPS, ping, server tick, position, and entity counts).

Start the server with `gradlew.bat :server:run`, then start the desktop client with
`gradlew.bat :desktop:run`. The desktop client uses `127.0.0.1:54555` by default.

The Android build defaults to `10.0.2.2:54555`, which reaches the host machine from an
Android Emulator. For a physical device, build with a reachable server address:

```shell
gradlew.bat :android:assembleDebug -PserverHost=192.168.1.20 -PserverPort=54555
```

Run the automated MVP smoke checks with:

```shell
gradlew.bat test
```
