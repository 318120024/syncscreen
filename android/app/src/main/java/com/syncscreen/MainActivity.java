package com.syncscreen;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 200;

    private TextView statusText;
    private TextView roomIdText;
    private EditText roomIdInput;
    private EditText messageInput;
    private Button createRoomBtn;
    private Button joinRoomBtn;
    private Button copyRoomIdBtn;
    private Button showQrBtn;
    private Button scanQrBtn;
    private Button startVideoBtn;
    private Button startAudioBtn;
    private Button shareScreenBtn;
    private Button stopMediaBtn;
    private Button muteBtn;
    private Button sendMessageBtn;
    private Button enableControlBtn;
    private Button disableControlBtn;
    private ImageView qrCodeImage;
    private LinearLayout createRoomLayout;
    private LinearLayout joinRoomLayout;
    private LinearLayout roomDisplayLayout;
    private MaterialCardView mediaControlCard;
    private MaterialCardView controlCard;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private RecyclerView chatRecyclerView;
    private TabLayout tabLayout;

    private WebRTCManager webRTCManager;
    private String roomId;
    private boolean isHost;
    private boolean isMuted = false;
    private boolean controlEnabled = false;

    private EglBase eglBase;
    private MediaStream localStream;
    private VideoCapturer videoCapturer;
    private AudioTrack localAudioTrack;

    private ScreenCaptureService screenCaptureService;
    private boolean isScreenCaptureServiceBound = false;

    private List<ChatMessage> chatMessages = new ArrayList<>();
    private ChatAdapter chatAdapter;

    private ServiceConnection screenCaptureConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScreenCaptureService.ScreenCaptureBinder binder =
                    (ScreenCaptureService.ScreenCaptureBinder) service;
            screenCaptureService = binder.getService();
            isScreenCaptureServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isScreenCaptureServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        eglBase = EglBase.create();

        initViews();
        setupListeners();
        checkPermissions();

        webRTCManager = new WebRTCManager(this);
        setupWebRTCListener();

        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void initViews() {
        statusText = findViewById(R.id.statusText);
        roomIdText = findViewById(R.id.roomIdText);
        roomIdInput = findViewById(R.id.roomIdInput);
        messageInput = findViewById(R.id.messageInput);
        createRoomBtn = findViewById(R.id.createRoomBtn);
        joinRoomBtn = findViewById(R.id.joinRoomBtn);
        copyRoomIdBtn = findViewById(R.id.copyRoomIdBtn);
        showQrBtn = findViewById(R.id.showQrBtn);
        scanQrBtn = findViewById(R.id.scanQrBtn);
        startVideoBtn = findViewById(R.id.startVideoBtn);
        startAudioBtn = findViewById(R.id.startAudioBtn);
        shareScreenBtn = findViewById(R.id.shareScreenBtn);
        stopMediaBtn = findViewById(R.id.stopMediaBtn);
        muteBtn = findViewById(R.id.muteBtn);
        sendMessageBtn = findViewById(R.id.sendMessageBtn);
        enableControlBtn = findViewById(R.id.enableControlBtn);
        disableControlBtn = findViewById(R.id.disableControlBtn);
        qrCodeImage = findViewById(R.id.qrCodeImage);
        createRoomLayout = findViewById(R.id.createRoomLayout);
        joinRoomLayout = findViewById(R.id.joinRoomLayout);
        roomDisplayLayout = findViewById(R.id.roomDisplayLayout);
        mediaControlCard = findViewById(R.id.mediaControlCard);
        controlCard = findViewById(R.id.controlCard);
        localVideoView = findViewById(R.id.localVideoView);
        remoteVideoView = findViewById(R.id.remoteVideoView);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addTab(tabLayout.newTab().setText("创建房间"));
        tabLayout.addTab(tabLayout.newTab().setText("加入房间"));

        localVideoView.init(eglBase.getEglBaseContext(), null);
        remoteVideoView.init(eglBase.getEglBaseContext(), null);
    }

    private void setupListeners() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    createRoomLayout.setVisibility(View.VISIBLE);
                    joinRoomLayout.setVisibility(View.GONE);
                } else {
                    createRoomLayout.setVisibility(View.GONE);
                    joinRoomLayout.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        createRoomBtn.setOnClickListener(v -> createRoom());
        joinRoomBtn.setOnClickListener(v -> joinRoom());
        copyRoomIdBtn.setOnClickListener(v -> copyRoomId());
        showQrBtn.setOnClickListener(v -> toggleQrCode());
        scanQrBtn.setOnClickListener(v -> scanQrCode());
        startVideoBtn.setOnClickListener(v -> startVideo());
        startAudioBtn.setOnClickListener(v -> startAudio());
        shareScreenBtn.setOnClickListener(v -> startScreenShare());
        stopMediaBtn.setOnClickListener(v -> stopMedia());
        muteBtn.setOnClickListener(v -> toggleMute());
        sendMessageBtn.setOnClickListener(v -> sendMessage());
        enableControlBtn.setOnClickListener(v -> enableRemoteControl());
        disableControlBtn.setOnClickListener(v -> disableRemoteControl());
    }

    private void setupWebRTCListener() {
        webRTCManager.setListener(new WebRTCManager.WebRTCListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    updateStatus("已连接", "#4CAF50");
                    addChatMessage("系统", "连接已建立", ChatMessage.TYPE_SYSTEM);
                    showMediaControls();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    updateStatus("已断开", "#F44336");
                    addChatMessage("系统", "连接已断开", ChatMessage.TYPE_SYSTEM);
                });
            }

            @Override
            public void onRemoteStream(MediaStream stream) {
                runOnUiThread(() -> {
                    if (stream.videoTracks.size() > 0) {
                        stream.videoTracks.get(0).addSink(remoteVideoView);
                    }
                    addChatMessage("系统", "收到远程媒体流", ChatMessage.TYPE_SYSTEM);
                });
            }

            @Override
            public void onDataChannelMessage(String message) {
                runOnUiThread(() -> handleDataChannelMessage(message));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                    addChatMessage("系统", "错误: " + error, ChatMessage.TYPE_SYSTEM);
                });
            }
        });
    }

    private void createRoom() {
        roomId = generateRoomId();
        isHost = true;

        roomIdText.setText(roomId);
        roomDisplayLayout.setVisibility(View.VISIBLE);
        createRoomBtn.setEnabled(false);

        webRTCManager.createRoom(roomId);
        updateStatus("等待对方加入...", "#FF9800");
        addChatMessage("系统", "房间已创建: " + roomId, ChatMessage.TYPE_SYSTEM);
    }

    private void joinRoom() {
        String inputRoomId = roomIdInput.getText().toString().trim().toUpperCase();
        if (inputRoomId.isEmpty()) {
            Toast.makeText(this, "请输入房间ID", Toast.LENGTH_SHORT).show();
            return;
        }

        roomId = inputRoomId;
        isHost = false;

        webRTCManager.joinRoom(roomId);
        updateStatus("正在加入房间...", "#FF9800");
        addChatMessage("系统", "正在加入房间: " + roomId, ChatMessage.TYPE_SYSTEM);
    }

    private String generateRoomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void copyRoomId() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Room ID", roomId);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "房间ID已复制", Toast.LENGTH_SHORT).show();
    }

    private void toggleQrCode() {
        if (qrCodeImage.getVisibility() == View.GONE) {
            generateQrCode();
            qrCodeImage.setVisibility(View.VISIBLE);
            showQrBtn.setText("隐藏二维码");
        } else {
            qrCodeImage.setVisibility(View.GONE);
            showQrBtn.setText(R.string.show_qr);
        }
    }

    private void generateQrCode() {
        String url = "syncscreen://join?room=" + roomId;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            qrCodeImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void scanQrCode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("扫描房间二维码");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        barcodeLauncher.launch(options);
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scannedText = result.getContents();
                    if (scannedText.startsWith("syncscreen://join?room=")) {
                        String scannedRoomId = scannedText.substring(23);
                        roomIdInput.setText(scannedRoomId);
                        joinRoom();
                    }
                }
            });

    private void startVideo() {
        videoCapturer = createCameraCapturer();
        if (videoCapturer != null) {
            VideoSource videoSource = webRTCManager.getPeerConnectionFactory().createVideoSource(false);
            SurfaceTextureHelper surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);

            VideoTrack videoTrack = webRTCManager.getPeerConnectionFactory()
                    .createVideoTrack("video", videoSource);
            videoTrack.addSink(localVideoView);

            localStream = webRTCManager.getPeerConnectionFactory().createLocalMediaStream("local");
            localStream.addTrack(videoTrack);

            AudioSource audioSource = webRTCManager.getPeerConnectionFactory().createAudioSource(new MediaConstraints());
            localAudioTrack = webRTCManager.getPeerConnectionFactory().createAudioTrack("audio", audioSource);
            localStream.addTrack(localAudioTrack);

            webRTCManager.addLocalStream(localStream);

            stopMediaBtn.setEnabled(true);
            muteBtn.setEnabled(true);
            addChatMessage("系统", "视频已开启", ChatMessage.TYPE_SYSTEM);
        }
    }

    private void startAudio() {
        AudioSource audioSource = webRTCManager.getPeerConnectionFactory().createAudioSource(new MediaConstraints());
        localAudioTrack = webRTCManager.getPeerConnectionFactory().createAudioTrack("audio", audioSource);

        localStream = webRTCManager.getPeerConnectionFactory().createLocalMediaStream("local");
        localStream.addTrack(localAudioTrack);

        webRTCManager.addLocalStream(localStream);

        stopMediaBtn.setEnabled(true);
        muteBtn.setEnabled(true);
        addChatMessage("系统", "语音已开启", ChatMessage.TYPE_SYSTEM);
    }

    private void startScreenShare() {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            startForegroundService(serviceIntent);
            bindService(serviceIntent, screenCaptureConnection, Context.BIND_AUTO_CREATE);

            addChatMessage("系统", "屏幕共享已开启", ChatMessage.TYPE_SYSTEM);
            stopMediaBtn.setEnabled(true);
        }
    }

    private VideoCapturer createCameraCapturer() {
        CameraEnumerator enumerator;
        if (Camera2Enumerator.isSupported(this)) {
            enumerator = new Camera2Enumerator(this);
        } else {
            enumerator = new Camera1Enumerator(true);
        }

        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }

        for (String deviceName : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }

        return null;
    }

    private void stopMedia() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (localStream != null) {
            localStream.dispose();
            localStream = null;
        }

        if (isScreenCaptureServiceBound) {
            unbindService(screenCaptureConnection);
            stopService(new Intent(this, ScreenCaptureService.class));
            isScreenCaptureServiceBound = false;
        }

        stopMediaBtn.setEnabled(false);
        muteBtn.setEnabled(false);
        addChatMessage("系统", "媒体已停止", ChatMessage.TYPE_SYSTEM);
    }

    private void toggleMute() {
        if (localAudioTrack != null) {
            isMuted = !isMuted;
            localAudioTrack.setEnabled(!isMuted);
            muteBtn.setText(isMuted ? R.string.unmute : R.string.mute);
        }
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            webRTCManager.sendMessage(message);
            addChatMessage("我", message, ChatMessage.TYPE_ME);
            messageInput.setText("");
        }
    }

    private void enableRemoteControl() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先启用辅助功能服务", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        controlEnabled = true;
        enableControlBtn.setEnabled(false);
        disableControlBtn.setEnabled(true);
        addChatMessage("系统", "远程控制已启用", ChatMessage.TYPE_SYSTEM);
    }

    private void disableRemoteControl() {
        controlEnabled = false;
        enableControlBtn.setEnabled(true);
        disableControlBtn.setEnabled(false);
        addChatMessage("系统", "远程控制已禁用", ChatMessage.TYPE_SYSTEM);
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + RemoteControlService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(service);
    }

    private void handleDataChannelMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            if ("chat".equals(type)) {
                String msg = json.getString("message");
                addChatMessage("对方", msg, ChatMessage.TYPE_OTHER);
            } else if ("control".equals(type)) {
                String action = json.getString("action");
                float x = (float) json.getDouble("x");
                float y = (float) json.getDouble("y");

                if (controlEnabled && "touch".equals(action)) {
                    RemoteControlService service = RemoteControlService.getInstance();
                    if (service != null) {
                        service.performTouch(x, y);
                        addChatMessage("系统", String.format("执行触摸: (%.1f%%, %.1f%%)",
                                x * 100, y * 100), ChatMessage.TYPE_SYSTEM);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message", e);
        }
    }

    private void showMediaControls() {
        mediaControlCard.setVisibility(View.VISIBLE);
        controlCard.setVisibility(View.VISIBLE);
        messageInput.setEnabled(true);
        sendMessageBtn.setEnabled(true);
    }

    private void updateStatus(String text, String color) {
        statusText.setText(text);
        statusText.setTextColor(android.graphics.Color.parseColor(color));
    }

    private void addChatMessage(String sender, String message, int type) {
        chatMessages.add(new ChatMessage(sender, message, type));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要相机和麦克风权限", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRTCManager != null) {
            webRTCManager.close();
        }
        if (localVideoView != null) {
            localVideoView.release();
        }
        if (remoteVideoView != null) {
            remoteVideoView.release();
        }
        if (eglBase != null) {
            eglBase.release();
        }
        if (isScreenCaptureServiceBound) {
            unbindService(screenCaptureConnection);
        }
    }
}
