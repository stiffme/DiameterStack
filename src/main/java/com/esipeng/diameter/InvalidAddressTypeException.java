package com.esipeng.diameter;


public class InvalidAddressTypeException
extends Exception
{

	private static final long serialVersionUID = 1L;
	public AVP avp;

	public InvalidAddressTypeException(AVP paramAVP)
	{
		this.avp = new AVP(paramAVP);
	}
}
