package game.client.screens

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.graphics.use

class GameScreen(
    internal val assets: GameAssetManager,
) : KtxScreen {
    private var batch: SpriteBatch? = null
    private var logo: Texture? = null

    init {
        check(assets.isFinished()) {
            "GameScreen requires a fully loaded GameAssetManager"
        }
    }

    override fun show() {
        if (batch == null) {
            batch = SpriteBatch()
            logo = assets.get(AssetDescriptors.LOGO)
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        val texture = logo ?: return
        batch?.use {
            it.draw(texture, 100f, 160f)
        }
    }

    override fun dispose() {
        batch.disposeSafely()
        batch = null
        logo = null
    }
}
