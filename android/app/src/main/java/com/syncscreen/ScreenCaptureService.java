package com.syncscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ScreenCapturer screenCapturer;

    public static class ScreenCapturer implements VideoCapturer {
        private MediaProjection mediaProjection;
        private VirtualDisplay virtualDisplay;
        private CapturerObserver capturerObserver;
        private SurfaceTextureHelper surfaceTextureHelper;
        private int width;
        private int height;

        public ScreenCapturer(MediaProjection mediaProjection, int width, int height) {
            this.mediaProjection = mediaProjection;
            this.width = width;
            this.height = height;
        }

        @Override
        public void initialize(SurfaceTextureHelper surfaceTextureHelper,
                             android.content.Context context,
                             CapturerObserver capturerObserver) {
            this.surfaceTextureHelper = surfaceTextureHelper;
            this.capturerObserver = capturerObserver;
        }

        @Override
        public void startCapture(int width, int height, int framerate) {
            this.width = width;
            this.height = height;

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    new Surface(surfaceTextureHelper.getSurfaceTexture()),
                    null, null);
        }

        @Override
        public void stopCapture() throws InterruptedException {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        }

        @Override
        public void changeCaptureFormat(int width, int height, int framerate) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void dispose() {
            try {
                stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        }

        @Override
        public boolean isScreencast() {
            return true;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            if (resultCode != -1 && data != null) {
                startScreenCapture(resultCode, data);
            }
        }

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private void startScreenCapture(int resultCode, Intent data) {
        MediaProjectionManager projectionManager =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        screenCapturer = new ScreenCapturer(mediaProjection, width, height);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Screen sharing is active");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SyncScreen")
                .setContentText("屏幕共享进行中...")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    public ScreenCapturer getScreenCapturer() {
        return screenCapturer;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCaptureBinder();
    }

    public class ScreenCaptureBinder extends android.os.Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenCapturer != null) {
            screenCapturer.dispose();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }
}
