# AGENTS.md

## Project

Online top-down game built with **libGDX** for:

* Desktop PC
* Android
* Dedicated authoritative server

MVP: simple MMO-like world with Tiled maps, primitive rendering, Box2D collision/physics, ECS architecture, and centralized asset loading.

Prioritize architecture, debugging, server correctness, and incremental development over final graphics.

---

## Core rules

### Server-authoritative model

The server is the source of truth for:

* entity state
* movement and positions
* physics and collisions
* combat and damage
* inventory/world mutations
* spawning/despawning
* map transitions

Clients send **intent**, not final state.

Good:

```text
Client: move-left + move-up
Server: simulates and sends authoritative snapshot
```

Bad:

```text
Client: my position is x=120, y=50
Server: accepts it
```

Never trust client-side position, velocity, damage, item pickup, teleport, or kill confirmation.

---

## Architecture boundaries

Keep client, server, and shared code separated.

Recommended logical structure:

```text
core/
  shared/
    protocol/
    constants/
    math/
    ids/
    ecs/
    map/
  client/
    screens/
    rendering/
    input/
    networking/
    prediction/
    interpolation/
    assets/
  server/
    loop/
    world/
    networking/
    persistence/
    physics/
    map/
desktop/
android/
server-launcher/
```

Shared code may contain DTOs, protocol models, ids, constants, math helpers, serialization, ECS data, and map metadata.

Shared code must not contain rendering, UI screens, platform-specific logic, server DB access, or client asset code.

---

## ECS rules

ECS is mandatory.

* Entities are ids.
* Components are data only.
* Systems contain behavior.
* Prefer composition over inheritance.
* Define explicit system order.
* Do not rely on accidental collection iteration order.

Avoid inheritance trees like:

```text
GameObject -> Character -> Player
```

Prefer components:

```text
TransformComponent
VelocityComponent
PhysicsBodyComponent
NetworkIdentityComponent
HealthComponent
PlayerInputComponent
RenderablePrimitiveComponent
```

Components must not load assets, render, send network messages, or contain services.

---

## Recommended systems

Client:

```text
InputSystem
NetworkSendSystem
NetworkReceiveSystem
ClientPredictionSystem
SnapshotInterpolationSystem
CameraFollowSystem
PrimitiveRenderSystem
DebugRenderSystem
MapRenderSystem
AssetLoadingSystem
```

Server:

```text
ServerInputSystem
MovementSystem
PhysicsSimulationSystem
CollisionSystem
InterestManagementSystem
SnapshotBroadcastSystem
SpawnSystem
DespawnSystem
PersistenceSystem
ReconnectSystem
```

Only put systems in shared code if they are platform-independent and deterministic.

---

## Update order

Server tick:

```text
1. Receive network messages
2. Validate input
3. Apply input to ECS
4. Run gameplay systems
5. Step Box2D
6. Sync physics back to ECS
7. Process collisions
8. Spawn/despawn
9. Build snapshots
10. Send snapshots/events
11. Persist important state if needed
```

Client frame:

```text
1. Poll input
2. Send input
3. Receive server messages
4. Apply authoritative snapshots
5. Predict/reconcile local player
6. Interpolate remote entities
7. Update camera
8. Render map/entities/debug
```

---

## Box2D rules

Server Box2D world is authoritative.

Use fixed timestep for authoritative simulation.

Good:

```java
world.step(FIXED_TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
```

Bad:

```java
world.step(Gdx.graphics.getDeltaTime(), 6, 2);
```

Client Box2D may be used only for prediction, smoothing, local feel, or debug visualization. It must not override server state.

---

## Coordinates

Define conversions in one place.

Recommended:

```text
1 tile = 32 px
1 Box2D meter = 32 px
1 world unit = 1 meter
```

Use helpers:

```text
WorldUnits.toPixels(...)
WorldUnits.toMeters(...)
TileUnits.toWorld(...)
```

Do not scatter `/ 32f` or `* 32f` across the codebase.

---

## Tiled maps

