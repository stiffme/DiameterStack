package com.esipeng.diameter;

import java.util.Date;

public class AVP_Time
extends AVP_Unsigned32
{
	private static final int seconds_between_1900_and_1970 = -2085978496;

	public AVP_Time(AVP baseAVP)
			throws InvalidAVPLengthException
	{
		super(baseAVP);
	}

	public AVP_Time(int code, Date value) { this(code, 0, value); }

	public AVP_Time(int code, int vendor, Date value) {
		super(code, vendor, (int)(value.getTime() / 1000L + seconds_between_1900_and_1970));
	}

	public AVP_Time(int code, int value) { this(code, 0, value); }

	public AVP_Time(int code, int vendor, int value) {
		super(code, vendor, value + seconds_between_1900_and_1970);
	}

	public Date queryDate() { return new Date((super.queryValue() - seconds_between_1900_and_1970) * 1000L); }

	public int querySecondsSince1970() {
		return super.queryValue() - seconds_between_1900_and_1970;
	}

	public void setValue(Date value) { super.setValue((int)(value.getTime() / 1000L + seconds_between_1900_and_1970)); }
}
