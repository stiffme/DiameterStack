package com.esipeng.diameter.node;

import java.nio.ByteBuffer;

abstract class ConnectionBuffers {

    abstract ByteBuffer netOutBuffer();

    abstract ByteBuffer netInBuffer();

    abstract ByteBuffer appInBuffer();

    abstract ByteBuffer appOutBuffer();

    abstract void processNetInBuffer();

    abstract void processAppOutBuffer();

    abstract void makeSpaceInNetInBuffer();

    abstract void makeSpaceInAppOutBuffer(int len);

    void consumeNetOutBuffer(int len) {
        consume(netOutBuffer(), len);
    }

    void consumeAppInBuffer(int len) {
        consume(appInBuffer(), len);
    }

    static ByteBuffer makeSpaceInBuffer(ByteBuffer buffer, int len) {
        if (buffer.position() + len > buffer.capacity()) {
            int i = buffer.position();
            int j = buffer.capacity() + len;
            j += 4096 - j % 4096;
            ByteBuffer localByteBuffer = ByteBuffer.allocate(j);
            buffer.flip();
            localByteBuffer.put(buffer);
            localByteBuffer.position(i);
            buffer = localByteBuffer;
        }
        return buffer;
    }

    private static void consume(ByteBuffer buffer, int len) {
        buffer.limit(buffer.position());
        buffer.position(len);
        buffer.compact();
    }
}
