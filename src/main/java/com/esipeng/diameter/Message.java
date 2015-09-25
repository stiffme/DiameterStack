package com.esipeng.diameter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;


public class Message
{
	public MessageHeader hdr;
	private ArrayList<AVP> avp;

	public Message()
	{
		this.hdr = new MessageHeader();
		this.avp = new ArrayList<AVP>();
	}



	public Message(MessageHeader header)
	{
		this.hdr = new MessageHeader(header);
		this.avp = new ArrayList<AVP>();
	}



	public Message(Message message)
	{
		this(message.hdr);
		for (AVP avp : message.avp) {
			this.avp.add(new AVP(avp));
		}
	}



	public int encodeSize()
	{
		int i = 0;
		i += this.hdr.encodeSize();
		for (AVP avp : this.avp) {
			i += avp.encodeSize();
		}
		return i;
	}



	public void encode(byte[] bytes)
	{
		int i = encodeSize();
		int j = 0;
		j += this.hdr.encode(bytes, j, i);
		for (AVP avp : this.avp) {
			j += avp.encode(bytes, j);
		}
	}



	public byte[] encode()
	{
		int i = encodeSize();
		byte[] bytes = new byte[i];
		int j = 0;
		j += this.hdr.encode(bytes, j, i);
		for (AVP avp : this.avp) {
			j += avp.encode(bytes, j);
		}
		return bytes;
	}







	public static int decodeSize(byte[] bytes, int pos)
	{
		int i = packunpack.unpack32(bytes, pos);
		int j = i >> 24 & 0xFF;
		int k = i & 0xFFFFFF;
		if ((j != 1) || (k < 20) || (k % 4 != 0))
			return 4;
		return k;
	}


	public static enum decode_status
	{
		decoded, 

		not_enough, 

		garbage;



		private decode_status() {}
	}


	public decode_status decode(byte[] bytes)
	{
		return decode(bytes, 0, bytes.length);
	}

	public decode_status decode(byte[] bytes, int startPos, int length)
	{
		if (length < 1)
			return decode_status.not_enough;
		if (packunpack.unpack8(bytes, startPos) != 1)
			return decode_status.garbage;
		if (length < 4)
			return decode_status.not_enough;
		int i = decodeSize(bytes, startPos);
		if ((i & 0x3) != 0)
			return decode_status.garbage;
		if (i < 20)
			return decode_status.garbage;
		if (length < 20)
			return decode_status.not_enough;
		if (i == -1)
			return decode_status.garbage;
		if (length < i) {
			return decode_status.not_enough;
		}
		this.hdr.decode(bytes, startPos);
		if (this.hdr.version != 1)
			return decode_status.garbage;
		startPos += 20;
		int j = length - 20;
		int k = j / 16;
		ArrayList<AVP> avps = new ArrayList<AVP>(k);
		while (j > 0) {
			if (j < 8)
				return decode_status.garbage;
			int m = AVP.decodeSize(bytes, startPos, j);
			if (m == 0)
				return decode_status.garbage;
			if (m > j) {
				return decode_status.garbage;
			}
			AVP localAVP = new AVP();
			if (!localAVP.decode(bytes, startPos, m))
				return decode_status.garbage;
			avps.add(localAVP);
			startPos += m;
			j -= m;
		}
		if (j != 0) {
			return decode_status.garbage;
		}
		this.avp = avps;
		return decode_status.decoded;
	}


	public int size() { return this.avp.size(); }

	public void ensureCapacity(int capability) { this.avp.ensureCapacity(capability); }

	public AVP get(int code) { return new AVP(this.avp.get(code)); }

	public void clear() { this.avp.clear(); }

	public void add(AVP avp) { this.avp.add(avp); }

	public void add(int index, AVP avp) { this.avp.add(index, avp); }

	public void remove(int index) { this.avp.remove(index); }

	private class AVPIterator implements Iterator<AVP> {
		private ListIterator<AVP> i;
		private int code;
		private int vendor_id;

		AVPIterator(ListIterator<AVP> it,int code, int vendor) {

			this.i = it;
			this.code = code;
			this.vendor_id = vendor;
		}

		public void remove() { this.i.remove(); }

		public boolean hasNext() {
			while (this.i.hasNext()) {
				AVP avp = this.i.next();
				if ((avp.code == this.code) && ((this.vendor_id == 0) || (avp.vendor_id == this.vendor_id)))
				{

					this.i.previous();
					return true;
				}
			}
			return false;
		}

		public AVP next() { return this.i.next(); }
	}



	public Iterable<AVP> avps() { return this.avp; }

	public Iterator<AVP> iterator() { return this.avp.iterator(); }

	public Iterator<AVP> iterator(int code) { return iterator(code, 0); }

	public Iterator<AVP> iterator(int code, int vendor) {

		return new AVPIterator(this.avp.listIterator(), code, vendor);
	}





	public void prepareResponse(Message requestMessage)
	{
		this.hdr.prepareResponse(requestMessage.hdr);
	}







	public void prepareAnswer(Message requestMessage) { prepareResponse(requestMessage); }

	private class Subset implements Iterable<AVP> {
		Message msg;
		int code;
		int vendor_id;

		Subset(Message message, int code, int vendor) {
			this.msg = message;
			this.code = code;
			this.vendor_id = vendor;
		}

		public Iterator<AVP> iterator() { return this.msg.iterator(this.code, this.vendor_id); }
	}





	public Iterable<AVP> subset(int code)
	{
		return subset(code, 0);
	}


	public Iterable<AVP> subset(int code, int vendor)
	{
		return new Subset(this, code, vendor);
	}




	public AVP find(int code)
	{
		return find(code, 0);
	}


	public AVP find(int code, int vendor)
	{
		for (AVP avp : this.avp) {
			if ((avp.code == code) && (avp.vendor_id == vendor))
				return avp;
		}
		return null;
	}

	int find_first(int code) {
		int i = 0;
		for (AVP avp : this.avp) {
			if (avp.code == code)
				return i;
			i++;
		}
		return -1;
	}

	int count(int code) { 
		int i = 0;
		for (AVP avp : this.avp) {
			if (avp.code == code)
				i++;
		}
		return i;
	}
}
