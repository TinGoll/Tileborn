# ARCHITECTURE.md

## Overview

This project is an online top-down game built with libGDX.

The game has three main runtime targets:

```text id="u2ka8l"
Desktop client
Android client
Dedicated authoritative server
```

The early MVP uses primitive rendering instead of final graphics:

```text id="p3myfi"
players = circles
walls = rectangles / Tiled collision objects
zones = transparent debug shapes
direction = lines/arrows
```

The goal of the architecture is to build a stable online-game foundation first:

```text id="xm3fje"
server-authoritative world
ECS-based simulation
centralized asset loading
Tiled map support
Box2D collision
desktop + Android clients
simple multiplayer synchronization
```

Final graphics, advanced combat, quests, inventory, economy, and large-scale MMO systems are intentionally postponed.

---

## High-level architecture

```text id="jbgqhk"
                        ┌────────────────────┐
                        │ Dedicated Server   │
                        │                    │
                        │ ECS World          │
                        │ Box2D Physics      │
                        │ Tiled Map Data     │
                        │ Game Loop          │
                        │ Networking         │
                        └─────────▲──────────┘
                                  │
                                  │ snapshots / events
                                  │ input / commands
                                  ▼
        ┌─────────────────────────────────────────────────┐
        │                  Network Protocol                │
        │          shared messages, ids, constants         │
        └─────────────────────────────────────────────────┘
                 ▲                                 ▲
                 │                                 │
                 ▼                                 ▼
┌────────────────────────┐             ┌────────────────────────┐
│ Desktop Client         │             │ Android Client         │
│                        │             │                        │
│ libGDX Rendering       │             │ libGDX Rendering       │
│ Input                  │             │ Touch Input            │
│ ECS Client World       │             │ ECS Client World       │
│ Prediction             │             │ Prediction             │
│ AssetManager           │             │ AssetManager           │
└────────────────────────┘             └────────────────────────┘
```

The server owns the real game state.

Clients own:

```text id="tm8los"
rendering
input collection
local prediction
interpolation
UI
audio
debug overlay
```

The server owns:

```text id="zn5jum"
real entity state
physics
collision
gameplay rules
validation
world snapshots
persistence decisions
anti-cheat boundaries
```

---

## Module structure

Current Gradle module structure:

```text id="hdj3n3"
shared/
  src/main/kotlin/game/shared/
    ecs/
    protocol/
    constants/
    math/
    ids/
    map/
    serialization/

client/
  src/main/kotlin/game/client/
    app/
    screens/
    assets/
    input/
    network/
    render/
    prediction/
    interpolation/
    debug/
    world/

server/
  src/main/kotlin/game/server/
    app/
    loop/
    network/
    world/
    physics/
    map/
    persistence/
    interest/
    validation/

desktop/  # game.desktop; depends on client
android/  # game.android; depends on client
assets/
```

Dependency direction is `desktop -> client -> shared`, `android -> client -> shared`, and
`server -> shared`. The server does not depend on client or platform modules.

---

## Shared module

The shared module contains code used by both clients and server.

Allowed in `shared`:

```text id="k87e9h"
protocol messages
protocol version
entity ids
component data models when safe
game constants
coordinate conversion helpers
math helpers
map gameplay metadata models
serialization helpers
basic validation utilities
```

Not allowed in `shared`:

```text id="s5wdii"
client rendering
client screens
Android lifecycle code
desktop launcher code
database access
server-only persistence
server socket implementation
client-only AssetManager code
```

The shared module should stay boring and stable.

---

## Client architecture

The client is responsible for input, rendering, local prediction, interpolation, and presentation.

Client flow:

```text id="db5bss"
Input → InputCommand → Network → Server
ServerSnapshot → Client World → Prediction/Reconciliation → Render
```

Main client responsibilities:

```text id="aomr4b"
collect player input
send input commands to server
receive authoritative snapshots
predict local player movement
interpolate remote entities
render Tiled map
render primitive entities
render debug overlay
manage assets
handle loading screens
handle Android pause/resume
```

The client must not decide authoritative gameplay results.

---

## Server architecture

The server is the source of truth.

Main server responsibilities:

```text id="by9xke"
accept client connections
validate protocol version
create player sessions
load gameplay map data
create ECS world
run fixed tick game loop
simulate movement and physics
process collisions
apply gameplay rules
filter visible entities per client
send snapshots and events
handle disconnect/reconnect
persist important state
```

The server must be able to run without graphics.

The server should not depend on rendering-only assets.

---

## ECS architecture

The game uses ECS from the beginning.

Basic rule:

```text id="w8nljz"
Entity = identity
Component = data
System = behavior
```

Example entity:

