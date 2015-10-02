package com.esipeng.diameter.node;

import java.nio.ByteBuffer;

class NormalConnectionBuffers extends ConnectionBuffers {
  private ByteBuffer in_buffer;
  private ByteBuffer out_buffer;
  
  NormalConnectionBuffers() { this.in_buffer = ByteBuffer.allocate(8192);
    this.out_buffer = ByteBuffer.allocate(8192);
  }
  
  ByteBuffer netOutBuffer() {
    return this.out_buffer;
  }
  
  ByteBuffer netInBuffer() { return this.in_buffer; }
  
  ByteBuffer appInBuffer() {
    return this.in_buffer;
  }
  
  ByteBuffer appOutBuffer() { return this.out_buffer; }
  

  void processNetInBuffer() {}
  
  void processAppOutBuffer() {}
  
  void makeSpaceInNetInBuffer()
  {
    this.in_buffer = makeSpaceInBuffer(this.in_buffer, 4096);
  }
  
  void makeSpaceInAppOutBuffer(int len) { this.out_buffer = makeSpaceInBuffer(this.out_buffer, len); }
}

