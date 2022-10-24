package com.bosa.testfps;

import com.nimbusds.jose.crypto.*;


import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import com.nimbusds.jose.JWEObject;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.minio.*;
import io.minio.errors.*;
import org.json.JSONObject;

import javax.activation.MimetypesFileTypeMap;

/**
 * Test/sample code for an FPS service where users (citizens) can sign documents.
 * The flow is as follows:
 * <pre>
 * - the FPS creates an unsigned doc for a user and uploads it to the BOSA S3 server
 *      optionally, in case of an xml, a corresponding .xslt file can be uploaded
 *      optionally, in case of PDF visible signature, a .psp file can be uploaded
 * - the FPS obtains a token (a string) for this doc from the BOSA DSS server
         remark: this 'token' has nothing to do with OAuth or OpenID
 * - the FPS redirects the user to the BOSA DSS front-end server
 * - after a signed doc is created (and put on the BOSA S3 server), the user does a callback to the FPS
 * - the FPS retreives the signed doc from the BOSA S3 server and deletes the unsigned and signed docs (and xslt, psp)
 * </pre>
 *
 * Documentation: https://github.com/Fedict/fts-documentation
 * 
 * S3 info (to upload, download and delete files to/from an S3 server):
 *   https://docs.min.io/docs/minio-quickstart-guide.html
 *   https://github.com/minio/minio-java/tree/master/examples
 */
public class Main implements HttpHandler {

	private static String s3Url;
	private static String s3UserName;
	private static String s3Passwd;

	private static File filesDir;
	private static File inFilesDir;
	private static File outFilesDir;

	private static String getTokenUrl;

	private static String bosaDssFrontend;

	private static String localUrl;

	private MinioClient minioClient;

	// Default profiles
	private static String XADES_DEF_PROFILE = "XADES_1";
	private static String PADES_DEF_PROFILE = "PADES_1";

	private static String NAME = "FPS XXX"; // is displayed in user's browser

	private static String LANGUAGE = "en"; // options: en, nl, fr, de

	private static final String UNSIGNED_DIR = "unsigned";
	private static final String SIGNED_DIR = "signed";

	private static final String HTML_START =
		"<!DOCTYPE html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n" +
		"<title>FPS test signing service</title>\n  </head>\n  <body>\n";

	private static final String HTML_END = "</body>\n</html>\n";

	// This is defined by the firewall settings, don't change!
	private static int S3_PART_SIZE = 5 * 1024 * 1024;
	
	private static final Map<String, String> sigProfiles = new HashMap<String, String>();

	/** Start of the program */
	public static final void main(String[] args) throws Exception {
		// Read the config file

		Properties config = new Properties();
		config.load(new FileInputStream("config.txt"));

		int port =     Integer.parseInt(config.getProperty("port"));

		s3UserName =   config.getProperty("s3UserName");
		s3Passwd =     config.getProperty("s3Passwd");
		s3Url =        config.getProperty("s3Url");

		filesDir =     new File(config.getProperty("fileDir"));
		inFilesDir =   new File(filesDir, UNSIGNED_DIR);
		String tmp  =  config.getProperty("outFileDir");
		outFilesDir =  (null == tmp) ? new File(filesDir, SIGNED_DIR) : new File(tmp);

		getTokenUrl =  config.getProperty("getTokenUrl");

		bosaDssFrontend =  config.getProperty("bosaDssFrontend");

		localUrl =     config.getProperty("localUrl");

		String xadesProfile = config.getProperty("xadesProfile");
		sigProfiles.put("application/xml", (null == xadesProfile) ? XADES_DEF_PROFILE : xadesProfile);

		String padesProfile = config.getProperty("padesProfile");
		sigProfiles.put("application/pdf", (null == padesProfile) ? PADES_DEF_PROFILE : padesProfile);

		// Start the HTTP server
		startService(port);
	}

	/** Start the HTTP server, incomming request will go to the handle() method below */
	public static void startService(int port) throws Exception {
		HttpServer server = HttpServer.create();
		server.bind(new InetSocketAddress(port), 10);
		server.createContext("/", new Main());
		server.start();

		System.out.println("Service started - press Ctrl-C to stop\n");
		System.out.println("Surf with your browser to http://localhost:" + port);
	}

	/** Map filename to profile name */
        private String profileFor(String filename) {
            MimetypesFileTypeMap map = new MimetypesFileTypeMap();
            map.addMimeTypes("application/pdf pdf PDF");
            map.addMimeTypes("application/xml xml XML docx");
            String type = map.getContentType(filename);
            if(sigProfiles.containsKey(type)) {
                return sigProfiles.get(type);
            }
            return "CADES_1";
        }

