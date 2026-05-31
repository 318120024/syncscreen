// Firebase配置 - 请替换为你自己的配置
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
  databaseURL: "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID"
};

// 初始化Firebase
firebase.initializeApp(firebaseConfig);
const database = firebase.database();

// WebRTC配置
const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' }
  ]
};

// 全局变量
let peerConnection = null;
let dataChannel = null;
let localStream = null;
let roomId = null;
let isHost = false;
let controlEnabled = false;

// 更新状态
function updateStatus(text, type = 'waiting') {
  const statusEl = document.getElementById('status');
  statusEl.textContent = text;
  statusEl.className = `status-badge status-${type}`;
}

// 添加聊天消息
function addChatMessage(sender, message, type = 'other') {
  const chatBox = document.getElementById('chatBox');
  const msgDiv = document.createElement('div');
  msgDiv.className = `chat-message ${type}`;

  const time = new Date().toLocaleTimeString();
  msgDiv.innerHTML = `<div style="font-size: 11px; opacity: 0.7; margin-bottom: 4px;">${time}</div><div>${type === 'system' ? message : `${sender}: ${message}`}</div>`;

  chatBox.appendChild(msgDiv);
  chatBox.scrollTop = chatBox.scrollHeight;
}

// 切换标签页
function switchTab(tabName) {
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

  event.target.classList.add('active');
  document.getElementById(`tab-${tabName}`).classList.add('active');
}

// 生成随机房间ID
function generateRoomId() {
  return Math.random().toString(36).substring(2, 10).toUpperCase();
}

// 创建房间
document.getElementById('createRoomBtn').addEventListener('click', async () => {
  roomId = generateRoomId();
  isHost = true;

  document.getElementById('roomIdValue').textContent = roomId;
  document.getElementById('roomDisplay').style.display = 'block';
  document.getElementById('createRoomBtn').disabled = true;

  // 生成二维码
  const qrcodeDiv = document.getElementById('qrcode');
  qrcodeDiv.innerHTML = '';
  qrcodeDiv.style.display = 'block';
  const url = `${window.location.origin}${window.location.pathname}?room=${roomId}`;
  new QRCode(qrcodeDiv, {
    text: url,
    width: 256,
    height: 256
  });

  updateStatus('等待对方加入...', 'waiting');
  addChatMessage('系统', `房间已创建: ${roomId}`, 'system');

  // 监听对方加入
  await setupHostConnection();
});

// 复制房间ID
document.getElementById('copyRoomBtn').addEventListener('click', () => {
  navigator.clipboard.writeText(roomId);
  addChatMessage('系统', '房间ID已复制', 'system');
});

// 加入房间
document.getElementById('joinRoomBtn').addEventListener('click', async () => {
  const inputRoomId = document.getElementById('roomIdInput').value.trim().toUpperCase();
  if (!inputRoomId) {
    alert('请输入房间ID');
    return;
  }

  roomId = inputRoomId;
  isHost = false;

  updateStatus('正在加入房间...', 'waiting');
  addChatMessage('系统', `正在加入房间: ${roomId}`, 'system');

  await setupGuestConnection();
});

// 主机端连接设置
async function setupHostConnection() {
  peerConnection = new RTCPeerConnection(rtcConfig);

  // 创建数据通道
  dataChannel = peerConnection.createDataChannel('control');
  setupDataChannel(dataChannel);

  // ICE候选
  peerConnection.onicecandidate = (event) => {
    if (event.candidate) {
      database.ref(`rooms/${roomId}/host/ice`).push(event.candidate.toJSON());
    }
  };

  // 接收远程流
  peerConnection.ontrack = (event) => {
    addVideoStream('remote', event.streams[0]);
  };

  // 创建Offer
  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  // 保存Offer到Firebase
  await database.ref(`rooms/${roomId}/host/offer`).set({
    type: offer.type,
    sdp: offer.sdp
  });

  // 监听Guest的Answer
  database.ref(`rooms/${roomId}/guest/answer`).on('value', async (snapshot) => {
    const answer = snapshot.val();
    if (answer && peerConnection.signalingState === 'have-local-offer') {
      await peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
      updateStatus('已连接', 'connected');
      addChatMessage('系统', '对方已加入房间', 'system');
      showMediaControls();
    }
  });

  // 监听Guest的ICE候选
  database.ref(`rooms/${roomId}/guest/ice`).on('child_added', async (snapshot) => {
    const candidate = snapshot.val();
    if (candidate) {
      await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
    }
  });
}