Maps are created in Tiled.

Support:

* tile layers
* object layers
* collision objects
* spawn points
* triggers
* portals
* safe zones
* custom properties

Recommended layers:

```text
ground
decorations
collision
spawn_points
triggers
portals
objects
debug
```

Prefer explicit custom properties over behavior based on object names.

Examples:

```text
type = spawn
spawnId = default

type = portal
targetMap = map_002
targetSpawn = entrance

type = safe_zone
pvp = false
```

Server loads gameplay metadata, collision, spawns, triggers, and zones.
Server must not depend on textures or rendering-only assets.

---

## Networking protocol

Use explicit client/server message categories.

Client to server:

```text
JoinRequest
InputCommand
InteractCommand
AttackCommand
ChatMessage
PingRequest
```

Server to client:

```text
JoinAccepted
JoinRejected
WorldSnapshot
EntitySpawned
EntityDespawned
EventMessage
CorrectionMessage
PongResponse
```

Every message must include:

```text
type
protocolVersion
sequence/timestamp when needed
```

Define protocol version explicitly.

```java
public static final int PROTOCOL_VERSION = 1;
```

Reject incompatible clients. Do not silently ignore protocol mismatch.

---

## Prediction and interpolation

Local player:

* predict immediately
* compare with authoritative state
* reconcile smoothly when possible
* snap only on large error

Remote players:

* interpolate between snapshots
* optionally extrapolate briefly during delay
* do not render directly at latest received position if it causes jitter

---

## Interest management

Do not send the whole world to every client once maps grow.

For MVP, use simple distance-based or chunk-based filtering.

Recommended first version:

```text
divide map into chunks
track entity chunk
client receives own chunk + neighboring chunks
```

Server decides what each client can observe.

---

## Persistence

Do not persist fast-changing state every tick.

Keep in memory:

```text
positions
active physics bodies
temporary combat state
projectiles
mobs
```

Persist:

```text
account
character
saved position
inventory
progression
settings
important world state
```

Save on disconnect, map transition, important gameplay event, or safe periodic interval.

Never write player position to DB 20 times per second.

---

## Assets

Use centralized asset loading.

Use libGDX `AssetManager` or a small wrapper around it.

Do not load assets directly in random code.

Bad:

```java
new Texture("player.png")
```

Good:

```java
assets.get(PlayerAssets.PLAYER_TEXTURE)
```

Centralize paths/descriptors.

Recommended structure:

```text
assets/
  maps/
  textures/
  atlases/
  audio/
  fonts/
  skins/
  debug/
```

Asset loading flow:

```text
1. Queue assets
2. Show loading/debug screen
3. assetManager.update()
4. Switch screen after loading completes
5. Unload/dispose scoped assets when needed
```

---

## Rendering

MVP rendering uses primitives:

* circles for players
* rectangles for walls
* lines for direction/debug
* colors for entity types
* Box2D debug renderer
* debug text overlay

Rendering reads ECS components only.

Rendering systems must not contain gameplay decisions.

---

## Debug overlay

Add early and keep easy to disable.

Minimum:

```text
FPS
ping
client tick
server tick
local player position
current map id
entity count
visible entity count
connection state
```

Useful optional data:

```text
collision shapes
chunk boundaries
entity ids
snapshot delay
prediction error
asset loading progress
```

---

## Android

Test Android throughout development.

Account for:

* pause/resume
* backgrounding
* connection loss
* touch controls
* aspect ratios
* slow devices
* asset loading time
* battery/performance

Do not assume keyboard-only input.

All input sources should produce the same game-level input model:

```text
KeyboardInputSource
TouchInputSource
GamepadInputSource
```

---

## MVP scope

Default MVP:

```text
1 map
desktop client
android client
dedicated server
up to 10 players
primitive rendering
Tiled collision
Box2D physics
server-authoritative movement
players see each other
basic reconnect
debug overlay
```

Out of scope unless explicitly requested:

```text
final graphics
quests
complex inventory
advanced combat
guilds
trading
auction house
large open world
procedural generation
advanced AI
monetization
```

Do not expand scope silently.

---

## Code style

Prefer simple, explicit code.

Good names:

```text
ServerSnapshotSystem
PlayerInputComponent
PhysicsBodyComponent
TiledCollisionLoader
```

Bad names:

```text
Manager
Processor
Handler2
Thing
BaseObject
```

Avoid clever abstractions unless they solve an immediate problem.

Avoid global mutable state and static access to game state.

Prefer explicit dependencies through constructors.

---

## Testing

Add or update tests when gameplay logic changes.

Prioritize tests for:

* protocol serialization/deserialization
* coordinate conversion
* Tiled property parsing
* ECS system behavior
* input validation
* snapshot building
* interest management
* reconnect behavior

For libGDX-heavy code, isolate pure logic into testable classes.

Before multiplayer movement is considered done, verify:

```text
one client connects
two clients connect
clients see each other
disconnect/reconnect works
invalid protocol is rejected
invalid movement is corrected
high ping does not break movement
server does not trust client position
```

---

## Performance

Avoid per-frame allocations in hot loops.

Watch for:

* new vectors every frame
* temporary collections in systems
* string concatenation in update/render loops
* excessive snapshot objects
* unnecessary asset lookups
* sending unchanged state too often

Use pooling only after measuring a real problem.

---

## Logging and errors

Server logs should include:

```text
connection opened/closed
join accepted/rejected
protocol mismatch
map loaded
player spawned
unexpected input
snapshot send failures
persistence errors
```

Avoid noisy per-tick logs unless behind a debug flag.

Fail loudly for developer/config errors:

```text
missing map
missing collision layer
invalid custom property
duplicate spawn id
unknown protocol message
unsupported protocol version
```

Fail gracefully for runtime issues:

```text
connection lost
reconnect attempt
loading failure
server unavailable
```

---

## Dependencies

Allowed categories:

```text
libGDX
Box2D
Tiled support
ECS framework
networking
serialization
logging
testing
```

Before adding a dependency, check:

```text
desktop support
Android support
server compatibility
license
maintenance
binary size
API stability
```

Prefer boring, stable dependencies.

Do not add a library for something that can be implemented safely in a few clear classes.

---

## ECS framework

If no ECS framework is chosen, default to:

```text
Ashley ECS
```

Reason: commonly used with libGDX, simple enough for MVP, avoids building ECS infrastructure too early.

If using Ashley:

* keep components data-only
* keep behavior in systems
* define system priority explicitly
* avoid mixing rendering and simulation

If using custom ECS:

* keep it minimal
* support entity creation/deletion
* support component storage
* support queries/families
* support deterministic system order
* do not build a full engine before building the game

---

## Codex workflow

Before editing code:

1. Inspect existing structure.
2. Identify affected modules.
3. Preserve existing conventions.
4. Make the smallest coherent change.
5. Avoid unrelated refactors.
6. Add/update tests when logic changes.
7. Run relevant checks if available.
8. Summarize changed files and reasoning.

Do not:

* rewrite large parts unless asked
* introduce dependencies without reason
* change protocol shapes without version/migration notes
* move files across client/server/shared casually

---

## Definition of done

A task is done only when:

* code compiles
* relevant tests pass or failure is explained
* client/server/shared boundaries are preserved
* ECS rules are followed
* assets go through asset manager
* server remains authoritative
* no unrelated refactors are included
* debug behavior is preserved or improved
* important assumptions are documented

---

## Development bias

Implement in this order by default:

```text
1. Project/module structure
2. Shared protocol/constants
3. ECS foundation
4. Asset manager wrapper
5. Tiled map loading
6. Primitive rendering
7. Box2D collision
8. Server loop
9. Local simulation using same ECS concepts
10. Network connection
11. Multiplayer movement
12. Prediction/interpolation
13. Android controls
14. Reconnect/persistence
```

Goal: not a perfect engine, but a small online game foundation that can survive becoming larger.
