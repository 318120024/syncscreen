package com.syncscreen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class RemoteControlService extends AccessibilityService {
    private static final String TAG = "RemoteControlService";
    private static RemoteControlService instance;

    private int screenWidth;
    private int screenHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        Log.d(TAG, "RemoteControlService created. Screen: " + screenWidth + "x" + screenHeight);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理accessibility事件
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    public static RemoteControlService getInstance() {
        return instance;
    }

    public void performTouch(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // 将相对坐标转换为绝对坐标
            float absoluteX = x * screenWidth;
            float absoluteY = y * screenHeight;

            Log.d(TAG, "Performing touch at: " + absoluteX + ", " + absoluteY);

            // 创建点击手势
            Path path = new Path();
            path.moveTo(absoluteX, absoluteY);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 100);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            boolean dispatched = dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "Gesture completed");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Gesture cancelled");
                }
            }, null);

            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch gesture");
            }
        }
    }

    public void performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            float absStartX = startX * screenWidth;
            float absStartY = startY * screenHeight;
            float absEndX = endX * screenWidth;
            float absEndY = endY * screenHeight;

            Path path = new Path();
            path.moveTo(absStartX, absStartY);
            path.lineTo(absEndX, absEndY);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, duration);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            dispatchGesture(builder.build(), null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "RemoteControlService destroyed");
    }
}