// 客户端连接设置
async function setupGuestConnection() {
  // 检查房间是否存在
  const roomSnapshot = await database.ref(`rooms/${roomId}/host/offer`).once('value');
  if (!roomSnapshot.exists()) {
    alert('房间不存在');
    updateStatus('连接失败', 'error');
    return;
  }

  peerConnection = new RTCPeerConnection(rtcConfig);

  // 接收数据通道
  peerConnection.ondatachannel = (event) => {
    dataChannel = event.channel;
    setupDataChannel(dataChannel);
  };

  // ICE候选
  peerConnection.onicecandidate = (event) => {
    if (event.candidate) {
      database.ref(`rooms/${roomId}/guest/ice`).push(event.candidate.toJSON());
    }
  };

  // 接收远程流
  peerConnection.ontrack = (event) => {
    addVideoStream('remote', event.streams[0]);
  };

  // 获取Offer
  const offer = roomSnapshot.val();
  await peerConnection.setRemoteDescription(new RTCSessionDescription(offer));

  // 创建Answer
  const answer = await peerConnection.createAnswer();
  await peerConnection.setLocalDescription(answer);

  // 保存Answer到Firebase
  await database.ref(`rooms/${roomId}/guest/answer`).set({
    type: answer.type,
    sdp: answer.sdp
  });

  // 监听Host的ICE候选
  database.ref(`rooms/${roomId}/host/ice`).on('child_added', async (snapshot) => {
    const candidate = snapshot.val();
    if (candidate) {
      await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
    }
  });

  updateStatus('已连接', 'connected');
  addChatMessage('系统', '已成功加入房间', 'system');
  showMediaControls();
}

// 设置数据通道
function setupDataChannel(channel) {
  channel.onopen = () => {
    addChatMessage('系统', '数据通道已建立', 'system');
    document.getElementById('messageInput').disabled = false;
    document.getElementById('sendMessageBtn').disabled = false;
  };

  channel.onmessage = (event) => {
    const data = JSON.parse(event.data);

    if (data.type === 'chat') {
      addChatMessage('对方', data.message, 'other');
    } else if (data.type === 'control') {
      handleControlEvent(data);
    }
  };

  channel.onclose = () => {
    addChatMessage('系统', '数据通道已关闭', 'system');
    document.getElementById('messageInput').disabled = true;
    document.getElementById('sendMessageBtn').disabled = true;
  };
}

// 显示媒体控制
function showMediaControls() {
  document.getElementById('videoSection').style.display = 'block';
  document.getElementById('controlSection').style.display = 'block';
}

// 开启视频
document.getElementById('startVideoBtn').addEventListener('click', async () => {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    addVideoStream('local', localStream);

    localStream.getTracks().forEach(track => {
      peerConnection.addTrack(track, localStream);
    });

    document.getElementById('stopMediaBtn').disabled = false;
    document.getElementById('muteBtn').disabled = false;
    addChatMessage('系统', '视频已开启', 'system');
  } catch (err) {
    alert('无法访问摄像头: ' + err.message);
  }
});

// 开启语音
document.getElementById('startAudioBtn').addEventListener('click', async () => {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true });

    localStream.getTracks().forEach(track => {
      peerConnection.addTrack(track, localStream);
    });

    document.getElementById('stopMediaBtn').disabled = false;
    document.getElementById('muteBtn').disabled = false;
    addChatMessage('系统', '语音已开启', 'system');
  } catch (err) {
    alert('无法访问麦克风: ' + err.message);
  }
});

// 共享屏幕
document.getElementById('shareScreenBtn').addEventListener('click', async () => {
  try {
    localStream = await navigator.mediaDevices.getDisplayMedia({ video: true });
    addVideoStream('local', localStream);

    localStream.getTracks().forEach(track => {
      peerConnection.addTrack(track, localStream);
    });

    document.getElementById('stopMediaBtn').disabled = false;
    addChatMessage('系统', '屏幕共享已开启', 'system');
  } catch (err) {
    alert('无法共享屏幕: ' + err.message);
  }
});

// 停止媒体
document.getElementById('stopMediaBtn').addEventListener('click', () => {
  if (localStream) {
    localStream.getTracks().forEach(track => track.stop());
    localStream = null;

    const localVideo = document.getElementById('video-local');
    if (localVideo) localVideo.remove();

    document.getElementById('stopMediaBtn').disabled = true;
    document.getElementById('muteBtn').disabled = true;
    addChatMessage('系统', '媒体已停止', 'system');
  }
});

