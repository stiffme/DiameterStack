package com.esipeng.diameter;

public class AVP_Integer64
extends AVP
{
	public AVP_Integer64(AVP baseAVP) throws InvalidAVPLengthException
	{
		super(baseAVP);
		if (baseAVP.queryPayloadSize() != 8)
			throw new InvalidAVPLengthException(baseAVP);
	}

	public AVP_Integer64(int code, long value) { super(code, long2byte(value)); }

	public AVP_Integer64(int code, int vendor, long value) {
		super(code, vendor, long2byte(value));
	}

	public long queryValue() { return packunpack.unpack64(this.payload, 0); }


	public void setValue(long value) { packunpack.pack64(this.payload, 0, value); }

	private static final byte[] long2byte(long value) {
		byte[] bytes = new byte[8];
		packunpack.pack64(bytes, 0, value);
		return bytes;
	}
}
