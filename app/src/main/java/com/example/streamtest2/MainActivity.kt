package com.example.streamtest2

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamtest2.ui.theme.StreamTest2Theme
import com.google.android.exoplayer2.ui.PlayerView


object DataStore {
    var livestreamEnabled by mutableStateOf(false)
}


class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var streamHelper: StreamHelper


    @Composable
    fun LivestreamView(streamHelper: StreamHelper) {
        // State to track if the stream is buffering or ready
        val isBuffering = remember { mutableStateOf(true) }

        // Box to layer the PlayerView and CircularProgressIndicator
        Box(modifier = Modifier.fillMaxSize()) {
            // Embed the ExoPlayer PlayerView inside a Compose UI using AndroidView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // Create a FrameLayout as a container for the ExoPlayer view
                    val frameLayout = FrameLayout(context)

                    // Create the PlayerView programmatically
                    val playerView = PlayerView(context).apply {
                        // Disable the default controller (removes play/pause/seek buttons)
                        useController = false
                    }

                    // Add PlayerView to the FrameLayout
                    frameLayout.addView(playerView)

                    // Set the PlayerView in the StreamHelper class
                    streamHelper.setPlayerView(playerView)

                    // Attach a listener to ExoPlayer to track buffering state
                    streamHelper.setOnBufferingListener { buffering ->
                        isBuffering.value = buffering
                    }

                    frameLayout // Return the FrameLayout containing PlayerView
                }
            )

            // Show CircularProgressIndicator if the stream is buffering
            if (isBuffering.value) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }


    fun toggleLivestream() {
        DataStore.livestreamEnabled = !DataStore.livestreamEnabled
        if (!DataStore.livestreamEnabled) {
            streamHelper.restartIfNeeded = false
            streamHelper.release()
            streamHelper.stopStream()
        } else {
            streamHelper = StreamHelper(this)
            streamHelper.requestStream(this)
        }
    }

    override fun onBackPressed() {
        if (DataStore.livestreamEnabled) {
            toggleLivestream()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GoProHttpApi.initialize(this)

        streamHelper = StreamHelper(this)
        streamHelper.stopStream()

        // Set up Jetpack Compose content
        setContent {
            StreamTest2Theme {
                if (DataStore.livestreamEnabled) {
                    LivestreamView(streamHelper)
                } else {
                    Button(onClick = { toggleLivestream() }) {
                        Text("Start Stream")
                    }
                }
            }
        }

        // Start streaming
    }


    override fun onDestroy() {
        super.onDestroy()
        streamHelper.stopStream()
        streamHelper.release()
    }
}
