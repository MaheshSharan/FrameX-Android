package com.framex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.featurediscovery.FeatureDiscoveryHost
import com.framex.app.ui.navigation.FrameXNavGraph
import com.framex.app.ui.theme.FrameXTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorIndex by settingsRepository.overlayColorIndex.collectAsState()

            FrameXTheme(colorIndex = colorIndex) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FeatureDiscoveryHost {
                        FrameXNavGraph()
                    }
                }
            }
        }
    }
}