```text id="oz9x03"
Player entity:
  NetworkIdentityComponent
  TransformComponent
  VelocityComponent
  PhysicsBodyComponent
  PlayerInputComponent
  HealthComponent
  RenderPrimitiveComponent
```

Example server-only components:

```text id="xircsi"
ClientSessionComponent
InterestAreaComponent
PersistenceComponent
ServerAuthorityComponent
```

Example client-only components:

```text id="ce8nbx"
InterpolatedTransformComponent
PredictedInputComponent
RenderPrimitiveComponent
CameraTargetComponent
```

Example shared components:

```text id="am9mnv"
TransformComponent
VelocityComponent
HealthComponent
NetworkIdentityComponent
MapIdentityComponent
```

Shared components should only contain data that is meaningful on both client and server.

---

## ECS system order

### Server tick order

```text id="udxmsz"
1. Receive network messages
2. Decode and validate messages
3. Store input commands
4. Apply input commands to ECS
5. Run gameplay systems
6. Step Box2D physics
7. Sync Box2D bodies back to ECS transforms
8. Process collision events
9. Spawn/despawn entities
10. Update interest management
11. Build snapshots
12. Send snapshots/events
13. Run persistence checkpoints if needed
```

### Client frame order

```text id="ch2w35"
1. Poll platform input
2. Convert input to game commands
3. Send input commands
4. Receive server messages
5. Apply snapshots/events
6. Reconcile local predicted player
7. Interpolate remote entities
8. Update camera
9. Render map
10. Render entities
11. Render debug overlay
```

System order must be explicit.

Do not rely on accidental collection ordering.

---

## Asset architecture

The client uses a centralized asset loading layer.

Recommended classes:

```text id="h4ihqq"
GameAssetManager
AssetDescriptors
MapAssetRegistry
LoadingScreen
AssetScope
```

Asset types:

```text id="4pvlr0"
Tiled maps
textures
texture atlases
fonts
sounds
music
skins
debug assets
```

Early MVP may use very few assets, but the asset manager should exist from the beginning.

Even primitive rendering benefits from a loading flow because maps, fonts, skins, debug textures, and later atlases will need lifecycle management.

Asset loading flow:

```text id="oy4ltq"
1. Queue assets
2. Show loading screen
3. Update AssetManager
4. Enter game screen after loading completes
5. Unload scoped assets when no longer needed
```

Do not create disposable resources randomly inside gameplay systems.

Bad:

```java id="l4wfs4"
Texture texture = new Texture("player.png");
```

Good:

```java id="ybx99x"
Texture texture = assets.get(AssetDescriptors.PLAYER);
```

---

## Map architecture

Maps are created in Tiled.

The client uses the map for:

```text id="vtmulv"
visual tile rendering
debug display
camera bounds
local collision preview if needed
```

The server uses the map for:

```text id="mv807l"
collision geometry
spawn points
portals
safe zones
trigger areas
gameplay metadata
```

Recommended Tiled layers:

```text id="29s43z"
ground
decorations
collision
spawn_points
npc_spawn_points
triggers
portals
objects
debug
```

Recommended map object types:

```text id="b7i7r5"
spawn
collision
portal
safe_zone
damage_zone
interaction
npc_spawn
```

Example Tiled custom properties:

```text id="k1elsd"
type = spawn
spawnId = default

type = npc_spawn
spawnId = slime_camp
mobDefinitionId = slime
maxAlive = 3
respawnSeconds = 5
spawnRadius = 2

type = portal
targetMap = dungeon_01
targetSpawn = entrance

type = safe_zone
pvp = false
```

Map loading should produce a gameplay map model:

```text id="695auq"
GameMapData
  mapId
  width
  height
  tileSize
  collisionShapes
  spawnPoints
  portals
  zones
  objectMetadata
```

This model should be usable by the server without requiring rendering logic.

---

## Physics architecture

Box2D is used for collision and physics.

The server physics world is authoritative.

Client physics may exist only for:

```text id="98hyl5"
prediction
debug visualization
local movement feel
temporary collision approximation
```

The server must use a fixed timestep.

Recommended:

```text id="al6xrp"
20 server ticks per second
fixed tick duration = 50 ms
```

If physics needs more precision, the server may run more physics steps internally, but authoritative snapshots can still be sent at a lower rate.

Coordinate conversion must be centralized:

```text id="mn5hjo"
1 tile = 32 pixels
1 Box2D meter = 32 pixels
1 world unit = 1 meter
```

Avoid scattered magic numbers.

Use helpers:

```text id="1kfqso"
WorldUnits.toPixels(...)
WorldUnits.toMeters(...)
TileUnits.toWorld(...)
```

---

## Networking architecture

The network protocol has three major flows:

```text id="n0suif"
connection/session
client input/commands
server snapshots/events
```

### Client to server

