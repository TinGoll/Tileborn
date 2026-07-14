package game.server.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.system.ServerInitializationSystem
import game.server.ecs.system.AttackCommandSystem
import game.server.ecs.system.AttackValidationSystem
import game.server.ecs.system.CharacterStateSystem
import game.server.ecs.system.CombatEventSystem
import game.server.ecs.system.CooldownSystem
import game.server.ecs.system.DamageSystem
import game.server.ecs.system.HealthSystem
import game.server.ecs.system.MobDespawnSystem
import game.server.ecs.system.MobSpawnSystem
import game.shared.definition.DefinitionRegistry
import game.shared.map.NpcSpawnPoint
import game.shared.ecs.system.MovementSystem
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory

/** Owns the authoritative ECS engine and its explicitly ordered systems. */
class ServerEcsWorld : Disposable {
    /** The server-owned authoritative Box2D world. */
    val physicsWorld = PhysicsWorldFactory.create()
    private val physicsSimulationSystem = PhysicsSimulationSystem(physicsWorld)
    val healthSystem = HealthSystem()
    val characterStateSystem = CharacterStateSystem()
    val combatEventSystem = CombatEventSystem()
    val damageSystem = DamageSystem(healthSystem, characterStateSystem, combatEventSystem)
    val attackCommandSystem = AttackCommandSystem()
    private val cooldownSystem = CooldownSystem()
    val attackValidationSystem = AttackValidationSystem(combatEventSystem)
    var mobSpawnSystem: MobSpawnSystem? = null
        private set

    val engine: Engine = Engine().apply {
        addSystem(ServerInitializationSystem())
        addSystem(healthSystem)
        addSystem(characterStateSystem)
        addSystem(attackCommandSystem)
        addSystem(cooldownSystem)
        addSystem(attackValidationSystem)
        addSystem(damageSystem)
        addSystem(combatEventSystem)
        addSystem(MovementSystem())
        addSystem(physicsSimulationSystem)
    }

    fun configureMobLifecycle(
        spawnPoints: List<NpcSpawnPoint>,
        definitionRegistry: DefinitionRegistry,
        spawnMob: (Int, String, Float, Float, String) -> Entity,
    ) {
        check(mobSpawnSystem == null) { "Mob lifecycle is already configured" }
        val spawnSystem = MobSpawnSystem(spawnPoints, definitionRegistry, spawnMob)
        mobSpawnSystem = spawnSystem
        engine.addSystem(MobDespawnSystem(spawnSystem::scheduleRespawn))
        engine.addSystem(spawnSystem)
    }

    override fun dispose() {
        engine.removeAllEntities()
        val systems = mutableListOf<EntitySystem>()
        for (system in engine.systems) {
            systems += system
        }
        systems.forEach { system ->
            if (system is Disposable) system.dispose()
            engine.removeSystem(system)
        }
        physicsWorld.dispose()
    }
}
