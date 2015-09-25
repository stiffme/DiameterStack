package com.esipeng.diameter.node;

import java.util.Random;

class NodeState {
  private final int state_id;
  private int end_to_end_identifier;
  private int session_id_high;
  private long session_id_low;
  
  NodeState() { int i = (int)(System.currentTimeMillis() / 1000L);
    this.state_id = i;
    this.end_to_end_identifier = (i << 20 | new Random().nextInt() & 0xFFFFF);
    this.session_id_high = i;
    this.session_id_low = 0L;
  }
  
  public int stateId() {
    return this.state_id;
  }
  
  public synchronized int nextEndToEndIdentifier() {
    int i = this.end_to_end_identifier;
    this.end_to_end_identifier += 1;
    return i;
  }
  
  synchronized String nextSessionId_second_part() {
    long l = this.session_id_low;
    int i = this.session_id_high;
    this.session_id_low += 1L;
    if (this.session_id_low == 4294967296L) {
      this.session_id_low = 0L;
      this.session_id_high += 1;
    }
    return i + ";" + l;
  }
}

