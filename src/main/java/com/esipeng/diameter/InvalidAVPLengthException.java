package com.esipeng.diameter;


public class InvalidAVPLengthException
extends Exception
{
	private static final long serialVersionUID = 1L;
	public AVP avp;

	public InvalidAVPLengthException(AVP paramAVP)
	{
		this.avp = new AVP(paramAVP);
	}
}
