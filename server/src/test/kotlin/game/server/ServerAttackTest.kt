package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.shared.constants.GameConstants
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.AttackCommand
import game.shared.protocol.AttackStartedEvent
import game.shared.protocol.DamageEvent
import game.shared.protocol.GameEventType
import game.shared.protocol.HitEvent
import game.shared.protocol.Protocol
import game.shared.protocol.ProtocolCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAttackTest {
    @Test
    fun `attack in range applies fixed server damage and emits hit event`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = 1f)

        assertTrue(world.queueAttack(1, command(sequence = 1L, targetEntityId = 2)))
        world.update(0f)

        assertEquals(90f, target.health(), 0f)
        val events = world.drainCombatEvents()
        assertTrue(events[0] is AttackStartedEvent)
        val hit = events[1] as HitEvent
        val damage = events[2] as DamageEvent
        assertEquals(1, hit.sourceEntityId)
        assertEquals(2, hit.targetEntityId)
        assertEquals(hit.eventId, damage.hitEventId)
        assertEquals(1, damage.sourceEntityId)
        assertEquals(2, damage.targetEntityId)
        assertEquals(GameConstants.PLAYER_ATTACK_DAMAGE, damage.amount, 0f)
        assertEquals(90f, damage.currentHealth!!, 0f)
    }

    @Test
    fun `attack outside range misses without damage`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = GameConstants.PLAYER_ATTACK_RANGE + 0.1f)

        world.queueAttack(1, command(sequence = 1L, targetEntityId = 2))
        world.update(0f)

        assertEquals(GameConstants.PLAYER_MAX_HEALTH, target.health(), 0f)
        assertEquals(GameEventType.ATTACK_MISSED, world.drainAttackEvents().single().eventType)
        assertTrue(world.drainCombatEvents().single() is AttackStartedEvent)
    }

    @Test
    fun `cooldown prevents a second attack until authoritative time advances`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = 1f)
        world.queueAttack(1, command(sequence = 1L, targetEntityId = 2))
        world.update(0f)
        world.drainCombatEvents()

        world.queueAttack(1, command(sequence = 2L, targetEntityId = 2))
        world.update(GameConstants.PLAYER_ATTACK_COOLDOWN_SECONDS / 2f)

        assertEquals(90f, target.health(), 0f)
        assertTrue(world.drainCombatEvents().isEmpty())

        world.update(GameConstants.PLAYER_ATTACK_COOLDOWN_SECONDS)
        world.queueAttack(1, command(sequence = 3L, targetEntityId = 2))
        world.update(0f)

        assertEquals(80f, target.health(), 0f)
        assertEquals(3, world.drainCombatEvents().size)
    }

    @Test
    fun `duplicate and stale attack commands are never applied again`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = 1f)
        val first = command(sequence = 7L, targetEntityId = 2)
        assertTrue(world.queueAttack(1, first))
        world.update(0f)
        world.drainCombatEvents()
        world.update(GameConstants.PLAYER_ATTACK_COOLDOWN_SECONDS)

        assertFalse(world.queueAttack(1, first))
        assertFalse(world.queueAttack(1, command(sequence = 6L, targetEntityId = 2)))
        world.update(0f)

        assertEquals(90f, target.health(), 0f)
        assertTrue(world.drainCombatEvents().isEmpty())
    }

    @Test
    fun `dead player cannot attack`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = 1f)
        assertTrue(world.applyDamage(1, GameConstants.PLAYER_MAX_HEALTH))
        world.drainCombatEvents()

        world.queueAttack(1, command(sequence = 1L, targetEntityId = 2))
        world.update(0f)

        assertEquals(GameConstants.PLAYER_MAX_HEALTH, target.health(), 0f)
        assertTrue(world.drainCombatEvents().isEmpty())
    }

    @Test
    fun `client supplied damage is unsupported and cannot change authoritative damage`() = withWorld { world ->
        val (_, target) = spawnCombatants(world, targetOffsetX = 1f)
        val decoded = ProtocolCodec.decodeClient(
            """{"type":"ATTACK_COMMAND","protocolVersion":${Protocol.PROTOCOL_VERSION},"inputSequence":1,"clientTick":1,"aimX":1.0,"aimY":0.0,"optionalTargetEntityId":2,"damage":1000.0}""",
        ) as AttackCommand

        world.queueAttack(1, decoded)
        world.update(0f)

        assertEquals(90f, target.health(), 0f)
    }

    @Test
    fun `attack from nonexistent entity is rejected`() = withWorld { world ->
        world.spawnPlayer(2)

        assertFalse(world.queueAttack(9999, command(sequence = 1L, targetEntityId = 2)))
        world.update(0f)

        assertTrue(world.drainCombatEvents().isEmpty())
    }

    @Test
    fun `nonexistent target produces no hit or damage event`() = withWorld { world ->
        world.spawnPlayer(1)

        assertTrue(world.queueAttack(1, command(sequence = 1L, targetEntityId = 9999)))
        world.update(0f)

        assertEquals(listOf(AttackStartedEvent::class.java), world.drainCombatEvents().map { it::class.java })
        assertEquals(GameEventType.ATTACK_MISSED, world.drainAttackEvents().single().eventType)
    }

    private fun spawnCombatants(world: ServerWorld, targetOffsetX: Float) =
        world.spawnPlayer(1).let { attacker ->
            val target = world.spawnPlayer(2)
            val attackerTransform = attacker.getComponent(TransformComponent::class.java)
            target.getComponent(TransformComponent::class.java).apply {
                x = attackerTransform.x + targetOffsetX
                y = attackerTransform.y
            }
            attacker to target
        }

    private fun command(sequence: Long, targetEntityId: Int) = AttackCommand(
        inputSequence = sequence,
        clientTick = sequence,
        aimX = 1f,
        aimY = 0f,
        optionalTargetEntityId = targetEntityId,
    )

    private fun com.badlogic.ashley.core.Entity.health(): Float =
        getComponent(HealthComponent::class.java).currentHealth

    private fun withWorld(block: (ServerWorld) -> Unit) {
        val application = if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
        val world = ServerWorld("debug_map", "maps/debug_map.tmx")
        try {
            block(world)
        } finally {
            world.dispose()
            application?.exit()
        }
    }
}
