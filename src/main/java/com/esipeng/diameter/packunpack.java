package com.esipeng.diameter;


class packunpack {
	public static final void pack8(byte[] bytes, int startPos, byte value) { 
		bytes[startPos] = value; 
	}

	public static final void pack16(byte[] bytes, int startPos, int value) {
		bytes[(startPos + 0)] = ((byte)(value >> 8 & 0xFF));
		bytes[(startPos + 1)] = ((byte)(value & 0xFF));
	}

	public static final void pack32(byte[] bytes, int startPos, int value) { 
		bytes[(startPos + 0)] = ((byte)(value >> 24 & 0xFF));
		bytes[(startPos + 1)] = ((byte)(value >> 16 & 0xFF));
		bytes[(startPos + 2)] = ((byte)(value >> 8 & 0xFF));
		bytes[(startPos + 3)] = ((byte)(value & 0xFF));
	}

	public static final void pack64(byte[] bytes, int startPos, long value) { 
		bytes[(startPos + 0)] = ((byte)(int)(value >> 56 & 0xFF));
		bytes[(startPos + 1)] = ((byte)(int)(value >> 48 & 0xFF));
		bytes[(startPos + 2)] = ((byte)(int)(value >> 40 & 0xFF));
		bytes[(startPos + 3)] = ((byte)(int)(value >> 32 & 0xFF));
		bytes[(startPos + 4)] = ((byte)(int)(value >> 24 & 0xFF));
		bytes[(startPos + 5)] = ((byte)(int)(value >> 16 & 0xFF));
		bytes[(startPos + 6)] = ((byte)(int)(value >> 8 & 0xFF));
		bytes[(startPos + 7)] = ((byte)(int)(value & 0xFF));
	}

	public static final byte unpack8(byte[] bytes, int startPos) {
		return bytes[startPos];
	}

	public static final int unpack32(byte[] bytes, int startPos) { 
		return (bytes[(startPos + 0)] & 0xFF) << 24 | (bytes[(startPos + 1)] & 0xFF) << 16 | (bytes[(startPos + 2)] & 0xFF) << 8 | bytes[(startPos + 3)] & 0xFF; 
	}




	public static final int unpack16(byte[] bytes, int startPos)
	{
		return (bytes[(startPos + 0)] & 0xFF) << 8 | bytes[(startPos + 1)] & 0xFF;
	}

	public static final long unpack64(byte[] bytes, int startPos)
	{
		return (bytes[(startPos + 0)] & 0xFF) << 56 | (bytes[(startPos + 1)] & 0xFF) << 48 | (bytes[(startPos + 2)] & 0xFF) << 40 | (bytes[(startPos + 3)] & 0xFF) << 32 | (bytes[(startPos + 4)] & 0xFF) << 24 | (bytes[(startPos + 5)] & 0xFF) << 16 | (bytes[(startPos + 6)] & 0xFF) << 8 | bytes[(startPos + 7)] & 0xFF;
	}
}
