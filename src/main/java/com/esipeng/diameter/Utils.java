package com.esipeng.diameter;

import java.util.Collection;
import java.util.Iterator;


public final class Utils
{
	private static final boolean contains(int[] intSet, int key)
	{
		for (int k : intSet)
			if (k == key)
				return true;
		return false; 
	}

	private static final int[] empty_array = new int[0];

	private static final boolean setMandatory(AVP avp, int[] codes, int[] grouped_codes) {
		boolean ret = false;
		if ((avp.vendor_id == 0) && (contains(grouped_codes, avp.code))) {
			try {
				AVP_Grouped grouped_avp = new AVP_Grouped(avp);
				AVP[] children = grouped_avp.queryAVPs();
				for (AVP child : children)
					ret = (setMandatory(child, codes, grouped_codes)) || (ret);
				int i = 0;
				for (AVP child : children)
					i = (i != 0) || (child.isMandatory()) ? 1 : 0;
				if ((i != 0) && (!avp.isMandatory())) {
					grouped_avp.setMandatory(true);
					ret = true;
				}
				if (ret) {
					grouped_avp.setAVPs(children);
					avp.inline_shallow_replace(grouped_avp);
				}
			}
			catch (InvalidAVPLengthException exception) {}
		}

		if ((!avp.isMandatory()) && 
				(avp.vendor_id == 0) && (contains(codes, avp.code))) {
			avp.setMandatory(true);
			ret = true;
		}

		return ret;
	}









	public static final void setMandatory(Iterable<AVP> iterator, int[] codes, int[] grouped_codes)
	{
		for (AVP avp : iterator) {
			setMandatory(avp, codes, grouped_codes);
		}
	}






	public static final void setMandatory(Iterable<AVP> iterator, int[] codes)
	{
		setMandatory(iterator, codes, empty_array);
	}






	public static final void setMandatory(Iterable<AVP> iterator, Collection<Integer> codes)
	{
		for (AVP avp : iterator) {
			if ((codes.contains(Integer.valueOf(avp.code))) && (avp.vendor_id == 0)) {
				avp.setMandatory(true);
			}
		}
	}




	public static final void setMandatory(Message messsage, int[] codes)
	{
		setMandatory(messsage.avps(), codes, empty_array);
	}






	public static final void setMandatory(Message message, int[] codes, int[] grouped_codes)
	{
		setMandatory(message.avps(), codes, grouped_codes);
	}


	public static final int[] rfc3588_mandatory_codes = { 483, 485, 480, 44, 287, 259, 85, 50, 291, 258, 276, 274, 277, 25, 293, 283, 273, 300, 55, 297, 298, 279, 257, 299, 272, 264, 296, 278, 280, 284, 33, 292, 261, 262, 268, 285, 282, 270, 263, 271, 27, 265, 295, 1, 266, 260 };

	public static final int[] rfc3588_grouped_avps = { 300, 297, 279, 284, 260 };



	public static final void setMandatory_RFC3588(Iterable<AVP> iterator)
	{
		setMandatory(iterator, rfc3588_mandatory_codes, rfc3588_grouped_avps);
	}


	public static final void setMandatory_RFC3588(Message message)
	{
		setMandatory(message.avps(), rfc3588_mandatory_codes, rfc3588_grouped_avps);
	}


	public static final int[] rfc4006_mandatory_codes = { 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 454, 422, 423, 424, 426, 427, 425, 428, 429, 449, 430, 431, 453, 457, 456, 455, 432, 433, 434, 435, 436, 437, 438, 461, 439, 443, 444, 450, 452, 451, 445, 446, 447, 448 };

	public static final int[] rfc4006_grouped_avps = { 413, 423, 430, 431, 457, 456, 434, 437, 440, 443, 445, 446, 458 };


	public static final void setMandatory_RFC4006(Iterable<AVP> iterator)
	{
		setMandatory(iterator, rfc4006_mandatory_codes, rfc4006_grouped_avps);
	}



	public static final void setMandatory_RFC4006(Message message)
	{
		setMandatory(message.avps(), rfc4006_mandatory_codes, rfc4006_grouped_avps);
	}





	public static final void copyProxyInfo(Message message, Message message2)
	{
		for (AVP avp : message.subset(284)) {
			message2.add(new AVP(avp));
		}
	}


	public static class ABNFComponent
	{
		public boolean fixed_position;

		public int min_count;

		public int max_count;

		public int code;


		public ABNFComponent(boolean paramBoolean, int paramInt1, int paramInt2, int paramInt3)
		{
			this.fixed_position = paramBoolean;
			this.min_count = paramInt1;
			this.max_count = paramInt2;
			this.code = paramInt3;
		}
	}


