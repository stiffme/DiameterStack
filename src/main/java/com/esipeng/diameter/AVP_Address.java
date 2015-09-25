package com.esipeng.diameter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;


public class AVP_Address
extends AVP_OctetString
{
	private static final int IANA_IP_V4 = 1;
	private static final int IANA_IP_V6 = 2;
	public AVP_Address(AVP baseAVP)
			throws InvalidAVPLengthException, InvalidAddressTypeException
	{
		super(baseAVP);
		if (baseAVP.queryPayloadSize() < 2)
			throw new InvalidAVPLengthException(baseAVP);
		int i = packunpack.unpack16(this.payload, 0);
		if (i == 1) {
			if (baseAVP.queryPayloadSize() != 6)
				throw new InvalidAVPLengthException(baseAVP);
		} else if (i == 2) {
			if (baseAVP.queryPayloadSize() != 18)
				throw new InvalidAVPLengthException(baseAVP);
		} else
			throw new InvalidAddressTypeException(baseAVP);
	}

	public AVP_Address(int code, InetAddress address) { super(code, InetAddress2byte(address)); }

	public AVP_Address(int code, int vendor, InetAddress address) {
		super(code, vendor, InetAddress2byte(address));
	}

	public InetAddress queryAddress() throws InvalidAVPLengthException, InvalidAddressTypeException {
		if (queryPayloadSize() < 2)
			throw new InvalidAVPLengthException(this);
		byte[] addressTypeBytes = queryValue();
		int addressType = packunpack.unpack16(addressTypeBytes, 0);
		byte[] addressBytes;
		try {    	
			switch (addressType) {
			case IANA_IP_V4: 
				if (queryPayloadSize() != 6)
					throw new InvalidAVPLengthException(this);
				addressBytes = new byte[4];
				System.arraycopy(addressTypeBytes, 2, addressBytes, 0, 4);
				return InetAddress.getByAddress(addressBytes);

			case IANA_IP_V6: 
				if (queryPayloadSize() != 18)
					throw new InvalidAVPLengthException(this);
				addressBytes = new byte[16];
				System.arraycopy(addressTypeBytes, 2, addressBytes, 0, 16);
				return InetAddress.getByAddress(addressBytes);
			default:
				throw new InvalidAddressTypeException(this);
			}

		}
		catch (UnknownHostException exception) {}

		return null;
	}

	public void setAddress(InetAddress inetAddress) {
		setValue(InetAddress2byte(inetAddress));
	}

	private static final byte[] InetAddress2byte(InetAddress inetAddress) {
		byte[] addressBytes = inetAddress.getAddress();
		int addressType;
		if(inetAddress instanceof Inet4Address) 
			addressType = IANA_IP_V4;
		else  
			addressType = IANA_IP_V6;

		byte[] totalBytes = new byte[2 + addressBytes.length];
		packunpack.pack16(totalBytes, 0, addressType);
		System.arraycopy(addressBytes, 0, totalBytes, 2, addressBytes.length);
		return totalBytes;
	}
}
