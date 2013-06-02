package com.leoxiong.nfcapture;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.leoxiong.nfcapture.EMV.Response;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

public class EMV {
	/*
	 * Europay, MasterCard, Visa
	 * 
	 * Application Protocol Data Unit command
	 * 
	 * Command
	 * static final byte[] SELETC_PSE = {
	 * 	0x00, //[length 1] CLA (instruction class) - indicates the type of command
	 * 	0x00, //[length 1] INS (Instruction code) - indicates the specific command (0xA4: select, 0xB2: read)
	 * 	0x00, 0x00, //[length 2] Parameter bytes - instruction parameters for the command
	 * 	0x00, 0x00, 0x00, //[length 0, 2, or 3] Lc - encodes the number (Nc) of bytes of command data to follow
	 * 	0x00, //[length nc] Nc bytes of data
	 * 	0x00, 0x00, 0x00 //[length 0 - 3] Encodes the maximum number of (Ne) of response bytes expected, omit to receive all
	 * };
	 * 
	 * Response
	 * response data - [length Nr (at most Ne)] response data
	 * sw1-sw2 (response trailer) - [length 2] command processing status
	 */
	
	//Application Protocol Data Unit
	static class APDU {
		//Get Processing Options
		public static final byte[] GPO = {0x00, (byte) 0xB2, 0x01, 0x0C, 0x00};
		
		//Payment Environment Environment
		public static final byte[] PSE2 = Util.getBytes("2PAY.SYS.DDF01"); //2PAY.SYS.DDF01 is the contactless system, 1PAY.SYS.DDF01 is the contact system

		//Processing Options Data Object List
		public static final byte[] PDOL = { (byte) 0x83, 0x00 };
		public static final byte[] PDOL_VISA = { (byte) 0x83, (byte) 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	}
	
	static class Tags {
		//Dedicated File Name
		public static final byte DF_NAME = (byte) 0x84;
		
		//File Control Information Template
		public static final byte FCI_TEMPLATE = 0x6F;
		
		//File Control Information Proprietary Template
		public static final byte FCI_PROPRIETARY_TEMPLATE = (byte) 0xA5;
		
		//Application Label
		public static final byte APPLICATION_LABEL = 0x50;
		
		//Application Priority Indicator
		public static final byte APPLICATION_PRIORITY_INDICATOR = (byte) 0x87;
		
		//Application Identifier
		public static final byte AID = 0x4F;
		
		//Short File Identifier
		public static final byte SFI = (byte) 0x88;
		
		//File Control Information Issuer Discretionary Data Template
		public static final byte FCI_ISSUER_DISCRETIONARY_DATA_TEMPLATE = (byte) 0xBF0C;
		
		public static final byte APPLICATION_TEMPLATE = 0x61;
		
		//Processing Options Data Object List
		public static final byte PDOL = (byte) 0x9F38;

		//Application Interchange Profile
		public static final byte AIP = (byte) 0x80;
		
		//Application File Locator
		public static final byte AFL = (byte) 0x94;

		public static final byte LANGUAGE_PREFERENCE = (byte) 0x5F2D;
		
		public static final byte ISSUER_CODE_TABLE_INDEX = (byte) 0x9F11;

		public static final byte APPLICATION_PREFERRED_NAME = (byte) 0x9F12;

		public static final byte LOG_ENTRY = (byte) 0x9F4D;
		
		public static final byte EMV_PROPRIETARY_TEMPLATE = 0x70;

		public static final byte CARDHOLDER_NAME = (byte) 0x5F20;

		public static final byte TRACK_2_DATA = 0x57;

		public static final byte TRACK_1_DATA = (byte) 0x9F1F;
		
	}

	public IsoDep isoDep;
	