	/**
	 * We handle 3 endpoints:
	 * <pre>
	 *  - the home page, where the user starts the signature process
	 *  - /sign, where
	 *      - the unsigned doc is uploaded to the S3,
	        - a getToken is done and
		- the user is redirected to the BOSA DSS front-endpoint
	 *  - /callback, where we are notified that the signature process is done (or failed/aborted)
	 *        and we download the signed doc and then delete the unsigned/signed docs
	 * </pre>
	 */
	public void handle(HttpExchange httpExch) throws IOException {
		try {
			String uri = httpExch.getRequestURI().toString();   // e.g. /sign?name=test.xml
			uri = URLDecoder.decode(uri);
			System.out.println("\nURI: " + uri);

			// Parse the query parameters
			int idx = uri.indexOf("?");
			String[] queryParams = idx >= 0 ? uri.substring(idx + 1).split("&") : null;

			if (uri.startsWith("/callback?")) {
				handleCallback(httpExch, queryParams);
			} else if (uri.startsWith("/hook")) {
				handleHook(httpExch);
			} else if (uri.startsWith("/getFileList")) {
				getFileList(httpExch);
			} else if (uri.startsWith("/sign?json=")) {
				handleJsonSign(httpExch, queryParams);
			} else {
				handleStatic(httpExch, uri);
			}
		}
		catch (Exception e) {
			try {
				System.out.println("ERR in Standalone.handle(): " + e.toString());
				e.printStackTrace();
				respond(httpExch, 500, "text/html", e.toString().getBytes());
			}
			catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	private void handleHook(HttpExchange httpExch) throws IOException {

		if ("POST".equals(httpExch.getRequestMethod())) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			copyStream(httpExch.getRequestBody(), bos);
			System.out.println(bos.toString());
		}
		httpExch.getResponseHeaders().add("Access-Control-Allow-Headers", "content-type");
		httpExch.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		httpExch.sendResponseHeaders(200, 0);
		httpExch.close();
	}

	private void handleStatic(HttpExchange httpExch, String uri) {
		int httpStatus = 200;
		byte[] bytes = null;
		String contentType = "text/plain";

		try {
			uri = uri.substring(1);
			if (uri.length() == 0) uri = "static/index.html";
			else if (!uri.startsWith("static")) throw new NoSuchFileException("Not so fast here !");
			uri = uri.replaceAll("\\.\\.", "").replaceAll("~", "");

			Path path = Paths.get(uri);

			System.out.println("Reading static file: " + uri);
			bytes = Files.readAllBytes(path);
			contentType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(path.getFileName().toString());

		} catch(NoSuchFileException e) {
			httpStatus = 404;
			bytes = "File not found".getBytes();
		} catch(IOException e) {
			httpStatus = 500;
			bytes = "Error".getBytes();
		}
		System.out.println("Returning : " + httpStatus + " - " + contentType + " - " + bytes.length);
		respond(httpExch, httpStatus, contentType, bytes);
	}

	/**
	 * get list of input files
	 */
	private void getFileList(HttpExchange httpExch) throws Exception {
		// Get the unsigned file names
		respond(httpExch, 200, "text/html", String.join(",", Arrays.asList(inFilesDir.list())).getBytes());
	}


	private void handleJsonSign(HttpExchange httpExch, String[] queryParams) throws Exception {
		String rawJson = queryParams[0].substring(5);

		StringBuilder sb = new StringBuilder();
		int len = rawJson.length();
		int off = 0;
		while(len != off) {
			char c = rawJson.charAt(off++);
			switch (c) {
				case '\'':
					sb.append('"');
					break;

				case '@':
					c = rawJson.charAt(off++);
					if (c =='o') sb.append("{\n");
					else if (c == 'c') sb.append("\n}");
					else if (c == 'O') sb.append("[\n");
					else if (c == 'C') sb.append("\n]");
					else if (c == 's') sb.append(": ");
					else if (c == 'S') sb.append(",\n");
					else System.out.println("ERROR in URL at position : " + --off + " - " + rawJson.substring(off));
					break;

				default:
					sb.append(c);
					break;
			}
		}

		String json = sb.toString();

		List<String> filesToUpload = new ArrayList<String>();
		String outFiles;
		boolean multidoc = json.indexOf("inputs") != -1;
		if (multidoc) {
			System.out.println("Multifile");
			json = json.replaceFirst("\\{", "{\n\"bucket\":\"" +s3UserName + "\",\n" +
					"\"password\":\"" + s3Passwd + "\",");

			outFiles = getToken(json, "outFilePath");
			if (outFiles != null && outFiles.length() ==0) outFiles = null;

				addTokens(json, "filePath", filesToUpload);
			if (outFiles == null) {
				String prefix = getToken(json, "outPathPrefix");
				outFiles = "";
				for(String inFile : filesToUpload) outFiles = outFiles + prefix + inFile + ",";
				outFiles = outFiles.substring(0, outFiles.length() - 1);
			}

			addTokens(json, "pspFilePath", filesToUpload);
			addTokens(json, "displayXsltPath", filesToUpload);
			addTokens(json, "outXsltPath", filesToUpload);

		} else {
			System.out.println("Singlefile");
			json = json.replaceFirst("\\{", "{\n\"name\":\"" +s3UserName + "\",\n" +
					"\"pwd\":\"" + s3Passwd + "\",");

			addTokens(json, "psp", filesToUpload);
			addTokens(json, "xslt", filesToUpload);
			addTokens(json, "in", filesToUpload);

			outFiles = getToken(json, "out");
		}

		for(int i = filesToUpload.size() - 1; i >= 0; i--) if (filesToUpload.get(i).startsWith("notFound")) filesToUpload.remove(i);

		uploadFiles(filesToUpload);

		System.out.println("Out file(s) : " + outFiles);

		createTokenAndRedirect(getTokenUrl + (multidoc ? "s" : ""), json, outFiles, String.join(",", filesToUpload), queryParams, httpExch);
	}

	private String getToken(String json, String tokenName) {
		List<String> tokenValues = new ArrayList<>();
		addTokens(json, tokenName, tokenValues);
		return tokenValues.size() == 0 ? null : tokenValues.get(0);
	}

	private void addTokens(String json, String tokenName, List<String> tokenValues) {
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

	private void createTokenAndRedirect(String url, String json, String out, String filesToDelete, String[] queryParams, HttpExchange httpExch) throws Exception {
		System.out.println("JSON for the getToken call:\n" + json);
		String token = postJson(url, json);

		System.out.println("  DONE, received token = " + token);

		// 3. Do a redirect to the BOSA DSS front-end
		// Format: https://{host:port}/sign/{token}?redirectURL={callbackURL}&language={language}&name={name}

		System.out.println("\n3. Redirect to the BOSA DSS front-end");
		String callbackURL = localUrl + "/callback?out=" + out + "&toDelete=" + filesToDelete;
		System.out.println("  Callback: " + callbackURL);
		String redirectUrl = bosaDssFrontend + "/sign/" + URLEncoder.encode(token) +
				"?redirectUrl=" + URLEncoder.encode(callbackURL);
				redirectUrl += "&HookURL=" + URLEncoder.encode(localUrl + "/hook");

		for(String queryParam : queryParams) {
			if (!queryParam.startsWith("json")) redirectUrl += "&" + queryParam;
		}

		System.out.println("  URL: " + redirectUrl);
		httpExch.getResponseHeaders().add("Location", redirectUrl);
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();
		System.out.println("  DONE, now waiting till we get a callback...");
	}

	/**
	 * In handleSign(), we specified a callback to this service after the signature process is done.
	 * E.g. /callback?out=signed_test.pdf&err=1&details=user_cancelled
	 * The first part has been specified completely by us previously, only the 'err' and 'details'
	 * have been added by the caller
	 */
	private void handleCallback(HttpExchange httpExch, String[] queryParams) throws Exception {

		System.out.println("\n4. Callback");

		String outFiles = getParam(queryParams, "out"); // this one we specified ourselves in handleSign()

		// These params were added by the BOSA DSS/front-end in case of an error
		String ref = getParam(queryParams, "ref");
		String err = getParam(queryParams, "err");
		String details = getParam(queryParams, "details");

		String htmlBody = "";
		if (null == err) {
			// If the signing was successful, download the signed file

			for(String out : outFiles.split(",")) {
				System.out.println("  Trying to downloading file " + out + " from the S3 server");
				MinioClient minioClient = getClient();

				try {
					InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(s3UserName).object(out).build());
					if (!outFilesDir.exists()) outFilesDir.mkdirs();

					File f = new File(outFilesDir, out);
					copyStream(stream, new FileOutputStream(f));
					System.out.println("    File is downloaded to " + f.getAbsolutePath());
				} catch(ErrorResponseException e) {
					if (e.errorResponse().code().equals("NoSuchKey")) System.out.println("  Not Found (probably not signed)");
					else System.out.println("  Exception " + e);
				}
			}

			htmlBody = "Thank you for signing<br>";
		}
		else {
			// Handle the errror. Here we just show the error info

			System.out.println("  Error: " + err);
			System.out.println("  Reference: " + ref);
			System.out.println("  Details: " + details);

			htmlBody = "Signing failed\n\n<br><br>\nReference: " + ref + "<br>\nError: " + err +
				(null == details ? "" : ("\n<br>\nDetails: " + URLDecoder.decode(details))) +
				"<br><br>\n\nClick <a href=\"/\">here</a> to try again\n";
		}

		// Delete everything the S3 server
		MinioClient minioClient = getClient();
		for(String fileToDelete : getParam(queryParams, "toDelete").split(",")) {
			deleteFileFromBucket(fileToDelete);
		}

		for(String fileToDelete : outFiles.split(",")) {
			deleteFileFromBucket(fileToDelete);
			deleteFileFromBucket(fileToDelete + ".validationreport.json");
		}

		// Return a message to the user
		String html = HTML_START + htmlBody + HTML_END;
		respond(httpExch, 200, "text/html", html.getBytes());
	}

	private void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[16384];
		int bytesRead;
		while ((bytesRead = in.read(buf, 0, buf.length)) >= 0)
			out.write(buf, 0, bytesRead);
		in.close();
		out.close();
	}

