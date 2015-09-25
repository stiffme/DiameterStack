package com.esipeng.diameter;

public class AVP_OctetString
extends AVP
{
	public AVP_OctetString(AVP baseAVP)
	{
		super(baseAVP);
	}

	public AVP_OctetString(int code, byte[] value) { super(code, value); }

	public AVP_OctetString(int code, int vendor, byte[] value) {
		super(code, vendor, value);
	}

	public byte[] queryValue() { return queryPayload(); }

	public void setValue(byte[] value) {
		setPayload(value, 0, value.length);
	}
}