```text id="na9t3h"
JoinRequest
InputCommand
InteractCommand
AttackCommand
ChatMessage
PingRequest
DisconnectNotice
```

### Server to client

```text id="mfyq8i"
JoinAccepted
JoinRejected
WorldSnapshot
EntitySpawned
EntityDespawned
CorrectionMessage
GameEvent
ChatBroadcast
PongResponse
ServerError
```

Each message should include:

```text id="ekdqlr"
message type
protocol version
sequence/tick when relevant
```

Input commands should include:

```text id="hvc96s"
clientTick
inputSequence
movement direction
action flags
aim direction if needed
```

Snapshots should include:

```text id="k84y8k"
serverTick
entities
events
acknowledged input sequence
```

---

## Protocol version

The protocol version must be explicit.

Example:

```java id="ecyngf"
public final class Protocol {
    public static final int VERSION = 1;
}
```

Connection flow:

```text id="bqi8ii"
client connects
client sends JoinRequest with protocol version, nickname, and optional reconnect token
server validates version and nickname
server resumes only the session identified by a valid reconnect token
server creates a new guest session when no valid token is present
server accepts or rejects
```

Nickname is presentation data, not authentication or session identity. Matching nicknames
must never replace an active connection or grant access to another character's persisted state.

Protocol changes that break compatibility must update the protocol version.

Current migration note: protocol v9 replaces the generic hit result with explicit
`AttackStartedEvent`, `HitEvent`, `DamageEvent`, and `EntityDiedEvent` messages. Damage
remains absent from `AttackCommand`: hit detection creates a server-owned `DamageEvent`,
and only `DamageSystem` mutates health. Applied damage events include authoritative
current and maximum health for client reconciliation. Protocol v8 clients are rejected.

---

## Authoritative movement

Movement is input-based.

Client sends:

```text id="gm321b"
move up = true
move down = false
move left = true
move right = false
input sequence = 152
```

Server decides:

```text id="myj58b"
new position
new velocity
collision result
final transform
```

Server sends:

```text id="bhstfb"
server tick
entity id
position
velocity
state
acknowledged input sequence
```

The client may predict local movement for responsiveness, but must reconcile with server state.

---

## Client prediction

Local player movement should feel responsive.

Prediction flow:

```text id="f8lh9u"
1. Client captures input
2. Client applies input locally
3. Client sends input to server
4. Server simulates input
5. Server sends authoritative state
6. Client compares prediction with server state
7. Client corrects error
```

Correction policy:

```text id="mdbrnq"
small error → smooth correction
large error → snap to server state
```

Remote players should use interpolation instead of full prediction.

---

## Interpolation

The client should buffer server snapshots for remote entities.

Instead of rendering remote players at the newest received position immediately, render slightly behind real time.

Example:

```text id="o13iot"
server sends snapshots at 20 Hz
client renders at 60 FPS
client interpolates between snapshot A and snapshot B
```

This reduces jitter.

---

## Interest management

The server should not send the whole world to every client forever.

MVP strategy:

```text id="8jrbxl"
split map into chunks
track entity chunk
client observes own chunk and neighboring chunks
```

Alternative simple strategy for very early MVP:

```text id="5yh8by"
send entities within radius N from player
```

Interest management decides:

```text id="24a8ic"
which entities are visible to each client
which entity spawns/despawns must be sent
which updates can be skipped
```

---

## Persistence architecture

The server keeps active game state in memory.

Persisted data:

```text id="8gcsb4"
account
character
saved map id
saved position
inventory
progression
settings
important world state
```

In-memory data:

```text id="q64lop"
current physics bodies
temporary movement state
active projectiles
temporary mobs
short-lived effects
current snapshot buffers
```

Save triggers:

```text id="4x4z6c"
disconnect
map transition
periodic checkpoint
important gameplay event
server shutdown
```

Do not save high-frequency state every tick.

---

## Reconnect architecture

Reconnect is important, especially for Android.

Basic MVP behavior:

```text id="1wxe44"
client disconnects
server keeps session for short timeout
client reconnects with token
server restores session if possible
otherwise creates a new guest session
```

Recommended timeout:

```text id="5f3k9c"
5-30 seconds for MVP
```

Reconnect should preserve:

```text id="u05xri"
character id
map id
position
basic state
```

Reconnect does not need to preserve every temporary action in the first MVP.

---

## Input architecture

Input should be platform-independent after the first layer.

Platform-specific input:

```text id="iajyku"
KeyboardInputSource
TouchInputSource
GamepadInputSource
```

All input sources produce the same game input model:

```text id="66peda"
GameInputState
  moveX
  moveY
  attack
  interact
  aimX
  aimY
```

The rest of the game should not care whether input came from keyboard, touch joystick, or gamepad.

---

## Rendering architecture

