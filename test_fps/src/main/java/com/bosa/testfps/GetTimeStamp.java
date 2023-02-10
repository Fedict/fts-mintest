package com.bosa.testfps;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.tsp.TimeStampResp;
import org.bouncycastle.tsp.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;

public class GetTimeStamp {

	public static final void main(String[] args) throws Exception {

		byte[] dataToTimeStamp = "data to Timestamp".getBytes();
		String timeStampingURL = "http://tss.eidpki.belgium.be";
		ASN1ObjectIdentifier digestAlogrithm = TSPAlgorithms.SHA224;

		TimeStampToken tsToken = getTimeStamp(dataToTimeStamp, digestAlogrithm, timeStampingURL);
		System.out.println("Timestamp token: " + Arrays.toString(tsToken.getEncoded()));

		TimeStampTokenInfo tsInfo = tsToken.getTimeStampInfo();
		System.out.println("TSA DN: " + tsInfo.getTsa());
		System.out.println("Serial number: " + tsInfo.getSerialNumber());
		System.out.println("Timestamp Policy: " + tsInfo.getPolicy());

		System.out.println("Timestamp TIME : " + tsInfo.getGenTime());
	}

	public static TimeStampToken getTimeStamp(byte[] data, ASN1ObjectIdentifier digestAlgo, String tspUrl) throws TSPException {
		InputStream in = null;
		OutputStream out = null;
		HttpURLConnection con = null;
		try {
			byte[] digestValue = MessageDigest.getInstance(digestAlgo.getId()).digest(data);

			TimeStampRequestGenerator timeStampRequestGenerator = new TimeStampRequestGenerator();
			TimeStampRequest timeStampRequest = timeStampRequestGenerator.generate(digestAlgo, digestValue);
			byte request[] = timeStampRequest.getEncoded();

			con = (HttpURLConnection) new URL(tspUrl).openConnection();
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-type", "application/timestamp-query");
			con.setRequestProperty("Content-length", String.valueOf(request.length));
			out = con.getOutputStream();
			out.write(request);
			out.flush();

			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("Received HTTP error: " + con.getResponseCode() + " - " + con.getResponseMessage());
			}

			in = con.getInputStream();
			TimeStampResp resp = TimeStampResp.getInstance(new ASN1InputStream(in).readObject());
			TimeStampResponse response = new TimeStampResponse(resp);
			response.validate(timeStampRequest);
			PKIFailureInfo failInfo = response.getFailInfo();
			if (failInfo != null) {
				int failId = failInfo.intValue();
				throw new TSPException("Timestamping exception : " + failId + " - " + getTimeStampFailureName(failId));
			}
			out.close();
			in.close();
			con.disconnect();

			return response.getTimeStampToken();

		} catch(Exception e) {
			if (in != null) try { in.close(); } catch (IOException ignore) {}
			if (out != null) try { out.close(); } catch (IOException ignore) {}
			throw new TSPException("Timestamp", e);
		}
	}

	private static String getTimeStampFailureName(Integer failId) throws IllegalAccessException {
		for (Field field : PKIFailureInfo.class.getDeclaredFields()) {
			if( Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) ) {
				if (failId.equals(field.get(field.getType()))) return field.getName();
			}
		}
		return "UNKNOWN Error :" + failId;
	}
}