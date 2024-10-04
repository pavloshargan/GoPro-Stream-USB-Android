package com.example.streamtest2

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
    private const val GOPRO_AP_BASE_URL = "http://10.5.5.9" // Replace with your base URL
    private var baseUrl: String = GOPRO_AP_BASE_URL
    private var globalOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var context: Context

    fun initialize(context: Context) {
        GoProHttpApi.context = context
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

    // Start streaming
    @JvmStatic
    suspend fun requestStream(): Boolean {
        val result = withTimeoutOrNull(10000L) { // Timeout after 10 seconds
            stopRecording() // Stop recording before starting the stream
            Thread.sleep(2000) // Wait to ensure the recording has stopped
            suspendCancellableCoroutine<Boolean> { continuation ->
                val startStreamQuery = "$baseUrl/gopro/camera/stream/start"
                makeGetRequest(globalOkHttpClient, startStreamQuery) { success, response ->
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

    // Stop streaming
    @JvmStatic
    suspend fun stopStream(): Boolean {
        val result = withTimeoutOrNull(10000L) { // Timeout after 10 seconds
            suspendCancellableCoroutine<Boolean> { continuation ->
                val stopStreamQuery = "$baseUrl/gopro/camera/stream/stop"
                makeGetRequest(globalOkHttpClient, stopStreamQuery) { success, response ->
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

    // Make GET request
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



    // Stop recording (simplified example method)
    private fun stopRecording() {
        Log.i(TAG, "Stopping recording...")
        val stopRecordingQuery = "$baseUrl/gopro/camera/shutter/stop"
        makeGetRequest(globalOkHttpClient, stopRecordingQuery) { success, response ->
            if (success) {
                Log.i(TAG, "Recording stopped successfully")
            } else {
                Log.e(TAG, "Failed to stop recording")
            }
        }
    }
}
