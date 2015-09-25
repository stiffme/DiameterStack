package com.esipeng.diameter;



public class MessageHeader
{
	byte version;


	private byte command_flags;


	public int command_code;


	public int application_id;


	public int hop_by_hop_identifier;

	public int end_to_end_identifier;

	public static final byte command_flag_request_bit = -128;

	public static final byte command_flag_proxiable_bit = 64;

	public static final byte command_flag_error_bit = 32;

	public static final byte command_flag_retransmit_bit = 16;


	public boolean isRequest()
	{
		return (this.command_flags & command_flag_request_bit) != 0;
	}

	public boolean isProxiable() { 
		return (this.command_flags & command_flag_proxiable_bit) != 0; 
	}

	public boolean isError() {
		return (this.command_flags & command_flag_error_bit) != 0;
	}

	public boolean isRetransmit() { 
		return (this.command_flags & command_flag_retransmit_bit) != 0; 
	}

	public void setRequest(boolean isReq) {
		if (isReq) {
			this.command_flags = ((byte)(this.command_flags | command_flag_request_bit));
		} else
			this.command_flags = ((byte)(this.command_flags & ~command_flag_request_bit));
	}

	public void setProxiable(boolean isProx) { 
		if (isProx) {
			this.command_flags = ((byte)(this.command_flags | command_flag_proxiable_bit));
		} else
			this.command_flags = ((byte)(this.command_flags & ~command_flag_proxiable_bit));
	}

	public void setError(boolean isErr) {
		if (isErr) {
			this.command_flags = ((byte)(this.command_flags | command_flag_error_bit));
		} else
			this.command_flags = ((byte)(this.command_flags & ~command_flag_error_bit));
	}

	public void setRetransmit(boolean isRetran) {
		if (isRetran) {
			this.command_flags = ((byte)(this.command_flags | command_flag_retransmit_bit));
		} else {
			this.command_flags = ((byte)(this.command_flags & ~command_flag_retransmit_bit));
		}
	}

	public MessageHeader()
	{
		this.version = 1;
	}




	public MessageHeader(MessageHeader header)
	{
		this.version = header.version;
		this.command_flags = header.command_flags;
		this.command_code = header.command_code;
		this.application_id = header.application_id;
		this.hop_by_hop_identifier = header.hop_by_hop_identifier;
		this.end_to_end_identifier = header.end_to_end_identifier;
	}


	int encodeSize() { return 20; }

	int encode(byte[] bytes, int startPos, int length) {
		packunpack.pack32(bytes, startPos + 0, length);
		packunpack.pack8(bytes, startPos + 0, this.version);
		packunpack.pack32(bytes, startPos + 4, this.command_code);
		packunpack.pack8(bytes, startPos + 4, this.command_flags);
		packunpack.pack32(bytes, startPos + 8, this.application_id);
		packunpack.pack32(bytes, startPos + 12, this.hop_by_hop_identifier);
		packunpack.pack32(bytes, startPos + 16, this.end_to_end_identifier);
		return 20;
	}

	void decode(byte[] bytes, int startPos) {
		this.version = packunpack.unpack8(bytes, startPos + 0);

		this.command_flags = packunpack.unpack8(bytes, startPos + 4);
		this.command_code = (packunpack.unpack32(bytes, startPos + 4) & 0xFFFFFF);
		this.application_id = packunpack.unpack32(bytes, startPos + 8);
		this.hop_by_hop_identifier = packunpack.unpack32(bytes, startPos + 12);
		this.end_to_end_identifier = packunpack.unpack32(bytes, startPos + 16);
	}







	public void prepareResponse(MessageHeader header)
	{
		this.command_flags = ((byte)(header.command_flags & 0x40));
		this.command_code = header.command_code;
		this.application_id = header.application_id;
		this.hop_by_hop_identifier = header.hop_by_hop_identifier;
		this.end_to_end_identifier = header.end_to_end_identifier;
	}





	public void prepareAnswer(MessageHeader header)
	{
		prepareResponse(header);
	}
}
