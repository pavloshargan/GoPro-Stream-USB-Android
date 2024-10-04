package com.example.streamtest

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamtest.ui.theme.StreamTestTheme
import org.videolan.libvlc.util.VLCVideoLayout

class MainActivity : ComponentActivity() {
    private lateinit var streamHelper: StreamHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize StreamHelper to handle media playback and streaming
        streamHelper = StreamHelper(this)
        streamHelper.requestStream()

        // Set up Jetpack Compose content
        setContent {
            StreamTestTheme {
                VLCPlayerView(streamHelper)
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

@Composable
fun VLCPlayerView(streamHelper: StreamHelper) {
    // Embed the VLCVideoLayout inside a Compose UI using AndroidView
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // Create a FrameLayout as a container for the VLC video layout
            val frameLayout = FrameLayout(context)

            // Create the VLCVideoLayout programmatically
            val vlcVideoLayout = VLCVideoLayout(context)

            // Add VLCVideoLayout to the FrameLayout
            frameLayout.addView(vlcVideoLayout)

            // Set the video layout in the StreamHelper class
            streamHelper.setVideoLayout(vlcVideoLayout)

            frameLayout // Return the FrameLayout containing VLCVideoLayout
        }
    )
}
