package com.syncscreen;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WebRTCManager {
    private static final String TAG = "WebRTCManager";

    private Context context;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private DataChannel dataChannel;
    private DatabaseReference roomRef;
    private String roomId;
    private boolean isHost;

    private WebRTCListener listener;

    public interface WebRTCListener {
        void onConnected();
        void onDisconnected();
        void onRemoteStream(MediaStream stream);
        void onDataChannelMessage(String message);
        void onError(String error);
    }

    public WebRTCManager(Context context) {
        this.context = context;
        initializePeerConnectionFactory();
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();
    }

    public void setListener(WebRTCListener listener) {
        this.listener = listener;
    }

    public void createRoom(String roomId) {
        this.roomId = roomId;
        this.isHost = true;
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        createPeerConnection();
        createDataChannel();
        createOffer();
        listenForAnswer();
        listenForIceCandidates(false);
    }

    public void joinRoom(String roomId) {
        this.roomId = roomId;
        this.isHost = false;
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        createPeerConnection();
        listenForOffer();
        listenForIceCandidates(true);
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    if (listener != null) listener.onConnected();
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                           iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    if (listener != null) listener.onDisconnected();
                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                if (listener != null) listener.onRemoteStream(mediaStream);
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(DataChannel dc) {
                dataChannel = dc;
                setupDataChannel();
            }

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        });
    }

    private void createDataChannel() {
        DataChannel.Init init = new DataChannel.Init();
        dataChannel = peerConnection.createDataChannel("control", init);
        setupDataChannel();
    }

    private void setupDataChannel() {
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {}

            @Override
            public void onStateChange() {
                Log.d(TAG, "DataChannel state: " + dataChannel.state());
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                String message = new String(bytes, StandardCharsets.UTF_8);

                if (listener != null) listener.onDataChannelMessage(message);
            }
        });
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sd) {}

                    @Override
                    public void onSetSuccess() {
                        sendOffer(sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {}
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {
                if (listener != null) listener.onError("Create offer failed: " + s);
            }

            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }

    private void sendOffer(SessionDescription sdp) {
        roomRef.child("host").child("offer").child("type").setValue(sdp.type.canonicalForm());
        roomRef.child("host").child("offer").child("sdp").setValue(sdp.description);
    }

    private void listenForOffer() {
        roomRef.child("host").child("offer").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String type = snapshot.child("type").getValue(String.class);
                    String sdp = snapshot.child("sdp").getValue(String.class);

                    SessionDescription offer = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), sdp);

                    peerConnection.setRemoteDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sd) {}

                        @Override
                        public void onSetSuccess() {
                            createAnswer();
                        }

                        @Override
                        public void onCreateFailure(String s) {}

                        @Override
                        public void onSetFailure(String s) {}
                    }, offer);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (listener != null) listener.onError("Failed to get offer: " + error.getMessage());
            }
        });
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sd) {}

                    @Override
                    public void onSetSuccess() {
                        sendAnswer(sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {}
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {
                if (listener != null) listener.onError("Create answer failed: " + s);
            }

            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }

    private void sendAnswer(SessionDescription sdp) {
        roomRef.child("guest").child("answer").child("type").setValue(sdp.type.canonicalForm());
        roomRef.child("guest").child("answer").child("sdp").setValue(sdp.description);
    }

    private void listenForAnswer() {
        roomRef.child("guest").child("answer").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String type = snapshot.child("type").getValue(String.class);
                    String sdp = snapshot.child("sdp").getValue(String.class);

                    SessionDescription answer = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), sdp);

                    peerConnection.setRemoteDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sd) {}

                        @Override
                        public void onSetSuccess() {}

                        @Override
                        public void onCreateFailure(String s) {}

                        @Override
                        public void onSetFailure(String s) {}
                    }, answer);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void sendIceCandidate(IceCandidate candidate) {
        String path = isHost ? "host" : "guest";
        DatabaseReference iceRef = roomRef.child(path).child("ice").push();
        iceRef.child("sdpMid").setValue(candidate.sdpMid);
        iceRef.child("sdpMLineIndex").setValue(candidate.sdpMLineIndex);
        iceRef.child("candidate").setValue(candidate.sdp);
    }

    private void listenForIceCandidates(boolean fromHost) {
        String path = fromHost ? "host" : "guest";
        roomRef.child(path).child("ice").addChildEventListener(new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String sdpMid = snapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Integer.class);
                String candidate = snapshot.child("candidate").getValue(String.class);

                if (sdpMid != null && sdpMLineIndex != null && candidate != null) {
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
                    peerConnection.addIceCandidate(iceCandidate);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    public void addLocalStream(MediaStream stream) {
        if (peerConnection != null) {
            peerConnection.addStream(stream);
        }
    }

    public void sendMessage(String message) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "chat");
                json.put("message", message);

                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                dataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (JSONException e) {
                Log.e(TAG, "Error sending message", e);
            }
        }
    }

    public void sendControlEvent(String action, float x, float y) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "control");
                json.put("action", action);
                json.put("x", x);
                json.put("y", y);

                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                dataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (JSONException e) {
                Log.e(TAG, "Error sending control event", e);
            }
        }
    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return peerConnectionFactory;
    }

    public void close() {
        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
    }
}
