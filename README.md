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
