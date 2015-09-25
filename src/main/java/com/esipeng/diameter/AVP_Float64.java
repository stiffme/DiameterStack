package com.esipeng.diameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AVP_Float64 extends AVP
{
  public AVP_Float64(AVP baseAVP) throws InvalidAVPLengthException
  {
    super(baseAVP);
    if (baseAVP.queryPayloadSize() != 4)
      throw new InvalidAVPLengthException(baseAVP);
  }
  
  public AVP_Float64(int code, double value) {
    super(code, double2byte(value));
  }
  
  public AVP_Float64(int code, int vendor, double value) { super(code, vendor, double2byte(value)); }
  
  public void setValue(double value)
  {
    setPayload(double2byte(value));
  }
  
  public double queryValue() {
    byte[] bytes = queryPayload();
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.order(ByteOrder.BIG_ENDIAN);
    buffer.put(bytes);
    buffer.rewind();
    return buffer.getDouble();
  }
  
  private static final byte[] double2byte(double value) {
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.order(ByteOrder.BIG_ENDIAN);
    buffer.putDouble(value);
    buffer.rewind();
    byte[] bytes = new byte[4];
    buffer.get(bytes);
    return bytes;
  }
}
