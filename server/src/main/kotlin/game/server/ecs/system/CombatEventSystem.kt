package game.server.ecs.system

import com.badlogic.ashley.core.EntitySystem
import game.shared.protocol.CombatEvent
import game.shared.protocol.DamageEvent
import java.util.ArrayDeque

/** Owns authoritative combat event ids, damage routing, and ordered client delivery. */
class CombatEventSystem : EntitySystem(PRIORITY) {
    private var nextEventId = FIRST_EVENT_ID
    private val pendingDamageEvents = ArrayDeque<DamageEvent>()
    private val outboundEvents = ArrayDeque<CombatEvent>()

    fun nextEventId(): Long {
        check(nextEventId != Long.MAX_VALUE) { "Combat event id space is exhausted." }
        return nextEventId++
    }

    /** Queues an unapplied server-created damage event for DamageSystem. */
    fun queueDamage(event: DamageEvent) {
        require(event.currentHealth == null && event.maxHealth == null) {
            "Only unapplied damage events can enter DamageSystem."
        }
        observe(event.eventId)
        pendingDamageEvents.addLast(event)
    }

    /** Publishes an authoritative event for transport after its gameplay stage has completed. */
    fun publish(event: CombatEvent) {
        if (event is DamageEvent) {
            require(event.currentHealth != null && event.maxHealth != null) {
                "Damage events must contain authoritative health before publication."
            }
        }
        observe(event.eventId)
        outboundEvents.addLast(event)
    }

    fun drainPendingDamageEvents(): List<DamageEvent> = buildList {
        while (pendingDamageEvents.isNotEmpty()) add(pendingDamageEvents.removeFirst())
    }

    fun drainOutboundEvents(): List<CombatEvent> = buildList {
        while (outboundEvents.isNotEmpty()) add(outboundEvents.removeFirst())
    }

    private fun observe(eventId: Long) {
        if (eventId >= nextEventId) {
            check(eventId != Long.MAX_VALUE) { "Combat event id space is exhausted." }
            nextEventId = eventId + 1L
        }
    }

    companion object {
        const val PRIORITY = 140
        private const val FIRST_EVENT_ID = 1L
    }
}
