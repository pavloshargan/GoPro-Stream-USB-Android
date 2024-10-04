package com.example.streamtest2

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.Space
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamtest2.ui.theme.StreamTest2Theme
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.launch


object DataStore {
    var livestreamEnabled by mutableStateOf(false)
}

class UsbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (it.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d("USB ", "USB device attached: ${device.deviceName}")
                        // Request permission for the attached device
                        context?.let { ctx ->
                            (ctx as? MainActivity)?.requestUsbPermission(context, device)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var streamHelper: StreamHelper

    private val usbReceiver = UsbReceiver()
    private val ACTION_USB_PERMISSION = "com.example.streamtest2.USB_PERMISSION"


    fun requestUsbPermission(context: Context, device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_EXPORTED)
        usbManager.requestPermission(device, permissionIntent)
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (ACTION_USB_PERMISSION == it.action) {
                    synchronized(this) {
                        val device: UsbDevice? = it.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (it.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                val serialNumber = it.serialNumber
                                Toast.makeText(context, "Permissions granted for: $serialNumber", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            println("Permission denied for device $device")
                        }
                        context?.unregisterReceiver(this)  // Unregister the receiver after handling
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        println("OnNewIntent")
        if (intent != null) {
            super.onNewIntent(intent)
        }
        println("Intent action: ${intent?.action}")
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            device?.let {
                Log.d("USB", "Requesting permission for device: ${device.deviceName}")
                GoProHttpApi.mountGoproWired()
            }
        }
        else if (intent?.action != null){
            val flags = intent?.flags
            if (flags != null) {
                if ((flags and Intent.FLAG_ACTIVITY_CLEAR_TOP == Intent.FLAG_ACTIVITY_CLEAR_TOP) &&
                    (flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == Intent.FLAG_ACTIVITY_CLEAR_TASK)) {
                    setIntent(intent)
                } else {
                    finish() // Finish this activity if another instance is found
                }
            }
        }

    }

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

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)

        streamHelper = StreamHelper(this)
        streamHelper.stopStream()
        setContent {
            val scope =
                rememberCoroutineScope() // Create a coroutine scope to handle suspend functions

            StreamTest2Theme {
                if (DataStore.livestreamEnabled) {
                    LivestreamView(streamHelper)
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Button(onClick = { toggleLivestream() }) {
                            Text("Start Stream")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = {
                                scope.launch {
                                    GoProHttpApi.startRecording() // Call startRecording in a coroutine
                                }
                            }) {
                                Text("Start Recording")
                            }

                            Button(onClick = {
                                scope.launch {
                                    GoProHttpApi.stopRecording() // Call stopRecording in a coroutine
                                }
                            }) {
                                Text("Stop Recording")
                            }
                        }
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        streamHelper.stopStream()
        streamHelper.release()

        if(this::streamHelper.isInitialized){
            streamHelper.release()
        }
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e("USB","Error while unregistering receiver: ${e.message}")
        }
    }
}