// 静音
let isMuted = false;
document.getElementById('muteBtn').addEventListener('click', () => {
  if (localStream) {
    const audioTracks = localStream.getAudioTracks();
    audioTracks.forEach(track => {
      track.enabled = !track.enabled;
    });
    isMuted = !isMuted;
    document.getElementById('muteBtn').textContent = isMuted ? '🔊 取消静音' : '🔇 静音';
  }
});

// 添加视频流
function addVideoStream(id, stream) {
  const existingVideo = document.getElementById(`video-${id}`);
  if (existingVideo) existingVideo.remove();

  const videoWrapper = document.createElement('div');
  videoWrapper.className = 'video-wrapper';
  videoWrapper.id = `video-${id}`;

  const video = document.createElement('video');
  video.srcObject = stream;
  video.autoplay = true;
  video.playsInline = true;
  if (id === 'local') video.muted = true;

  const label = document.createElement('div');
  label.className = 'video-label';
  label.textContent = id === 'local' ? '我' : '对方';

  videoWrapper.appendChild(video);
  videoWrapper.appendChild(label);
  document.getElementById('videoGrid').appendChild(videoWrapper);
}

// 启用远程控制
document.getElementById('enableControlBtn').addEventListener('click', () => {
  controlEnabled = true;
  document.getElementById('controlCanvas').style.display = 'block';
  document.getElementById('enableControlBtn').disabled = true;
  document.getElementById('disableControlBtn').disabled = false;

  // 设置canvas
  const canvas = document.getElementById('controlCanvas');
  const remoteVideo = document.querySelector('#video-remote video');
  if (remoteVideo) {
    canvas.width = remoteVideo.videoWidth || 640;
    canvas.height = remoteVideo.videoHeight || 480;

    // 绘制视频帧到canvas
    const ctx = canvas.getContext('2d');
    function drawFrame() {
      if (controlEnabled && remoteVideo.readyState === remoteVideo.HAVE_ENOUGH_DATA) {
        ctx.drawImage(remoteVideo, 0, 0, canvas.width, canvas.height);
      }
      requestAnimationFrame(drawFrame);
    }
    drawFrame();
  }

  addChatMessage('系统', '远程控制已启用', 'system');
});

// 禁用远程控制
document.getElementById('disableControlBtn').addEventListener('click', () => {
  controlEnabled = false;
  document.getElementById('controlCanvas').style.display = 'none';
  document.getElementById('enableControlBtn').disabled = false;
  document.getElementById('disableControlBtn').disabled = true;
  addChatMessage('系统', '远程控制已禁用', 'system');
});

// 处理canvas点击
document.getElementById('controlCanvas').addEventListener('click', (e) => {
  if (!controlEnabled || !dataChannel || dataChannel.readyState !== 'open') return;

  const canvas = document.getElementById('controlCanvas');
  const rect = canvas.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width;
  const y = (e.clientY - rect.top) / rect.height;

  const controlData = {
    type: 'control',
    action: 'touch',
    x: x,
    y: y
  };

  dataChannel.send(JSON.stringify(controlData));
  addChatMessage('系统', `发送触摸事件: (${(x*100).toFixed(1)}%, ${(y*100).toFixed(1)}%)`, 'system');
});

// 处理控制事件
function handleControlEvent(data) {
  if (data.action === 'touch') {
    addChatMessage('系统', `收到触摸事件: (${(data.x*100).toFixed(1)}%, ${(data.y*100).toFixed(1)}%)`, 'system');
  }
}

// 发送消息
function sendMessage() {
  const input = document.getElementById('messageInput');
  const message = input.value.trim();

  if (message && dataChannel && dataChannel.readyState === 'open') {
    const data = {
      type: 'chat',
      message: message
    };
    dataChannel.send(JSON.stringify(data));
    addChatMessage('我', message, 'me');
    input.value = '';
  }
}

document.getElementById('sendMessageBtn').addEventListener('click', sendMessage);
document.getElementById('messageInput').addEventListener('keypress', (e) => {
  if (e.key === 'Enter') sendMessage();
});

// 检查URL参数
window.addEventListener('load', () => {
  const urlParams = new URLSearchParams(window.location.search);
  const urlRoomId = urlParams.get('room');

  if (urlRoomId) {
    document.getElementById('roomIdInput').value = urlRoomId;
    switchTab('join');
    addChatMessage('系统', `检测到房间ID: ${urlRoomId}`, 'system');
  }

  updateStatus('就绪', 'waiting');
});
