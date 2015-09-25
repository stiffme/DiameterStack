package com.esipeng.diameter.node;

import java.nio.ByteBuffer;

abstract class ConnectionBuffers { abstract ByteBuffer netOutBuffer();
  
  abstract ByteBuffer netInBuffer();
  
  abstract ByteBuffer appInBuffer();
  
  abstract ByteBuffer appOutBuffer();
  
  abstract void processNetInBuffer();
  
  abstract void processAppOutBuffer();
  
  abstract void makeSpaceInNetInBuffer();
  
  abstract void makeSpaceInAppOutBuffer(int paramInt);
  void consumeNetOutBuffer(int paramInt) { consume(netOutBuffer(), paramInt); }
  
  void consumeAppInBuffer(int paramInt) {
    consume(appInBuffer(), paramInt);
  }
  
  static ByteBuffer makeSpaceInBuffer(ByteBuffer paramByteBuffer, int paramInt)
  {
    if (paramByteBuffer.position() + paramInt > paramByteBuffer.capacity()) {
      int i = paramByteBuffer.position();
      int j = paramByteBuffer.capacity() + paramInt;
      j += 4096 - j % 4096;
      ByteBuffer localByteBuffer = ByteBuffer.allocate(j);
      paramByteBuffer.flip();
      localByteBuffer.put(paramByteBuffer);
      localByteBuffer.position(i);
      paramByteBuffer = localByteBuffer;
    }
    return paramByteBuffer;
  }
  
  private static void consume(ByteBuffer paramByteBuffer, int paramInt) { paramByteBuffer.limit(paramByteBuffer.position());
    paramByteBuffer.position(paramInt);
    paramByteBuffer.compact();
  }
}
