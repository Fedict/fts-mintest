package com.bosa.testfps;

import com.sun.net.httpserver.HttpExchange;
import io.minio.*;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

import static com.bosa.testfps.Main.*;
import static org.bouncycastle.asn1.cms.CMSAttributes.contentType;

public class Tools {
	// HTTP *******************************************************************************************

	private static final Map<String, String> urlEncoded = Map.of("Content-Type", "application/x-www-form-urlencoded");
	private static final Map<String, String> jsonEncoded = Map.of("Content-Type", "application/json; utf-8");

	/** Do an HTTP POST of a json (a REST call) */
	static  String postURLEncoded(String urlStr, String json) throws IOException {
		return httpRaw(false, urlStr, json, urlEncoded);
	}

	static  String postJson(String urlStr, String json) throws IOException {
		return httpRaw(false, urlStr, json, jsonEncoded);
	}

	static  String postJson(String urlStr, String json, String authorization) throws IOException {
		return httpRaw(false, urlStr, json, Map.of("Authorization", authorization, "Content-Type", "application/json; utf-8"));
	}

	static String postJson(String urlStr, String json, Map<String, String> headers) throws IOException {
		Map<String, String> hdrs = new HashMap<>();
		hdrs.put("Content-Type", "application/json; utf-8");
		hdrs.putAll(headers);
		return httpRaw(false, urlStr, json, hdrs);
	}

	static  String getJson(String urlStr, String authorization) throws IOException {
		return httpRaw(true, urlStr, null, Map.of("Authorization", authorization));
	}

	static  String httpRaw(boolean isGet, String urlStr, String data, Map<String, String> headers) throws IOException {
		System.out.println("Request from " + urlStr + " :" + data);
		HttpURLConnection urlConn = null;
		try {
			URL url = new URL(urlStr);

			urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod(isGet ? "GET" : "POST");
			for(Map.Entry<String, String> header : headers.entrySet()) {
				urlConn.setRequestProperty(header.getKey(), header.getValue());
			}
			if (!isGet) {
				urlConn.setDoOutput(true);
				OutputStream os = urlConn.getOutputStream();
				os.write(data.getBytes(StandardCharsets.UTF_8));
			}

			String reply = streamToString(urlConn.getInputStream());
			System.out.println("Reply from " + urlStr + " :" + reply);
			return reply;
		}
		catch(Exception e) {
			e.printStackTrace();
			if (urlConn != null && urlConn.getErrorStream() != null) {
				throw new IOException(streamToString(urlConn.getErrorStream()));
			}
		}
		return null;
	}

	/** Send back a response to the client */
	static void respond(HttpExchange httpExch, int status, String contentType, byte [] data) throws IOException {
		httpExch.getResponseHeaders().add("Content-Type", contentType);
		httpExch.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
		httpExch.getResponseHeaders().add("Pragma", "no-cache");
		httpExch.getResponseHeaders().add("Expires", "0");
		httpExch.sendResponseHeaders(status, data.length);
		httpExch.getResponseBody().write(data);
		httpExch.getResponseBody().close();
		httpExch.close();
	}

	static byte fileData[];
	static String fileName;

	static void handleGetFile(HttpExchange httpExch) throws IOException {
		if (fileData != null) {
			httpExch.getResponseHeaders().add("Content-Transfer-Encoding", "binary");
			httpExch.getResponseHeaders().add("Content-Disposition","attachment; filename=\"" + fileName + "\"");
			respond(httpExch, 200, "application/octet-stream", fileData);
			fileData = null;
		} else respond(httpExch, 200, "text/html", ("File not found !").getBytes());
	}
//	reply = "<script language=\"javascript\">window.onload=function() { document.getElementById('file').click(); } </script>" +
//			"<a id='file' href='/getFile'></a><h1>Sealed document '" + outFilename + "' was downloaded</h1>";

	// Minio *******************************************************************************************

	static void deleteFileFromBucket(String fileToDelete) throws Exception {
		fileToDelete = sanitize(fileToDelete);
		System.out.println("    Deleting from the S3 server :" + fileToDelete);
		minioClient.removeObject(RemoveObjectArgs.builder().bucket(s3UserName).object(fileToDelete).build());
	}

	static void uploadFiles(List<String> filePaths) throws Exception {
		for(String filePath : filePaths) uploadFile(filePath);
	}

	static void uploadFile(String fileName) throws Exception {

		fileName = sanitize(fileName);
		System.out.println("   Uploading " + fileName + " to the S3 server...");
		File f = new File(inFilesDir, fileName);
		FileInputStream fis = new FileInputStream(f);

		getClient().putObject(
				PutObjectArgs.builder()
						.bucket(s3UserName)
						.object(f.getName())
						.stream(fis, f.length(), S3_PART_SIZE)
						.build());
	}

	static void setMinioClientURL(boolean isDocker) {
		Main.isDocker = isDocker;
		Object minioClient = null;
	}

	/** Get the client for the S3 server */
	static MinioClient getClient() throws Exception {
		if (null == minioClient) {
			// Create client
			minioClient = MinioClient.builder()
					.endpoint(isDocker ? s3Url : dpS3Url)
					.credentials(s3UserName, s3Passwd)
					.build();
		}
		return minioClient;
	}

	static void fileToMinio(String fileName, byte [] fileData) throws Exception {
		getClient().putObject(
				PutObjectArgs.builder()
						.bucket(s3UserName)
						.object(fileName)
						.stream(new ByteArrayInputStream(fileData), fileData.length, S3_PART_SIZE)
						.build());
	}

