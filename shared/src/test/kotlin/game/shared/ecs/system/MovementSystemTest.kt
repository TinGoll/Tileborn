package game.shared.ecs.system

import com.badlogic.ashley.core.Engine
import game.shared.constants.GameConstants
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementSystemTest {
    @Test
    fun `right input increases x`() {
        val fixture = movementFixture(moveX = 1f)

        fixture.engine.update(0.25f)

        assertEquals(GameConstants.PLAYER_MOVE_SPEED, fixture.velocity.x, 0f)
        assertEquals(GameConstants.PLAYER_MOVE_SPEED * 0.25f, fixture.transform.x, 0f)
        assertEquals(0f, fixture.transform.y, 0f)
    }

    @Test
    fun `zero input does not change position`() {
        val fixture = movementFixture(startX = 3f, startY = -2f)

        fixture.engine.update(1f)

        assertEquals(0f, fixture.velocity.x, 0f)
        assertEquals(0f, fixture.velocity.y, 0f)
        assertEquals(3f, fixture.transform.x, 0f)
        assertEquals(-2f, fixture.transform.y, 0f)
    }

    @Test
    fun `position integrates velocity using delta time`() {
        val oneFrame = movementFixture(moveX = 1f)
        val twoFrames = movementFixture(moveX = 1f)

        oneFrame.engine.update(0.5f)
        twoFrames.engine.update(0.2f)
        twoFrames.engine.update(0.3f)

        assertEquals(GameConstants.PLAYER_MOVE_SPEED * 0.5f, oneFrame.transform.x, 0f)
        assertEquals(oneFrame.transform.x, twoFrames.transform.x, 0.000001f)
    }

    private fun movementFixture(
        moveX: Float = 0f,
        moveY: Float = 0f,
        startX: Float = 0f,
        startY: Float = 0f,
    ): MovementFixture {
        val engine = Engine()
        val transform = TransformComponent(startX, startY)
        val velocity = VelocityComponent()
        val input = PlayerInputComponent().apply { state.setMovement(moveX, moveY) }
        val entity = engine.createEntity().apply {
            add(transform)
            add(velocity)
            add(input)
        }
        engine.addEntity(entity)
        engine.addSystem(MovementSystem())
        return MovementFixture(engine, transform, velocity)
    }

    private data class MovementFixture(
        val engine: Engine,
        val transform: TransformComponent,
        val velocity: VelocityComponent,
    )
}