	EMV(Tag tag){
		IsoDep isoDep = IsoDep.get(tag);
		try {
			isoDep.connect();
			this.isoDep = isoDep;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	Response SELECT_PSE(byte[] dfName) throws IOException {
        return transceive(Util.concat(new byte[] { 0x00, (byte) 0xA4, 0x04, 0x00, 0x0E}, dfName));
	}
	
	Response SELECT_APPLET(byte[] aid) throws IOException {
		return transceive(Util.concat(new byte[] { 0x00, (byte) 0xA4, 0x04, 0x00 }, new byte[] { (byte) aid.length }, aid));
	}
	
	Response GPO(byte[] pdol) throws IOException {
		return transceive(Util.concat(new byte[] { (byte) 0x80, (byte) 0xA8, 0x00, 0x00, 0x00 }, pdol, new byte[] { 0x00 }));
	}
	
	Response READ_RECORD() throws IOException {
		return transceive(new byte[] { 0x00, (byte) 0xB2, 0x01, 0x0C, 0x00 });
	}
	
	Response transceive(byte[] data) throws IOException {
		return new Response(isoDep.transceive(data));
	}
	
	void dispose(){
		try {
			if (isoDep != null)
				isoDep.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static class EMV_Proprietary_Template {
		Response data;
		Track2 track2;
		String cardholderName;
		Response track1;
		Response emvProprietaryTemplate;
		
		EMV_Proprietary_Template(Response data) {
			this.data = data;
			this.emvProprietaryTemplate = data.getTag(Tags.EMV_PROPRIETARY_TEMPLATE);
			this.track2 = new Track2(emvProprietaryTemplate.getTag(Tags.TRACK_2_DATA));
			
			Matcher matcher = Pattern.compile("([\\w, ]{1,26})").matcher(emvProprietaryTemplate.getTag(Tags.CARDHOLDER_NAME).toASCII());
			this.cardholderName = matcher.find() ? matcher.group(1) : "null";
			this.track1 = emvProprietaryTemplate.getTag(Tags.TRACK_1_DATA);
		}
		static class Track2 {
			Response data;
			String pan;
			String expirary;
			
			Track2(Response data) {
				this.data = data;
				
				Matcher matcher;
				
				matcher = Pattern.compile("(\\d{1,19})").matcher(data.toHex());
				this.pan =  matcher.find() ? matcher.group(1) : null;
				
				matcher = Pattern.compile("D(\\d{4})").matcher(data.toHex());
				this.expirary =  matcher.find() ? matcher.group(1) : null;
			}
		}
		
	}
	
	static class FCI_Template {
		Response data;
		Response fci;
		Response dfName;
		FCI_Proprietary_Template proprietary_Template;
		
		public FCI_Template(Response data) {
			this.data = data;
			this.fci = data.getTag(Tags.FCI_TEMPLATE);
			this.dfName = fci.getTag(Tags.DF_NAME);
			this.proprietary_Template = new FCI_Proprietary_Template(fci.getTag(Tags.FCI_PROPRIETARY_TEMPLATE));
		}
		
		static class FCI_Proprietary_Template {
			Response data;
			Response sfi;
			Response languagePreference;
			Response issuerCodeTableIndex;
			FCI_Issuer_Discretionary_Data issuer_Discretionary_Data;
			Response pdol;
			Response applicationPreferredName;
			Response applicationPriorityIndicator;
			Response applicationLabel;
			
			FCI_Proprietary_Template (Response data){
				this.data = data;
				this.applicationLabel = data.getTag(Tags.APPLICATION_LABEL);
				this.applicationPriorityIndicator = data.getTag(Tags.APPLICATION_PRIORITY_INDICATOR);
				this.sfi = data.getTag(Tags.SFI);
				this.languagePreference = data.getTag(Tags.LANGUAGE_PREFERENCE);
				this.issuerCodeTableIndex = data.getTag(Tags.ISSUER_CODE_TABLE_INDEX);
				this.pdol = data.getTag(Tags.PDOL);
				this.applicationPreferredName = data.getTag(Tags.APPLICATION_PREFERRED_NAME);
				this.issuer_Discretionary_Data = new FCI_Issuer_Discretionary_Data(data.getTag(Tags.FCI_ISSUER_DISCRETIONARY_DATA_TEMPLATE));
			}
			
			static class FCI_Issuer_Discretionary_Data {
				Response data;
				Application_Template application_Template;
				Response logEntry;
				
				FCI_Issuer_Discretionary_Data(Response data){
					this.data = data;
					this.application_Template = new Application_Template(data.getTag(Tags.APPLICATION_TEMPLATE));
					this.logEntry = data.getTag(Tags.LOG_ENTRY);
				}
				
				class Application_Template {
					Response data;
					Response aid;
					Response apl;
					Response api;
					
					Application_Template(Response data) {
						this.data = data;
						this.aid = data.getTag(Tags.AID);
						this.apl = data.getTag(Tags.APPLICATION_LABEL);
						this.api = data.getTag(Tags.APPLICATION_PRIORITY_INDICATOR);
					}
				}
			}
		}
	}
	
	
	class Response {
		public byte[] data;
		
		Response(byte[] data){
			this.data = data;
		}
		
		Response(String data){
			this.data = Util.getBytes(data);
		}
		
		Response getTag(byte tag){
			int typeLength;
			for (int i = 0; i < data.length && i >= 0; i++){
				typeLength = (data[i] & 0x1F) == 0x1F ? 2 : 1;
				if (data[i] == tag || ((data[i] & 0x1F) == 0x1F && (byte) (data[i] << 8 | data[i + 1]) == tag)) {
					return new Response(Arrays.copyOfRange(data, i + typeLength + 1, data[i + typeLength] + i + typeLength + 1));
				}
				else{
					i += data[i + typeLength] + typeLength;
				}
			}
			return new Response(EMV.Util.getBytes("null"));
		}
		
		byte[] getBytes(){
			return data;
		}
		
		String toHex() {
			return Util.toHex(data);
		}
		
		String toASCII(){
			return Util.toASCII(data);
		}
		
		byte[] concat(byte[] b){
			return Util.concat(data, b);
		}
	}
	
	static class Util{
		
		static String toHex(byte[] hex) {
			StringBuilder stringBuilder = new StringBuilder();
			for (byte b : hex){
				stringBuilder.append(String.format("%02X", b));
			}
			return stringBuilder.toString();
		}
		
		static byte[] getBytes(String string) {
			return string.getBytes();
		}

		static String toASCII(byte[] bytes){
			try {
				return new String(bytes, 0, bytes.length, "ASCII");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				return e.getMessage();
			}
		}
		
		static int toInt(byte[] bytes){
			StringBuilder stringBuilder = new StringBuilder();
			for (byte b : bytes){
				stringBuilder.append(b);
			}
			return Integer.parseInt(stringBuilder.toString());
		}
		
		static byte[] concat(byte... bytes){
			return concat(bytes);
		}
		
		static byte[] concat(byte[] a, byte[]... b) {
			int length = a.length;
			for (byte[] c : b) {
				length += c.length;
			}
			
			byte[] out = Arrays.copyOf(a, length);
			
			int offset = a.length;
			for (byte[] c : b) {
				System.arraycopy(c, 0, out, offset, c.length);
				offset += c.length;
			}
			return out;
		}
		
	}

}