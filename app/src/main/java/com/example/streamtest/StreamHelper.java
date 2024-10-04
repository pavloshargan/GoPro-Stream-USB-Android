package com.example.streamtest;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StreamHelper {
    private OkHttpClient httpClient;
    private String ffmpegOutputUri = "udp://@localhost:8555";
    private String streamInputUri = "udp://:8554";
    private DatagramSocket udpSocket;

    // VLC-specific components
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;

    public StreamHelper(Context context) {
        initNetwork();
        setupFFmpeg();
        createPlayer(context);
    }

    public void setVideoLayout(VLCVideoLayout layout) {
        this.videoLayout = layout;
        if (mediaPlayer != null && videoLayout != null) {
            mediaPlayer.attachViews(videoLayout, null, false, false);
        }
    }

    private void initNetwork() {
        try {
            udpSocket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }

        httpClient = new OkHttpClient();
    }

    private void setupFFmpeg() {
        Thread ffmpegThread = new Thread(() -> {
            try {
                String command = "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i " + streamInputUri + " -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316";
                FFmpegKit.execute(command);
            } catch (Exception e) {
                Log.e("FFmpeg", "Exception on command execution: " + e);
            }
        });
        ffmpegThread.start();
    }

    private void createPlayer(Context context) {
        // Initialize VLC components
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        libVLC = new LibVLC(context, options);
        mediaPlayer = new MediaPlayer(libVLC);

        // Prepare the media with the UDP stream URL
        Media media = new Media(libVLC, Uri.parse(ffmpegOutputUri));
        media.setHWDecoderEnabled(true, false); // Enable hardware decoding if available
        media.addOption(":network-caching=1000"); // Adjust network caching if needed

        mediaPlayer.setMedia(media);
        mediaPlayer.play(); // Start playback immediately
    }

    public void requestStream() {
        String startStreamQuery = "http://10.5.5.9:8080/gopro/camera/stream/start"; // Adjust GoPro's stream URL
        Request request = new Request.Builder().url(HttpUrl.get(startStreamQuery)).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("RequestStream", "Failed to start stream: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    Log.e("RequestStream", "Stream request failed");
                } else {
                    Log.i("RequestStream", "Stream started successfully");
                }
                response.close();
            }
        });
    }

    public void stopStream() {
        String stopStreamQuery = "http://10.5.5.9:8080/gopro/camera/stream/stop"; // Adjust GoPro's stop stream URL
        Request request = new Request.Builder().url(HttpUrl.get(stopStreamQuery)).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("StopStream", "Failed to stop stream: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    Log.e("StopStream", "Stop stream request failed");
                } else {
                    Log.i("StopStream", "Stream stopped successfully");
                }
                response.close();
            }
        });
    }

    public void release() {
        if (udpSocket != null) udpSocket.disconnect();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
    }
}
