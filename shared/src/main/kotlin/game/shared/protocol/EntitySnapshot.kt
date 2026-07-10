package game.shared.protocol

data class EntitySnapshot(
    val entityId: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
)
