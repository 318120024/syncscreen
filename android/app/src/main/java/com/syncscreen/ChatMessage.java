package com.syncscreen;

public class ChatMessage {
    public static final int TYPE_ME = 0;
    public static final int TYPE_OTHER = 1;
    public static final int TYPE_SYSTEM = 2;

    private String sender;
    private String message;
    private int type;
    private long timestamp;

    public ChatMessage(String sender, String message, int type) {
        this.sender = sender;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