	private String streamToString(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(4096*4);
		copyStream(in, baos);
		return new String(baos.toByteArray());
	}

	private void deleteFileFromBucket(String fileToDelete) throws Exception {
		System.out.println("    Deleting from the S3 server :" + fileToDelete);
		minioClient.removeObject(RemoveObjectArgs.builder().bucket(s3UserName).object(fileToDelete).build());
	}

	/** Do an HTTP POST of a json (a REST call) */
	private String postJson(String urlStr, String json) throws IOException {
		HttpURLConnection urlConn = null;
		try {
			URL url = new URL(urlStr);
			urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestProperty("Content-Type", "application/json; utf-8");
			urlConn.setDoOutput(true);

			OutputStream os = urlConn.getOutputStream();
			os.write(json.getBytes("utf-8"));

			return streamToString(urlConn.getInputStream());
		}
		catch(Exception e) {
			if (urlConn != null) {
				throw new IOException(streamToString(urlConn.getErrorStream()));
			}
		}
		return null;
	}
		
	/** Send back a response to the client */
	private void respond(HttpExchange httpExch, int status, String contentType, byte[] data) {
		try {
			httpExch.getResponseHeaders().add("Content-Type", contentType);
			httpExch.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
			httpExch.getResponseHeaders().add("Pragma", "no-cache");
			httpExch.getResponseHeaders().add("Expires", "0");
			httpExch.sendResponseHeaders(status, data.length);
			OutputStream os = httpExch.getResponseBody();
			os.write(data);
			os.close();
			httpExch.close();
		}
		catch (Exception e) {
			System.out.println("Exception when send HTTP response: " + e.toString());
		}
	}