Rendering reads ECS state.

Rendering must not change gameplay state.

Recommended render systems:

```text id="7o5fok"
MapRenderSystem
PrimitiveRenderSystem
DebugShapeRenderSystem
NameplateRenderSystem
DebugOverlaySystem
```

MVP primitive render components:

```text id="uimv3u"
RenderPrimitiveComponent
  shape = circle | rectangle | line
  color
  radius
  width
  height
```

Later this can be replaced or extended with sprite/animation components.

Do not block MVP progress on final art.

---

## Debug architecture

Debug tools are part of the architecture, not an afterthought.

Minimum debug overlay:

```text id="a41x4k"
FPS
ping
client tick
server tick
current map id
local player position
entity count
visible entity count
prediction error
connection state
```

Debug draw modes:

```text id="fktdn5"
collision shapes
Box2D bodies
entity ids
chunk boundaries
interest radius
spawn points
trigger zones
```

Debug tools should be toggleable.

---

## Development milestones

### Milestone 1: Local foundation

```text id="c3tnmd"
libGDX desktop runs
libGDX Android runs
ECS initialized
AssetManager wrapper exists
Tiled map loads
primitive player renders
camera follows player
```

### Milestone 2: Physics and map

```text id="ckb73f"
Tiled collision layer parsed
Box2D world created
player collides with walls
debug collision rendering works
coordinate conversion is centralized
```

### Milestone 3: Server foundation

```text id="a8yxav"
server starts
server loads gameplay map data
server creates ECS world
server runs fixed tick loop
server simulates basic player entity
```

### Milestone 4: First network connection

```text id="tp3qdj"
client connects
protocol version checked
join accepted/rejected
client receives initial world state
ping/pong works
```

### Milestone 5: Multiplayer movement

```text id="a1whrb"
client sends input commands
server applies movement
server sends snapshots
two clients see each other
server corrects invalid movement
```

### Milestone 6: Client feel

```text id="3tohkn"
local prediction
server reconciliation
remote interpolation
debug prediction error
stable movement under artificial latency
```

### Milestone 7: Android viability

```text id="96tb59"
touch controls
pause/resume handling
reconnect after backgrounding
acceptable performance on Android
```

### Milestone 8: First gameplay interaction

```text id="wqebdv"
interact with trigger
portal or zone works
simple chat or simple object interaction
server validates interaction
```

---

## Initial MVP definition

The first true MVP is complete when:

```text id="ovknzt"
desktop client connects to server
Android client connects to server
both can join same map
players move with server-authoritative state
players collide with Tiled walls
players see each other
remote movement is interpolated
local movement is responsive
basic reconnect works
debug overlay exists
```

This is enough foundation before adding real graphics or more gameplay systems.

---

## Explicit non-goals for early MVP

Do not prioritize:

```text id="5f9m3q"
final character art
complex animations
quest system
inventory system
equipment
crafting
guilds
trading
auction house
large open world
advanced enemy AI
monetization
anti-cheat beyond server authority
```

These systems can be added later only after the core networking loop works.

---

## Main technical risks

### 1. Networking complexity

The hardest part is not drawing the world.

The hardest part is:

```text id="xcsckh"
latency
prediction
reconciliation
interpolation
disconnects
reconnects
server authority
snapshot size
```

### 2. Scope creep

“Simple MMO” can silently become a giant project.

Keep MVP small.

### 3. Client/server coupling

Avoid making the server depend on client rendering classes.

Avoid making the client decide gameplay truth.

### 4. Asset lifecycle

Even simple games can leak resources if assets are loaded manually.

Use centralized asset loading from the beginning.

### 5. Android late testing

Android-specific issues should be found early, not after the desktop version is already comfortable.

---

## Recommended first implementation path

```text id="f3flqx"
1. Create project/module structure
2. Add AGENTS.md and ARCHITECTURE.md
3. Add shared constants and protocol version
4. Add ECS dependency or minimal ECS foundation
5. Add client AssetManager wrapper
6. Add first Tiled map loading
7. Render map and primitive player
8. Add Box2D world and collision loading
9. Add server app with fixed tick loop
10. Add networking connection
11. Add input command protocol
12. Add server-authoritative movement
13. Add snapshots
14. Add interpolation/prediction
15. Add Android touch controls
```

---

## Architectural rule of thumb

When in doubt, ask:

```text id="91me40"
Should this exist on the client, server, or shared layer?
Who owns the truth?
Is this data or behavior?
Is this rendering or simulation?
Is this temporary state or persistent state?
Can this run on Android?
Can this be debugged when two clients disagree?
```

If the answer is unclear, keep the implementation smaller and more explicit.

The goal is not to build a perfect engine.

The goal is to build a multiplayer foundation that does not collapse when the second player joins.
