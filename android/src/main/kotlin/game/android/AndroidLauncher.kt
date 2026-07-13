package game.android

import android.os.Bundle

import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import game.client.Main
import game.client.input.TouchInputSource
import game.client.network.TcpGameClient

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(
            Main(
                inputSource = TouchInputSource(),
                networkClient = TcpGameClient(host = BuildConfig.SERVER_HOST, port = BuildConfig.SERVER_PORT),
            ),
            AndroidApplicationConfiguration().apply {
            // Configure your application here.
            useImmersiveMode = true // Recommended, but not required.
            },
        )
    }
}
