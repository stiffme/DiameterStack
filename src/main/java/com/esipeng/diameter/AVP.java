package com.esipeng.diameter;




public class AVP
{
	byte[] payload;
	public int code;
	private int flags;
	public int vendor_id;
	private static final int avp_flag_vendor = 128;
	private static final int avp_flag_mandatory = 64;
	private static final int avp_flag_private = 32;
	public AVP() {}



	public AVP(AVP baseAVP)
	{
		this.payload = new byte[baseAVP.payload.length];
		System.arraycopy(baseAVP.payload, 0, this.payload, 0, baseAVP.payload.length);
		this.code = baseAVP.code;
		this.flags = baseAVP.flags;
		this.vendor_id = baseAVP.vendor_id;
	}




	public AVP(int code, byte[] payload)
	{
		this(code, 0, payload);
	}





	public AVP(int code, int value, byte[] payload)
	{
		this.code = code;
		this.vendor_id = value;
		this.payload = payload;
	}

	static final int decodeSize(byte[] bytes, int startPos, int length) {
		if (length < 8)
			return 0;
		/**
		 *  
	0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                           AVP Code                            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V M P r r r r r|                  AVP Length                   |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                        Vendor-ID (opt)                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |    Data ...
		 */
		int flagAndLength = packunpack.unpack32(bytes, startPos + 4);
		int flags = flagAndLength >> 24 & 0xFF;
		int avpLength = flagAndLength & 0xFFFFFF;
		int m = avpLength + 3 & 0xFFFFFFFC;
		if ((flags & avp_flag_vendor) != 0) { //vendor-specific?
			if (avpLength < 12) {
				return 0;
			}
		} else if (avpLength < 8) {
			return 0;
		}
		return m;
	}

	boolean decode(byte[] bytes, int startPos, int length) {
		if (length < 8) return false;
		int curPos = 0;
		this.code = packunpack.unpack32(bytes, startPos + curPos);
		curPos += 4;
		int flagsAndLength = packunpack.unpack32(bytes, startPos + curPos);
		curPos += 4;
		this.flags = (flagsAndLength >> 24 & 0xFF);
		int avpLength = flagsAndLength & 0xFFFFFF;
		int m = avpLength + 3 & 0xFFFFFFFC;
		if (length != m) return false;
		avpLength -= 8; //avp code and flagsAndLength
		if ((this.flags & avp_flag_vendor) != 0) { //vendor-specific?
			if (avpLength < 4) return false;
			this.vendor_id = packunpack.unpack32(bytes, startPos + curPos);
			curPos += 4;
			avpLength -= 4;
		} else {
			this.vendor_id = 0; 
		}
		setPayload(bytes, startPos + curPos, avpLength);
		curPos += avpLength;
		return true;
	}

	int encodeSize() {
		int i = 8;
		if (this.vendor_id != 0)
			i += 4;
		i += (this.payload.length + 3 & 0xFFFFFFFC);
		return i;
	}

	int encode(byte[] bytes, int startPos) {
		int i = 8;
		if (this.vendor_id != 0)
			i += 4;
		i += this.payload.length;

		int j = this.flags;
		if (this.vendor_id != 0) {
			j |= avp_flag_vendor;
		} else {
			j &= 0xFF7F;
		}
		int k = 0;

		packunpack.pack32(bytes, startPos + k, this.code);
		k += 4;
		packunpack.pack32(bytes, startPos + k, i | j << 24);
		k += 4;
		if (this.vendor_id != 0) {
			packunpack.pack32(bytes, startPos + k, this.vendor_id);
			k += 4;
		}

		System.arraycopy(this.payload, 0, bytes, startPos + k, this.payload.length);

		return encodeSize();
	}

	byte[] encode() { 
		int i = 8;
		if (this.vendor_id != 0)
			i += 4;
		i += this.payload.length;

		int j = this.flags;
		if (this.vendor_id != 0) {
			j |= avp_flag_vendor;
		} else {
			j &= 0xFF7F;
		}
		byte[] payload = new byte[encodeSize()];

		int k = 0;

		packunpack.pack32(payload, k, this.code);
		k += 4;
		packunpack.pack32(payload, k, i | j << 24);
		k += 4;
		if (this.vendor_id != 0) {
			packunpack.pack32(payload, k, this.vendor_id);
			k += 4;
		}

		System.arraycopy(this.payload, 0, payload, k, this.payload.length);

		return payload;
	}






	public byte[] queryPayload()
	{
		byte[] bytes = new byte[this.payload.length];
		System.arraycopy(this.payload, 0, bytes, 0, this.payload.length);
		return bytes;
	}

	int queryPayloadSize() { return this.payload.length; }

	void setPayload(byte[] bytes) {
		setPayload(bytes, 0, bytes.length);
	}

	void setPayload(byte[] bytes, int startPos, int length) {
		byte[] arrayOfByte = new byte[length];
		System.arraycopy(bytes, startPos, arrayOfByte, 0, length);
		this.payload = arrayOfByte;
	}


	public boolean isVendorSpecific() { return this.vendor_id != 0; }

	public boolean isMandatory() { return (this.flags & avp_flag_mandatory) != 0; }

	public boolean isPrivate() { return (this.flags & avp_flag_private) != 0; }

	public void setMandatory(boolean m) {
		if (m) 
			this.flags |= avp_flag_mandatory; 
		else
			this.flags &= 0xFFFFFFBF;
	}

	public void setPrivate(boolean p) {
		if (p) 
			this.flags |= avp_flag_private; 
		else {
			this.flags &= 0xFFFFFFDF;
		}
	}


	public AVP setM()
	{
		this.flags |= avp_flag_mandatory;
		return this;
	}



	void inline_shallow_replace(AVP paramAVP)
	{
		this.payload = paramAVP.payload;
		this.code = paramAVP.code;
		this.flags = paramAVP.flags;
		this.vendor_id = paramAVP.vendor_id;
	}
}
