package com.esipeng.diameter;


public class AVP_Unsigned32
extends AVP
{
	public AVP_Unsigned32(AVP baseAVP)
			throws InvalidAVPLengthException
	{
		super(baseAVP);
		if (baseAVP.queryPayloadSize() != 4)
			throw new InvalidAVPLengthException(baseAVP);
	}

	public AVP_Unsigned32(int code, int value) { super(code, int2byte(value)); }

	public AVP_Unsigned32(int code, int vendor, int value) {
		super(code, vendor, int2byte(value));
	}

	public int queryValue() { return packunpack.unpack32(this.payload, 0); }

	public void setValue(int value) {
		packunpack.pack32(this.payload, 0, value);
	}

	private static final byte[] int2byte(int value) {
		byte[] bytes = new byte[4];
		packunpack.pack32(bytes, 0, value);
		return bytes;
	}
}
