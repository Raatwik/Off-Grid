package chat;

import java.nio.ByteBuffer;

/**
 * ChatFrame
 * -------------------------------------------------------------------------
 * The per-chunk wire frame that travels over {@link ByteTransport} (Phase
 * 3+4 transport-crypto-layer, Issue 01 — chat-engine-core):
 *
 * <pre>
 *   msgId        4 bytes   random, groups all chunks of one logical message
 *   chunkIndex   1 byte    0-based index of this chunk (unsigned)
 *   totalChunks  1 byte    total chunks in this message (unsigned)
 *   nonce        12 bytes  GCM nonce for this chunk only
 *   ciphertext   <=216 bytes  encrypted chunk payload, includes the 16-byte GCM tag
 * </pre>
 *
 * Total frame size is at or under ~234 bytes, inside ESP-NOW's ~250-byte
 * payload ceiling. {@code chunkIndex}/{@code totalChunks} are stored as
 * signed Java bytes but always treated as unsigned (0-255) values.
 */
record ChatFrame(byte[] msgId, int chunkIndex, int totalChunks, byte[] nonce, byte[] ciphertext) {

    static final int MSG_ID_LENGTH = 4;
    static final int NONCE_LENGTH = 12;
    static final int HEADER_LENGTH = MSG_ID_LENGTH + 1 + 1 + NONCE_LENGTH;
    static final int MAX_CIPHERTEXT_LENGTH = 216;
    static final int MAX_FRAME_LENGTH = HEADER_LENGTH + MAX_CIPHERTEXT_LENGTH;

    ChatFrame {
        if (msgId.length != MSG_ID_LENGTH) {
            throw new IllegalArgumentException("msgId must be " + MSG_ID_LENGTH + " bytes");
        }
        if (nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("nonce must be " + NONCE_LENGTH + " bytes");
        }
        if (chunkIndex < 0 || chunkIndex > 255) {
            throw new IllegalArgumentException("chunkIndex must fit in an unsigned byte");
        }
        if (totalChunks < 1 || totalChunks > 255) {
            throw new IllegalArgumentException("totalChunks must be between 1 and 255");
        }
        if (ciphertext.length > MAX_CIPHERTEXT_LENGTH) {
            throw new IllegalArgumentException("ciphertext exceeds " + MAX_CIPHERTEXT_LENGTH + " bytes");
        }
    }

    /** Groups chunks belonging to the same message, as a lookup key. */
    int msgIdKey() {
        return ByteBuffer.wrap(msgId).getInt();
    }

    byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + ciphertext.length);
        buffer.put(msgId);
        buffer.put((byte) chunkIndex);
        buffer.put((byte) totalChunks);
        buffer.put(nonce);
        buffer.put(ciphertext);
        return buffer.array();
    }

    static ChatFrame decode(byte[] frame) {
        if (frame.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("frame too short: " + frame.length + " bytes");
        }
        if (frame.length > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("frame too long: " + frame.length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        byte[] msgId = new byte[MSG_ID_LENGTH];
        buffer.get(msgId);
        int chunkIndex = buffer.get() & 0xFF;
        int totalChunks = buffer.get() & 0xFF;
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        return new ChatFrame(msgId, chunkIndex, totalChunks, nonce, ciphertext);
    }
}