	/** Get the client for the S3 server */
	private MinioClient getClient() throws Exception {
		if (null == minioClient) {
			// Create client
			minioClient =
				MinioClient.builder()
					.endpoint(s3Url)
					.credentials(s3UserName, s3Passwd)
					.build();
		}
		return minioClient;
	}

	/** 'params' consist of name=value pairs, we want the value for the requested name */
	private String getParam(String[] params, String name) throws Exception {
		for (String p : params) {
			if (p.startsWith(name))
				return p.substring(p.indexOf("=") + 1);
		}
		return null;
	}

	private void uploadFiles(List<String> filePaths) throws Exception {
		for(String filePath : filePaths) uploadFile(filePath);
	}

	private void uploadFile(String fileName) throws Exception {

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

	private JSONObject decodeJWTToken(String jwtToken) throws Exception {
		JWEObject jweObject = JWEObject.parse(jwtToken);

		String s3path = "keys/" + jweObject.getHeader().getKeyID() + ".json";
		System.out.println("S3 Key path : " + s3path);
		GetObjectResponse keyObject = getClient().getObject(GetObjectArgs.builder().bucket("secbucket").object(s3path).build());
		byte buffer[] = new byte[512];
		int size = keyObject.read(buffer);

		String b64key = (String) new JSONObject(new String(buffer, 0, size)).get("encoded");
		System.out.println("S3 Key  : " + b64key);

		jweObject.decrypt(new DirectDecrypter(java.util.Base64.getDecoder().decode(b64key)));
		String[] parts = jweObject.getPayload().toString().split("\\."); // split out the "parts" (header, payload and signature)

		return new JSONObject(new String(java.util.Base64.getDecoder().decode(parts[1])));
	}
}
