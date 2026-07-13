package game.client.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import game.client.debug.ConnectionState
import game.client.network.GameNetworkClient
import game.shared.protocol.JoinRejected
import game.shared.protocol.NicknameRules
import ktx.app.KtxScreen
import ktx.app.clearScreen

/** Collects a display nickname before a network client and game session are created. */
class JoinScreen(
    private val networkClientFactory: (String) -> GameNetworkClient,
    private val onConnected: (GameNetworkClient) -> Unit,
) : KtxScreen {
    private val stage = Stage(ScreenViewport())
    private val font = BitmapFont()
    private val textures = mutableListOf<Texture>()
    private val nicknameField = TextField("", textFieldStyle())
    private val validationLabel = Label("", labelStyle(Color(1f, 0.45f, 0.4f, 1f)))
    private val playButton = TextButton("Play", buttonStyle())
    private var pendingClient: GameNetworkClient? = null
    private var transitionRequested = false

    init {
        nicknameField.maxLength = NicknameRules.MAX_LENGTH
        nicknameField.messageText = "Nickname"
        nicknameField.setTextFieldListener { _, character ->
            if (character == '\n' || character == '\r') submit()
        }

        playButton.apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    if (pendingClient == null) submit() else cancelConnection()
                }
            })
        }

        validationLabel.setAlignment(Align.center)
        validationLabel.setWrap(true)

        stage.addActor(Table().apply {
            setFillParent(true)
            defaults().pad(8f)
            add(Label("Tileborn", labelStyle(Color.WHITE))).padBottom(18f).row()
            add(Label("Choose a nickname", labelStyle(Color.LIGHT_GRAY))).row()
            add(nicknameField).width(320f).height(48f).row()
            add(validationLabel).width(420f).height(44f).row()
            add(playButton).width(180f).height(48f).padTop(10f).row()
        })
    }

    override fun show() {
        transitionRequested = false
        nicknameField.text = Gdx.app.getPreferences(PREFERENCES_NAME).getString(NICKNAME_KEY, "")
        Gdx.input.inputProcessor = stage
        stage.keyboardFocus = nicknameField
        Gdx.input.setOnscreenKeyboardVisible(true)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.08f, green = 0.09f, blue = 0.12f)
        updateConnectionState()
        stage.act(delta.coerceAtMost(MAX_FRAME_DELTA))
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) stage.viewport.update(width, height, true)
    }

    override fun hide() {
        if (Gdx.input.inputProcessor === stage) Gdx.input.inputProcessor = null
        Gdx.input.setOnscreenKeyboardVisible(false)
    }

    override fun dispose() {
        hide()
        pendingClient?.close()
        pendingClient = null
        stage.dispose()
        textures.forEach(Texture::dispose)
        textures.clear()
        font.dispose()
    }

    private fun submit() {
        if (transitionRequested) return
        val nickname = NicknameRules.normalize(nicknameField.text)
        val validationError = NicknameRules.validationError(nickname)
        if (validationError != null) {
            validationLabel.color = Color(1f, 0.45f, 0.4f, 1f)
            validationLabel.setText(validationError)
            return
        }

        validationLabel.color = Color.LIGHT_GRAY
        validationLabel.setText("Connecting...")
        nicknameField.isDisabled = true
        playButton.setText("Cancel")
        pendingClient = networkClientFactory(nickname).also(GameNetworkClient::connect)
    }

    private fun updateConnectionState() {
        val client = pendingClient ?: return
        when (client.connectionState) {
            ConnectionState.CONNECTED -> {
                if (transitionRequested) return
                transitionRequested = true
                Gdx.app.getPreferences(PREFERENCES_NAME)
                    .putString(NICKNAME_KEY, NicknameRules.normalize(nicknameField.text))
                    .flush()
                pendingClient = null
                Gdx.app.postRunnable { onConnected(client) }
            }
            ConnectionState.REJECTED -> {
                val reason = (client.lastServerMessage as? JoinRejected)?.reason ?: "Join rejected."
                client.close()
                pendingClient = null
                showConnectionError(reason)
            }
            ConnectionState.RECONNECTING -> {
                validationLabel.color = Color.LIGHT_GRAY
                validationLabel.setText("Server unavailable. Retrying...")
            }
            ConnectionState.CONNECTING -> {
                validationLabel.color = Color.LIGHT_GRAY
                validationLabel.setText("Connecting...")
            }
            ConnectionState.DISCONNECTED, ConnectionState.LOCAL -> Unit
        }
    }

    private fun cancelConnection() {
        pendingClient?.close()
        pendingClient = null
        nicknameField.isDisabled = false
        playButton.setText("Play")
        validationLabel.setText("")
    }

    private fun showConnectionError(reason: String) {
        validationLabel.color = Color(1f, 0.45f, 0.4f, 1f)
        validationLabel.setText(reason)
        nicknameField.isDisabled = false
        playButton.setText("Play")
        stage.keyboardFocus = nicknameField
    }

    private fun labelStyle(color: Color) = Label.LabelStyle(font, color)

    private fun textFieldStyle() = TextField.TextFieldStyle().apply {
        font = this@JoinScreen.font
        fontColor = Color.WHITE
        messageFontColor = Color.GRAY
        background = drawable(Color(0.14f, 0.16f, 0.21f, 1f))
        cursor = drawable(Color.SKY)
        selection = drawable(Color(0.2f, 0.45f, 0.75f, 0.65f))
    }

    private fun buttonStyle() = TextButton.TextButtonStyle().apply {
        font = this@JoinScreen.font
        fontColor = Color.WHITE
        up = drawable(Color(0.16f, 0.42f, 0.75f, 1f))
        over = drawable(Color(0.2f, 0.5f, 0.88f, 1f))
        down = drawable(Color(0.1f, 0.3f, 0.58f, 1f))
    }

    private fun drawable(color: Color): TextureRegionDrawable {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        textures += texture
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private companion object {
        const val PREFERENCES_NAME = "tileborn-client"
        const val NICKNAME_KEY = "nickname"
        const val MAX_FRAME_DELTA = 1f / 15f
    }
}
