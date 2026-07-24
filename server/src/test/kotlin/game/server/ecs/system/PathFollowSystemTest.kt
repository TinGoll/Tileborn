package game.server.ecs.system

import com.badlogic.ashley.core.Engine
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
import game.shared.map.MapCollisionObject
import game.shared.navigation.NavigationGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathFollowSystemTest {
    @Test
    fun `mob follows path around rectangular obstacle`() {
        val obstacle = MapCollisionObject(id = 1, x = 2f, y = 0f, width = 1f, height = 2f)
        val grid = NavigationGrid(6, 4, 1f, listOf(obstacle))
        val engine = Engine().apply { addSystem(PathFollowSystem(grid)) }
        val player = player(engine, x = 5.5f, y = 1.5f)
        val mob = mob(engine, x = 0.5f, y = 1.5f, targetId = 7L)
        var usedDetour = false

        repeat(180) {
            engine.update(0.05f)
            val transform = mob.getComponent(TransformComponent::class.java)
            val velocity = mob.getComponent(VelocityComponent::class.java)
            transform.x += velocity.x * 0.05f
            transform.y += velocity.y * 0.05f
            usedDetour = usedDetour || transform.y > 2.1f
        }

        val transform = mob.getComponent(TransformComponent::class.java)
        assertTrue("Mob never used the free row around the obstacle", usedDetour)
        assertTrue("Mob did not reach the target side", transform.x > 4.5f)
        assertTrue(player.getComponent(TransformComponent::class.java).x > transform.x)
    }

    @Test
    fun `no route stops mob and retries only after cooldown`() {
        val wall = MapCollisionObject(id = 1, x = 2f, y = 0f, width = 1f, height = 4f)
        val grid = NavigationGrid(6, 4, 1f, listOf(wall))
        val engine = Engine().apply { addSystem(PathFollowSystem(grid, repathIntervalSeconds = 0.5f)) }
        player(engine, x = 5.5f, y = 1.5f)
        val mob = mob(engine, x = 0.5f, y = 1.5f, targetId = 7L)

        repeat(9) { engine.update(0.05f) }

        val path = mob.getComponent(PathComponent::class.java)
        val velocity = mob.getComponent(VelocityComponent::class.java)
        assertTrue(path.noPathAvailable)
        assertEquals(1L, path.pathRequestCount)
        assertEquals(0f, velocity.x, 0f)
        assertEquals(0f, velocity.y, 0f)

        engine.update(0.1f)
        assertEquals(2L, path.pathRequestCount)
        assertTrue(path.cells.isEmpty())
    }

    private fun mob(engine: Engine, x: Float, y: Float, targetId: Long) =
        engine.createEntity().apply {
            add(MobComponent())
            add(TransformComponent(x, y))
            add(VelocityComponent())
            add(MovementSpeedComponent(2f))
            add(AiStateComponent(AiState.CHASE, aggroRadius = 10f, attackRadius = 0.5f))
            add(AggroTargetComponent(targetId))
            add(HomePositionComponent(x, y))
            add(PathComponent(entityRadius = 0.2f))
        }.also(engine::addEntity)

    private fun player(engine: Engine, x: Float, y: Float) =
        engine.createEntity().apply {
            add(NetworkIdentityComponent(7L))
            add(PlayerInputComponent())
            add(TransformComponent(x, y))
        }.also(engine::addEntity)
}
