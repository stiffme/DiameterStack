package com.esipeng.diameter;


public class AVP_Grouped
extends AVP
{
	public AVP_Grouped(AVP baseAVP)
			throws InvalidAVPLengthException
	{
		super(baseAVP);

		int pos = 0;
		byte[] bytes = queryPayload();
		//int j = 0;
		while (pos < bytes.length) { //check that all AVP data is enough
			int k = AVP.decodeSize(bytes, pos, bytes.length - pos);
			if (k == 0)
				throw new InvalidAVPLengthException(baseAVP);
			pos += k;
			//j++;
		}

		if (pos > bytes.length)
			throw new InvalidAVPLengthException(baseAVP);
	}

	public AVP_Grouped(int code, AVP... avps) { super(code, avps2byte(avps)); }

	public AVP_Grouped(int code, int vendor, AVP... avps) {
		super(code, vendor, avps2byte(avps));
	}

	public AVP[] queryAVPs() {
		int pos = 0;
		byte[] bytes = queryPayload();
		int noOfChildren = 0;
		while (pos < bytes.length) {
			int k = AVP.decodeSize(bytes, pos, bytes.length - pos);
			if (k == 0)
				return null;
			pos += k;
			noOfChildren++;
		}
		AVP[] avps = new AVP[noOfChildren];
		pos = 0;
		while (pos < bytes.length) {
			int m = AVP.decodeSize(bytes, pos, bytes.length - pos);
			avps[noOfChildren] = new AVP();
			avps[noOfChildren].decode(bytes, pos, m);
			pos += m;
		}
		return avps;
	}

	public void setAVPs(AVP... avps) {
		setPayload(avps2byte(avps));
	}

	private static final byte[] avps2byte(AVP[] avps) {
		int i = 0;
		for (AVP avp : avps) {
			i += avp.encodeSize();
		}
		byte[] buffer = new byte[i];
		int pos = 0;
		for (AVP localAVP : avps) {
			pos += localAVP.encode(buffer, pos);
		}
		return buffer;
	}
}
