package com.example.streamtest2

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object GoProHttpApi {

    private const val TAG = "GoProHttpApi"
    const val GOPRO_AP_BASE_URL = "http://10.5.5.9:8080/"
    const val GOPRO_USB_BASE_URL = "http://172.2X.1YZ.51:8080/"  // where XYZ are the last three digits of the camera's serial number
    const val GOPRO_NETWORK_ADDRESS =  "172.2X.1YZ.5"
    
    private var baseUrl: String = GOPRO_AP_BASE_URL //default for wifi
    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var context: Context

    fun initialize(context: Context) {
        GoProHttpApi.context = context
    }

    fun mountGoproWired(){
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList

        for (device in deviceList.values) {
            if (device.manufacturerName == "GoPro") {
                if (!usbManager.hasPermission(device)) {
                    Log.e( TAG, "Error: unsufficient permissions for usb device")
//                        requestUsbPermission(context, device)
                } else {
                    // Handle the case where permission is already granted
                    val serialNumber = device.serialNumber
                    Log.e( TAG,"device details $device")
                    Log.e( TAG,"GoPro $serialNumber connected")

                    // GoPro serial number logic
                    if (serialNumber != null) {
                        goproWiredConnectedRoutines(serialNumber)
                    }
                }
            } else {
                println("Device is not a GoPro: ${device.manufacturerName}")
            }
        }
    }

    fun goproWiredConnectedRoutines(serialNumber: String){
        CoroutineScope(Dispatchers.IO).launch {
            val goproNetworkAddess = GOPRO_NETWORK_ADDRESS
                .replace("X", serialNumber[serialNumber.length - 3].toString())
                .replace("Y", serialNumber[serialNumber.length - 2].toString())
                .replace("Z", serialNumber[serialNumber.length - 1].toString())

            baseUrl = GOPRO_USB_BASE_URL
                .replace("X", serialNumber[serialNumber.length - 3].toString())
                .replace("Y", serialNumber[serialNumber.length - 2].toString())
                .replace("Z", serialNumber[serialNumber.length - 1].toString())

            repeat(5) { attempt ->
                val client = getGoProEthernetNetworkClient(context, goproNetworkAddess)
                if (client != null) {
                    okHttpClient = client
                    setWiredMode(0) {
                        setWiredMode(1) {}
                    }
                    return@launch
                } else {
                    if (attempt < 4) { // Avoid sleeping after the final attempt
                        Thread.sleep(3000L) // Wait 3 seconds before trying again
                    }
                }
            }
        }
    }

    fun getGoProEthernetNetworkClient(context: Context, goproIpAddress: String): OkHttpClient? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val allNetworks = connectivityManager.allNetworks
        var selectedNetwork: Network? = null

        // Loop through all networks to find the one with the GoPro IP address
        for (network in allNetworks) {
            val linkProperties = connectivityManager.getLinkProperties(network)

            if (linkProperties != null) {
                for (linkAddress in linkProperties.linkAddresses) {
                    // Check if the network contains the GoPro IP address
                    println("linkAddress.address.hostAddress ${linkAddress.address.hostAddress} goproIpAddress ${goproIpAddress}")
                    if (linkAddress.address.hostAddress.contains(goproIpAddress)) {
                        selectedNetwork = network
                        break
                    }
                }
                if (selectedNetwork != null) break
            }
        }

        return if (selectedNetwork != null) {
            // Create the OkHttpClient using a custom SocketFactory
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .socketFactory( selectedNetwork.socketFactory)
                .build()
        } else {
            null // Return null if the GoPro network is not found
        }
    }

    // Kotlin-friendly blocking wrapper for the suspend function
    @JvmStatic
    fun requestStreamBlocking(): Boolean {
        return runBlocking { requestStream() }
    }

    @JvmStatic
    fun stopStreamBlocking(): Boolean {
        return runBlocking { stopStream() }
    }

    @JvmStatic
    suspend fun requestStream(): Boolean {
        val result = withTimeoutOrNull(10000L) { // Timeout after 10 seconds
            stopRecording() // Stop recording before starting the stream
            Thread.sleep(2000) // Wait to ensure the recording has stopped
            suspendCancellableCoroutine<Boolean> { continuation ->
                val startStreamQuery = "${baseUrl}gopro/camera/stream/start"
                makeGetRequest(okHttpClient, startStreamQuery) { success, response ->
                    if (success) {
                        Log.i(TAG, "Stream started successfully")
                    } else {
                        Log.e(TAG, "Failed to start stream")
                    }
                    continuation.resume(success)
                }
            }
        }
        return result ?: false
    }

    @JvmStatic
    suspend fun stopStream(): Boolean {
        val result = withTimeoutOrNull(10000L) { // Timeout after 10 seconds
            suspendCancellableCoroutine<Boolean> { continuation ->
                val stopStreamQuery = "${baseUrl}gopro/camera/stream/stop"
                makeGetRequest(okHttpClient, stopStreamQuery) { success, response ->
                    if (success) {
                        Log.i(TAG, "Stream stopped successfully")
                    } else {
                        Log.e(TAG, "Failed to stop stream")
                    }
                    continuation.resume(success)
                }
            }
        }
        return result ?: false
    }

    fun setWiredMode(value: Int, completion: () -> Unit) {
        Log.e( TAG,"GPAPI setWiredMode..")
        makeGetRequest(
            okHttpClient,
            "${baseUrl}gopro/camera/control/wired_usb?p=${value}"
        ) { success, _ ->
            if (success) {
                Log.e( TAG,"GPAPI setWiredMode success")
            } else {
                Log.w( TAG,"GPAPI setWiredMode error")
            }
            completion()
        }
    }

    private fun makeGetRequest(client: OkHttpClient, url: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed: ${e.message}")
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Ensure responseBody is handled properly
                    val responseBody: ResponseBody? = response.body
                    val responseBodyString = responseBody?.string()

                    if (responseBodyString != null) {
                        Log.i(TAG, "Request succeeded: $responseBodyString")
                        callback(true, responseBodyString)
                    } else {
                        Log.e(TAG, "Response body was null")
                        callback(false, null)
                    }
                } else {
                    Log.e(TAG, "Request failed with code: ${response.code}")
                    callback(false, null)
                }
            }
        })
    }

    fun startRecording() {
        Log.i(TAG, "Starting recording...")
        val stopRecordingQuery = "${baseUrl}gopro/camera/shutter/start"
        makeGetRequest(okHttpClient, stopRecordingQuery) { success, response ->
            if (success) {
                Log.i(TAG, "Recording started successfully")
            } else {
                Log.e(TAG, "Failed to start recording")
            }
        }
    }

    fun stopRecording() {
        Log.i(TAG, "Stopping recording...")
        val stopRecordingQuery = "${baseUrl}gopro/camera/shutter/stop"
        makeGetRequest(okHttpClient, stopRecordingQuery) { success, response ->
            if (success) {
                Log.i(TAG, "Recording stopped successfully")
            } else {
                Log.e(TAG, "Failed to stop recording")
            }
        }
    }
}
