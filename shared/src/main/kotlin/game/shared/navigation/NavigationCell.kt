package game.shared.navigation

/** Integer grid address. World coordinates are resolved by the owning [NavigationGrid]. */
data class NavigationCell(
    val column: Int,
    val row: Int,
)
