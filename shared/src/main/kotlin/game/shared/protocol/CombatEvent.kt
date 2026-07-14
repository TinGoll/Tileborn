package game.shared.protocol

/** Authoritative combat event produced by server gameplay systems. */
sealed interface CombatEvent : ServerMessage {
    val eventId: Long
    val sourceEntityId: Int
}

data class AttackStartedEvent(
    override val eventId: Long,
    override val sourceEntityId: Int,
    val attackSequence: Long,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.ATTACK_STARTED_EVENT,
) : CombatEvent {
    init {
        require(eventId >= 0L) { "Combat event id must be non-negative." }
        require(attackSequence >= 0L) { "Attack sequence must be non-negative." }
        require(type == MessageType.ATTACK_STARTED_EVENT) {
            "AttackStartedEvent type must be ATTACK_STARTED_EVENT."
        }
    }
}

data class HitEvent(
    override val eventId: Long,
    val attackEventId: Long,
    override val sourceEntityId: Int,
    val targetEntityId: Int,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.HIT_EVENT,
) : CombatEvent {
    init {
        require(eventId >= 0L) { "Combat event id must be non-negative." }
        require(attackEventId >= 0L) { "Attack event id must be non-negative." }
        require(sourceEntityId != targetEntityId) { "A hit must have different source and target entities." }
        require(type == MessageType.HIT_EVENT) { "HitEvent type must be HIT_EVENT." }
    }
}

/**
 * Server-owned damage request and result.
 *
 * [currentHealth] and [maxHealth] are null while the event is awaiting DamageSystem processing.
 * DamageSystem fills both fields before the event is sent to clients.
 */
data class DamageEvent(
    override val eventId: Long,
    val hitEventId: Long,
    override val sourceEntityId: Int,
    val targetEntityId: Int,
    val amount: Float,
    val currentHealth: Float? = null,
    val maxHealth: Float? = null,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.DAMAGE_EVENT,
) : CombatEvent {
    init {
        require(eventId >= 0L) { "Combat event id must be non-negative." }
        require(hitEventId >= 0L) { "Hit event id must be non-negative." }
        require(amount.isFinite() && amount >= 0f) { "Damage must be finite and non-negative." }
        require((currentHealth == null) == (maxHealth == null)) {
            "Damage result health fields must either both be present or both be absent."
        }
        currentHealth?.let { health ->
            require(health.isFinite() && health >= 0f) { "Current health must be finite and non-negative." }
            require(maxHealth!!.isFinite() && maxHealth >= health) {
                "Max health must be finite and not less than current health."
            }
        }
        require(type == MessageType.DAMAGE_EVENT) { "DamageEvent type must be DAMAGE_EVENT." }
    }
}

data class EntityDiedEvent(
    override val eventId: Long,
    val damageEventId: Long,
    override val sourceEntityId: Int,
    val targetEntityId: Int,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.ENTITY_DIED_EVENT,
) : CombatEvent {
    init {
        require(eventId >= 0L) { "Combat event id must be non-negative." }
        require(damageEventId >= 0L) { "Damage event id must be non-negative." }
        require(type == MessageType.ENTITY_DIED_EVENT) {
            "EntityDiedEvent type must be ENTITY_DIED_EVENT."
        }
    }
}
