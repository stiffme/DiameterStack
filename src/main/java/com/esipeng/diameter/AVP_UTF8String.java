package com.esipeng.diameter;

import java.io.UnsupportedEncodingException;

public class AVP_UTF8String extends AVP
{
	public AVP_UTF8String(AVP baseAVP)
	{
		super(baseAVP);
	}

	public AVP_UTF8String(int code, String value) { super(code, string2byte(value)); }


	public AVP_UTF8String(int code, int vendor, String value) { super(code, vendor, string2byte(value)); }

	public String queryValue() {
		try {
			return new String(queryPayload(), "UTF-8");
		} catch (UnsupportedEncodingException exception) {}
		return null;
	}

	public void setValue(String value) {
		setPayload(string2byte(value));
	}

	private static final byte[] string2byte(String value) {
		try {
			return value.getBytes("UTF-8");
		}
		catch (UnsupportedEncodingException exception) {}

		return null;
	}
}
