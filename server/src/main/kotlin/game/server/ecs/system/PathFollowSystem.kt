package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiState
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.HomePositionComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PathComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.navigation.NavigationGrid
import game.shared.navigation.Pathfinder
import kotlin.math.sqrt

/** Follows server-authored A* paths and rate-limits path requests per mob. */
class PathFollowSystem(
    private val navigationGrid: NavigationGrid,
    private val pathfinder: Pathfinder = Pathfinder(navigationGrid),
    private val repathIntervalSeconds: Float = DEFAULT_REPATH_INTERVAL_SECONDS,
    private val targetMovementThreshold: Float = DEFAULT_TARGET_MOVEMENT_THRESHOLD,
) : IteratingSystem(MOB_FAMILY, PRIORITY) {
    init {
        require(repathIntervalSeconds.isFinite() && repathIntervalSeconds > 0f)
        require(targetMovementThreshold.isFinite() && targetMovementThreshold >= 0f)
    }

    override fun processEntity(mob: Entity, deltaTime: Float) {
        val path = PATH_MAPPER.get(mob)
        path.secondsUntilRepath = (path.secondsUntilRepath - validDeltaTime(deltaTime)).coerceAtLeast(0f)
        val ai = AI_MAPPER.get(mob)
        val destination = when (ai.state) {
            AiState.CHASE -> chaseDestination(mob)
            AiState.RETURN -> HOME_MAPPER.get(mob).let { Destination(it.x, it.y, 0f) }
            AiState.IDLE, AiState.ATTACK, AiState.DEAD -> null
        }
        if (destination == null) {
            clearPath(path)
            stop(mob)
            return
        }

        val currentTransform = TRANSFORM_MAPPER.get(mob)
        val currentCell = navigationGrid.cellAt(currentTransform.x, currentTransform.y)
        val targetCell = navigationGrid.cellAt(destination.x, destination.y)
        if (currentCell == null || targetCell == null) {
            path.noPathAvailable = true
            stop(mob)
            return
        }

        val targetMoved = path.lastTargetCell != targetCell && squaredTargetMovement(path, destination) >=
            targetMovementThreshold * targetMovementThreshold
        val needsPath = path.pathRequestCount == 0L || path.noPathAvailable || path.nextCellIndex >= path.cells.size
        if (path.pathRequestCount == 0L || ((needsPath || targetMoved) && path.secondsUntilRepath <= 0f)) {
            calculatePath(path, currentCell, targetCell, destination)
        }
        if (path.noPathAvailable) {
            stop(mob)
            return
        }

        advanceReachedWaypoints(path, currentTransform)
        if (path.nextCellIndex < path.cells.size) {
            if (path.nextCellIndex == path.cells.lastIndex && path.lastTargetCell == targetCell) {
                moveToward(mob, destination.x, destination.y, destination.stopDistance, deltaTime)
                return
            }
            val next = path.cells[path.nextCellIndex]
            moveToward(mob, navigationGrid.centerX(next), navigationGrid.centerY(next), 0f, deltaTime)
            return
        }

        // The target may move while the repath cooldown is active. Never leave the proven path
        // toward a different cell; wait briefly for the next permitted A* request instead.
        if (path.lastTargetCell != targetCell) {
            stop(mob)
            return
        }
        moveToward(mob, destination.x, destination.y, destination.stopDistance, deltaTime)
    }

    private fun calculatePath(
        path: PathComponent,
        currentCell: game.shared.navigation.NavigationCell,
        targetCell: game.shared.navigation.NavigationCell,
        destination: Destination,
    ) {
        val cells = pathfinder.findPath(currentCell, targetCell, path.entityRadius)
        path.cells = cells
        path.nextCellIndex = if (cells.firstOrNull() == currentCell) 1 else 0
        path.lastTargetCell = targetCell
        path.lastTargetX = destination.x
        path.lastTargetY = destination.y
        path.secondsUntilRepath = repathIntervalSeconds
        path.pathRequestCount++
        path.noPathAvailable = cells.isEmpty()
    }

    private fun advanceReachedWaypoints(path: PathComponent, transform: TransformComponent) {
        while (path.nextCellIndex < path.cells.size) {
            val cell = path.cells[path.nextCellIndex]
            val deltaX = navigationGrid.centerX(cell) - transform.x
            val deltaY = navigationGrid.centerY(cell) - transform.y
            if (deltaX * deltaX + deltaY * deltaY > WAYPOINT_ARRIVAL_RADIUS * WAYPOINT_ARRIVAL_RADIUS) return
            path.nextCellIndex++
        }
    }

    private fun chaseDestination(mob: Entity): Destination? {
        val target = findTarget(TARGET_MAPPER.get(mob).targetEntityId) ?: return null
        val transform = TRANSFORM_MAPPER.get(target)
        return Destination(transform.x, transform.y, AI_MAPPER.get(mob).attackRadius)
    }

    private fun findTarget(targetEntityId: Long?): Entity? {
        if (targetEntityId == null) return null
        for (candidate in engine.entities) {
            if (PLAYER_MAPPER.get(candidate) == null) continue
            if (IDENTITY_MAPPER.get(candidate)?.networkEntityId == targetEntityId) return candidate
        }
        return null
    }

    private fun moveToward(
        mob: Entity,
        destinationX: Float,
        destinationY: Float,
        stopDistance: Float,
        deltaTime: Float,
    ) {
        val transform = TRANSFORM_MAPPER.get(mob)
        val deltaX = destinationX - transform.x
        val deltaY = destinationY - transform.y
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (!distanceSquared.isFinite() || distanceSquared <= SAME_POSITION_EPSILON) {
            stop(mob)
            return
        }

        val distance = sqrt(distanceSquared)
        val remainingDistance = (distance - stopDistance).coerceAtLeast(0f)
        val configuredSpeed = SPEED_MAPPER.get(mob).movementSpeed
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
        val elapsed = validDeltaTime(deltaTime).takeIf { it > 0f }
        val speed = if (elapsed == null) configuredSpeed else minOf(configuredSpeed, remainingDistance / elapsed)
        val velocity = VELOCITY_MAPPER.get(mob)
        velocity.x = deltaX / distance * speed
        velocity.y = deltaY / distance * speed
    }

    private fun squaredTargetMovement(path: PathComponent, destination: Destination): Float {
        if (!path.lastTargetX.isFinite() || !path.lastTargetY.isFinite()) return Float.POSITIVE_INFINITY
        val deltaX = destination.x - path.lastTargetX
        val deltaY = destination.y - path.lastTargetY
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun clearPath(path: PathComponent) {
        path.cells = emptyList()
        path.nextCellIndex = 0
        path.lastTargetCell = null
        path.lastTargetX = Float.NaN
        path.lastTargetY = Float.NaN
        path.secondsUntilRepath = 0f
        path.noPathAvailable = false
        path.pathRequestCount = 0L
    }

    private fun stop(mob: Entity) {
        val velocity = VELOCITY_MAPPER.get(mob)
        velocity.x = 0f
        velocity.y = 0f
    }

    private fun validDeltaTime(deltaTime: Float): Float =
        deltaTime.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f

    private data class Destination(val x: Float, val y: Float, val stopDistance: Float)

    companion object {
        const val PRIORITY = 150
        const val DEFAULT_REPATH_INTERVAL_SECONDS = 0.5f
        const val DEFAULT_TARGET_MOVEMENT_THRESHOLD = 0.75f
        private const val WAYPOINT_ARRIVAL_RADIUS = 0.1f
        private const val SAME_POSITION_EPSILON = 0.000001f
        private val AI_MAPPER = ComponentMapper.getFor(AiStateComponent::class.java)
        private val TARGET_MAPPER = ComponentMapper.getFor(AggroTargetComponent::class.java)
        private val HOME_MAPPER = ComponentMapper.getFor(HomePositionComponent::class.java)
        private val PATH_MAPPER = ComponentMapper.getFor(PathComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        private val SPEED_MAPPER = ComponentMapper.getFor(MovementSpeedComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val PLAYER_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        private val MOB_FAMILY = Family.all(
            MobComponent::class.java,
            AiStateComponent::class.java,
            AggroTargetComponent::class.java,
            HomePositionComponent::class.java,
            PathComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
            MovementSpeedComponent::class.java,
        ).get()
    }
}
