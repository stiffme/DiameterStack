package com.esipeng.diameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AVP_Float32 extends AVP
{
	public AVP_Float32(AVP baseAVP) throws InvalidAVPLengthException
	{
		super(baseAVP);
		if (baseAVP.queryPayloadSize() != 4)
			throw new InvalidAVPLengthException(baseAVP);
	}

	public AVP_Float32(int code, float value) {
		super(code, float2byte(value));
	}

	public AVP_Float32(int code, int vendor, float value) { super(code, vendor, float2byte(value)); }

	public void setValue(float value)
	{
		setPayload(float2byte(value));
	}

	public float queryValue() {
		byte[] valueBytes = queryPayload();
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.put(valueBytes);
		buffer.rewind();
		return buffer.getFloat();
	}

	private static final byte[] float2byte(float value) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putFloat(value);
		buffer.rewind();
		byte[] bytes = new byte[4];
		buffer.get(bytes);
		return bytes;
	}
}