	public static final ABNFComponent[] abnf_cer = { new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, -1, 257), new ABNFComponent(false, 1, 1, 266), new ABNFComponent(false, 1, 1, 269), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, -1, 265), new ABNFComponent(false, 0, -1, 258), new ABNFComponent(false, 0, -1, 299), new ABNFComponent(false, 0, -1, 259), new ABNFComponent(false, 0, -1, 260), new ABNFComponent(false, 0, 1, 267), new ABNFComponent(false, 0, -1, -1) };















	public static final ABNFComponent[] abnf_cea = { new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, -1, 257), new ABNFComponent(false, 1, 1, 266), new ABNFComponent(false, 1, 1, 269), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, 1, 281), new ABNFComponent(false, 0, 1, 279), new ABNFComponent(false, 0, -1, 265), new ABNFComponent(false, 0, -1, 258), new ABNFComponent(false, 0, -1, 299), new ABNFComponent(false, 0, -1, 259), new ABNFComponent(false, 0, -1, 260), new ABNFComponent(false, 0, 1, 267), new ABNFComponent(false, 0, -1, -1) };


















	public static final ABNFComponent[] abnf_dpr = { new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, 1, 273) };





	public static final ABNFComponent[] abnf_dpa = { new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, 1, 281), new ABNFComponent(false, 0, 1, 279) };







	public static final ABNFComponent[] abnf_dwr = { new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, 1, 278) };





	public static final ABNFComponent[] abnf_dwa = { new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, 1, 281), new ABNFComponent(false, 0, 1, 279), new ABNFComponent(false, 0, 1, 278) };









	public static final ABNFComponent[] abnf_rar = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, 1, 283), new ABNFComponent(false, 1, 1, 293), new ABNFComponent(false, 0, 1, 258), new ABNFComponent(false, 0, 1, 285), new ABNFComponent(false, 0, -1, -1) };










	public static final ABNFComponent[] abnf_raa = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, -1, -1) };








	public static final ABNFComponent[] abnf_str = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, 1, 283), new ABNFComponent(false, 1, 1, 258), new ABNFComponent(false, 1, 1, 295), new ABNFComponent(false, 0, 1, 1), new ABNFComponent(false, 0, 1, 293), new ABNFComponent(false, 0, -1, 25), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, -1, 284), new ABNFComponent(false, 0, -1, 282), new ABNFComponent(false, 0, -1, -1) };















	public static final ABNFComponent[] abnf_sta = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, 1, 1), new ABNFComponent(false, 0, -1, 25), new ABNFComponent(false, 0, 1, 281), new ABNFComponent(false, 0, 1, 294), new ABNFComponent(false, 0, -1, 279), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, -1, 292), new ABNFComponent(false, 0, 1, 261), new ABNFComponent(false, 0, 1, 262), new ABNFComponent(false, 0, -1, 284), new ABNFComponent(false, 0, -1, -1) };

















	public static final ABNFComponent[] abnf_asr = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 1, 1, 283), new ABNFComponent(false, 1, 1, 293), new ABNFComponent(false, 1, 1, 258), new ABNFComponent(false, 0, 1, 1), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, -1, 284), new ABNFComponent(false, 0, -1, 282), new ABNFComponent(false, 0, -1, -1) };













	public static final ABNFComponent[] abnf_asa = { new ABNFComponent(true, 1, 1, 263), new ABNFComponent(false, 1, 1, 268), new ABNFComponent(false, 1, 1, 264), new ABNFComponent(false, 1, 1, 296), new ABNFComponent(false, 0, 1, 1), new ABNFComponent(false, 0, 1, 278), new ABNFComponent(false, 0, 1, 281), new ABNFComponent(false, 0, 1, 294), new ABNFComponent(false, 0, -1, 279), new ABNFComponent(false, 0, -1, 292), new ABNFComponent(false, 0, 1, 261), new ABNFComponent(false, 0, 1, 262), new ABNFComponent(false, 0, -1, 284), new ABNFComponent(false, 0, -1, -1) };





	public static class CheckABNFFailure
	{
		public AVP failed_avp;




		public int result_code;




		public String error_message;





		public CheckABNFFailure(AVP paramAVP, int paramInt, String paramString)
		{
			this.failed_avp = paramAVP;
			this.result_code = paramInt;
			this.error_message = paramString;
		}
	}



	public static final CheckABNFFailure checkABNF(Message paramMessage, ABNFComponent[] paramArrayOfABNFComponent)
	{
		int i = 0;
		for (int j = 0; j < paramArrayOfABNFComponent.length; j++)
			if (paramArrayOfABNFComponent[j].code == -1) {
				i = 1;
				break; }
		int m;
		int i1;
		Object localObject2; for (int j = 0; j < paramArrayOfABNFComponent.length; j++) {
			int k = paramArrayOfABNFComponent[j].code;
			if ((k != -1) && (
					(paramArrayOfABNFComponent[j].min_count != 0) || (paramArrayOfABNFComponent[j].max_count != -1)))
			{
				m = paramMessage.count(k);
				if (m < paramArrayOfABNFComponent[j].min_count)
				{
					return new CheckABNFFailure(new AVP(k, new byte[0]), 5005, "AVP must occur at least " + paramArrayOfABNFComponent[j].min_count + " times");
				}


				if ((paramArrayOfABNFComponent[j].max_count != -1) && (m > paramArrayOfABNFComponent[j].max_count))
				{
					Object localObject1 = null;
					i1 = 0;
					for (Iterator localIterator2 = paramMessage.subset(k).iterator(); localIterator2.hasNext();) { localObject2 = (AVP)localIterator2.next();
					i1++;
					if (i1 > paramArrayOfABNFComponent[j].max_count) {
						localObject1 = localObject2;
						break;
					}
					}
					return new CheckABNFFailure(new AVP((AVP)localObject1), 5009, null);
				}



				if (paramArrayOfABNFComponent[j].fixed_position)
				{



					int n = paramMessage.find_first(paramArrayOfABNFComponent[j].code);
					if (n != j)
					{
						return new CheckABNFFailure(new AVP(paramMessage.get(n)), 5004, "AVP occurs at wrong position");
					}
				}
			}
		}


		if (i == 0)
		{
			for (AVP localAVP : paramMessage.avps()) {
				m = 0;
				for (ABNFComponent localObject3 : paramArrayOfABNFComponent) {
					if (localAVP.code == ((ABNFComponent)localObject3).code) {
						m = 1;
						break;
					}
				}
				if (m == 0) {
					return new CheckABNFFailure(new AVP(localAVP), 5008, null);
				}
			}
		}




		return null;
	}
}