	static byte[] fileFromMinio(String fileName) throws Exception {
		InputStream inStream = getClient().getObject(GetObjectArgs.builder().bucket(s3UserName).object(fileName).build());
		ByteArrayOutputStream outStream = new ByteArrayOutputStream(8192);
		copyStream(inStream, outStream);
		outStream.close();
		inStream.close();
		return outStream.toByteArray();
	}

	static void deleteMinioFile(String fileName) throws Exception {
		getClient().removeObject(RemoveObjectArgs.builder().bucket(s3UserName).object(fileName).build());
	}

	// Misc *******************************************************************************************

	static  String mimeTypeFor(String filename) {
		String type = "text/plain";
		int pos = filename.lastIndexOf('.');
		if (pos >= 0) {
			String extension = filename.substring(pos + 1).toLowerCase(Locale.ROOT);
			if (extension.equals("xml")) type = "application/xml";
			if (extension.equals("pdf")) type = "application/pdf";
			if (extension.equals("js")) type = "text/javascript";
			if (extension.equals("html") || extension.equals("htm")) type = "text/html";
		}
		return type;
	}

	/** Map filename to profile name */
	static String profileFor(String filename) {
		String type = mimeTypeFor(filename);
		return sigProfiles.getOrDefault(type, "CADES_1");
	}

	static  String toStringJWT(String rawJwt) {
		String bits[] = rawJwt.split("\\.");
		return "HEADER: " + toStringB64JSON(bits[0]) + "\nPAYLOAD: " + toStringB64JSON(bits[1]) + "\nSIGNATURE: " + bits[2];
	}

	static  String toStringB64JSON(String b64) {
		return new JSONObject(new String(Base64.getDecoder().decode(b64))).toString(4);
	}

	static  String toStringJSON(String json) {
		return new JSONObject(json).toString(4);
	}

	static byte[] streamToBytes(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(4096*4);
		copyStream(in, baos);
		baos.close();
		in.close();
		return baos.toByteArray();
	}

	static  String streamToString(InputStream in) throws IOException {
		return new String(streamToBytes(in));
	}

	static  String getDocumentAsB64(File folder, String inFile) throws IOException {
		return Base64.getEncoder().encodeToString(getDocument(folder, inFile));
	}
	static  String getDocumentAsString(File folder, String inFile) throws IOException {
		return streamToString(Files.newInputStream(new File(folder, inFile).toPath()));
	}
	static  byte[] getDocument(File folder, String inFile) throws IOException {
		FileInputStream fis = new FileInputStream(new File(folder, sanitize(inFile)));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		copyStream(fis, baos);
		baos.close();
		fis.close();
		return baos.toByteArray();
	}

	static  void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[16384];
		int bytesRead;
		while ((bytesRead = in.read(buf, 0, buf.length)) >= 0)
			out.write(buf, 0, bytesRead);
		in.close();
		out.close();
	}

	static  String getDelimitedValue(String str, String beginMark, String endMark) throws Exception {
		int pos = str.indexOf(beginMark);
		if (pos < 0) throw new Exception("No " + beginMark + " ?");
		pos += beginMark.length();
		int endPos = str.indexOf(endMark, pos);
		if (endPos < 0) throw new Exception("No "+ endMark + " after " + beginMark + " ?");
		return str.substring(pos, endPos);
	}

	static String getToken(String json, String tokenName) {
		List<String> tokenValues = new ArrayList<>();
		addTokens(json, tokenName, tokenValues);
		return tokenValues.isEmpty() ? null : tokenValues.get(0);
	}

	static void addTokens(String json, String tokenName, List<String> tokenValues) {
		int posMain = 0;
		while(posMain != json.length()) {
			String search = '"' + tokenName + "\": ";
			int pos = json.indexOf(search, posMain);
			if (pos == -1) break;
			pos += search.length();
			int pos2 = json.indexOf(",", pos);
			int pos3 = json.indexOf("\n", pos);
			if (pos2 >= 0) {
				if (pos3 >= 0 && pos3 < pos2) pos2 = pos3;
			} else {
				if (pos3 >= 0) pos2 =pos3;
				else pos2 = json.length();
			}
			String tokenValue = json.substring(pos, pos2);
			posMain = pos2;

			if (tokenValue.charAt(0) == '"' && tokenValue.charAt(tokenValue.length() - 1) == '"') tokenValue = tokenValue.substring(1, tokenValue.length() - 1);

			System.out.println(tokenValue);
			tokenValues.add(tokenValue);
		}
	}

	static  String sanitize(String rawString) {
		String sanitized =  null;
		if (rawString != null) {
			sanitized = rawString.replaceAll("[^a-zA-Z0-9_.~]", "");
			if (!sanitized.equals(rawString)) System.out.println("Sanitized : " + sanitized);
		}
        return sanitized;
	}

	static  String calcHash(String URL, String algo) {
		try  {
			HttpURLConnection urlConn = (HttpURLConnection) new URL(URL).openConnection();
			byte[] digest = MessageDigest.getInstance(algo).digest(streamToBytes(urlConn.getInputStream()));
			String b64Digest = Base64.getEncoder().encodeToString(digest);
			System.out.printf("\"%s\" : %s\n", URL, b64Digest);
			return b64Digest;
		} catch(Exception e) {
			System.out.printf("ERROR : \"%s\" : %s\n", URL, e.toString());
		}
		return null;
	}
}
