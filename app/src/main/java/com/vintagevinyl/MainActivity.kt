package com.vintagevinyl

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.vintagevinyl.ui.AppRoot
import com.vintagevinyl.ui.components.LocalArtwork
import com.vintagevinyl.ui.theme.VintageTheme
import com.vintagevinyl.ui.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as VintageVinylApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = AppViewModelFactory(container)
        setContent {
            VintageTheme {
                // Needed on Android 13+ for the playback notification to be visible.
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                CompositionLocalProvider(LocalArtwork provides container.artwork) {
                    AppRoot(remember { factory })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.playback.connect()
        container.library.refresh()
    }

    override fun onStop() {
        container.playback.disconnect()
        super.onStop()
    }
}
