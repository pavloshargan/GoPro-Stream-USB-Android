package com.example.streamtest2;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import android.os.Handler;
import android.os.Looper;

public class StreamHelper {
    boolean restartIfNeeded = true;
    private String ffmpegOutputUri = "udp://@localhost:8555";
    private String streamInputUri = "udp://:8554";
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private Thread ffmpegThread;
    private boolean ffmpegStarted = false;
    private Handler handler = new Handler(Looper.getMainLooper()); // UI thread handler

    public StreamHelper(Context context) {
        createPlayer(context);
    }

    public void setPlayerView(PlayerView playerView) {
        this.playerView = playerView;
        if (exoPlayer != null && playerView != null) {
            playerView.setPlayer(exoPlayer);
        }
    }

    // Setup FFmpeg process
    public void setupFFmpeg(Context context) {
        ffmpegThread = new Thread(() -> {
            try {
                String command = "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i " + streamInputUri + " -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316";
                FFmpegKit.executeAsync(command, session -> {
                    // This callback will only handle FFmpeg session results, if needed
                    Log.i("FFmpeg session", "FFmpeg session ended with return code: " + session.getReturnCode());
                });

                // Immediately proceed after starting FFmpeg without waiting for the process to finish
                Log.i("FFmpeg startup", "FFmpeg command triggered.");
                ffmpegStarted = true;
                Log.i("FFmpeg startup", "Setting up player");

                // Add a delay before setting up the player to ensure FFmpeg is streaming
                handler.postDelayed(this::setupPlayer, 1000);

                handler.postDelayed(() -> restartStream(context), 10000);

            } catch (Exception e) {
                Log.e("FFmpeg", "Exception on command execution: " + e);
                ffmpegStarted = false;
            }
        });
        ffmpegThread.start();
    }

    private void restartStream(Context context) {
        Log.i("StreamHelper restart", "restarting everything");
        if (restartIfNeeded && !exoPlayer.isPlaying()) {  // Check if the stream is playing
            createPlayer(context);
            stopFFmpeg();
            setupFFmpeg(context);
        }
    }

    private void createPlayer(Context context) {
        // Initialize ExoPlayer with the optimized load control
        exoPlayer = new ExoPlayer.Builder(context)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                                50,   // Min buffer duration before playback starts (ms)
                                100,   // Max buffer duration during playback (ms)
                                50,    // Min duration of data to retain in the buffer (ms)
                                50    // Buffer duration to retain while rebuffering (ms)
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build())
                .build();
    }

    private void setupPlayer() {
        if (ffmpegStarted && playerView != null) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(ffmpegOutputUri));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();
            Log.i("ExoPlayer", "Player started successfully.");
        } else {
            Log.e("ExoPlayer", "FFmpeg not started or PlayerView not set. Player setup delayed.");
        }
    }

    // Set listener to monitor buffering state
    public void setOnBufferingListener(OnBufferingListener onBufferingListener) {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                boolean isBuffering = (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE);
                onBufferingListener.onBufferingChanged(isBuffering);
            }
        });
    }

    // Interface for the buffering listener
    public interface OnBufferingListener {
        void onBufferingChanged(boolean isBuffering);
    }

    public void requestStream(Context context) {
        if(restartIfNeeded){
            new Thread(() -> {
                try {
                    boolean success = GoProHttpApi.requestStreamBlocking();
                    if (success) {
                        Log.i("RequestStream", "Stream started successfully");
                        handler.postDelayed(() -> setupFFmpeg(context), 1000);
                    } else {
                        Log.e("RequestStream", "Failed to start stream");
                        handler.postDelayed(() -> requestStream(context), 1000);
                    }
                } catch (Exception e) {
                    Log.e("RequestStream", "Exception during stream start: " + e.getMessage());
                }
            }).start();
        }
    }

    public void stopStream() {
        new Thread(() -> {
            try {
                boolean success = GoProHttpApi.stopStreamBlocking();
                if (success) {
                    Log.i("StopStream", "Stream stopped successfully");
                    stopFFmpeg();
                } else {
                    Log.e("StopStream", "Failed to stop stream");
                }
            } catch (Exception e) {
                Log.e("StopStream", "Exception during stream stop: " + e.getMessage());
            }
        }).start();
    }

    public void release() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
        }
        stopFFmpeg();
    }

    private void stopFFmpeg() {
        if (ffmpegThread != null && ffmpegThread.isAlive()) {
            ffmpegThread.interrupt();
            Log.i("FFmpeg", "FFmpeg process stopped.");
        }
        ffmpegStarted = false;
    }
}
