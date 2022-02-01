package com.zetes.projects.bosa.testfps;

import com.nimbusds.jose.crypto.*;


import java.net.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.nimbusds.jose.JWEObject;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.minio.*;
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

	private static final String PDF_SIG_FIELD_NAME = "signature_1";
	private static final String PDF_SIG_FIELD_COORDS = "1,30,20,180,60";

	private static final String HTML_START =
		"<!DOCTYPE html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n" +
		"<title>FPS test signing service</title>\n  </head>\n  <body>\n";

	private static final String REACT_FORM =
		"<script src=\"https://unpkg.com/react@17/umd/react.development.js\" crossorigin></script>" +
		"<script src=\"https://unpkg.com/react-dom@17/umd/react-dom.development.js\" crossorigin></script>" +
		"<script src=\"https://unpkg.com/babel-standalone@6/babel.min.js\"></script>" +
		"<script src=\"/static/form.js\"></script>\n" +
		"<div id=\"form_container\"></div>\n" +
		"<script>ReactDOM.render(React.createElement(NameForm), document.querySelector('#form_container'));</script>\n";

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
			} else if (uri.startsWith("/getFileList")) {
				getFileList(httpExch);
			} else if (uri.startsWith("/sign?name=")) {
				handleSign(httpExch, queryParams);
			} else {
				handleStatic(httpExch, uri);
			}
		}
		catch (Exception e) {
			try {
				System.out.println("ERR in Standalone.handle(): " + e.toString());
				e.printStackTrace();
				respond(httpExch, 500, "text/plain", e.toString().getBytes());
			}
			catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	private void handleStatic(HttpExchange httpExch, String uri) {
		int httpStatus = 200;
		byte[] bytes = null;

		uri = uri.substring(1);
		if (uri.length() == 0) uri = "static/index.html";

		Path path = Paths.get(uri);
		String contentType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(path.getFileName().toString());

		System.out.println("Reading static file: " + uri);
		try {
			bytes = Files.readAllBytes(path);
		} catch(NoSuchFileException e) {
			httpStatus = 404;
			bytes = "File not found".getBytes();
		} catch(IOException e) {
			httpStatus = 500;
			bytes = "Error".getBytes();
		}
		respond(httpExch, httpStatus, contentType, bytes);
	}

	/**
	 * get list of input files
	 */
	private void getFileList(HttpExchange httpExch) throws Exception {
		// Get the unsigned file names
		respond(httpExch, 200, "text/html", String.join(",", Arrays.asList(inFilesDir.list())).getBytes());
	}

	/**
	 * The /sign enpoint: the user clicked on a document to sign and got here.
	 * E.g. /sign?name=test.pdf
	 */
	private void handleSign(HttpExchange httpExch, String[] queryParams) throws Exception {
		String lang = getParam(queryParams, "lang");
		String psfC = getParam(queryParams, "psfC");
		String psfN = getParam(queryParams, "psfN");
		String psfP = getParam(queryParams, "psfP");;
		String profile = getParam(queryParams, "prof");
		String noDownload = getParam(queryParams, "noDownload");;
		String psp = getParam(queryParams, "psp");;
		String xslt =  getParam(queryParams, "xslt");;
		String name = getParam(queryParams, "name");
		String out = getParam(queryParams, "out");
		String signTimeout = getParam(queryParams, "signTimeout");
		String allowedToSign = getParam(queryParams, "allowedToSign");
		String policyId = getParam(queryParams, "policyId");
		String policyDescription = getParam(queryParams, "policyDescription");
		String policyDigestAlgorithm = getParam(queryParams, "policyDigestAlgorithm");
		String requestDocumentReadConfirm = getParam(queryParams, "requestDocumentReadConfirm");

		if (out == null) out = "signed_" + name;
		String nameFileExtentsion = getExtension(name);

		System.out.println("\nUser wants to sign doc '" + name + "' to '" + out + "'");
		if (! nameFileExtentsion.equals(getExtension(out))) {
			System.out.println("\nWARNING : IN & OUT extensions must be the same !!!!");
		}
		psfP = makeBool(psfP, "psfP");
		noDownload = makeBool(noDownload, "noDownload");
		requestDocumentReadConfirm = makeBool(requestDocumentReadConfirm, "requestDocumentReadConfirm");

		if (lang == null) {
			lang = LANGUAGE;
		}
		if (profile == null) {
			profile = profileFor(name);
		}

		// 1. Upload the unsigned file to the S3 server
		// Note: this could have been done in advance
		System.out.println("\n1. Uploading the unsigned doc (+ xslt, psp) to the S3 server...");
		uploadFile(new File(inFilesDir, name));
		String filesToDelete = name;

		if ("XML".equals(nameFileExtentsion)) {
			if (!profile.contains("XADES")) {
				System.out.println("\nWARNING : other than XADES selected for a .xml file. was :"+ profile);
			}
			if (null != xslt) {
				System.out.println("   Uploading the corresponding xslt file '" + xslt + "' to the S3 server...");
				uploadFile(new File(inFilesDir, xslt));
				filesToDelete += "," + xslt;
			}
		} else if ("PDF".equals(nameFileExtentsion)) {
			if (!profile.contains("PADES")) {
				System.out.println("\nWARNING : other than PADES selected for a .pdf file. was :" + profile);
			}
			if (psp != null) {
				File pspFile = new File(inFilesDir, psp);
				System.out.println("   Uploading " + psp + " (PDF visible signature profile) to the S3 server...");
				uploadFile(pspFile);

				filesToDelete += "," + psp;
			}
		}

		System.out.println("  DONE");

		// 2. Do a 'getToken' request to the BOSA DSS
		// This is a HTTP POST containing a json

		System.out.println("\n2. Doing a 'getTokenForDocument' to the BOSA DSS");

		String json = "{\n" +
			"  \"name\":\"" + s3UserName + "\",\n" +
			"  \"pwd\":\""  + s3Passwd +   "\",\n" +
			"  \"in\":\""   + name + "\",\n";
		if (null != xslt) json += ( "  \"xslt\":\""   + xslt + "\",\n" );
		if (null != psp) json += "  \"psp\":\"" + psp + "\",\n";
		if (null != psfN) json += "  \"psfN\":\"" + psfN + "\",\n";
		if (null != psfC) json += "  \"psfC\":\"" + psfC + "\",\n";
		if (null != signTimeout) json += "  \"signTimeout\": " + signTimeout + ",\n";
		if (null != psfP) json += "  \"psfP\":\"" + psfP + "\",\n";
		if (null != noDownload) json += "  \"noDownload\": " + noDownload + ",\n";
		if (null != requestDocumentReadConfirm) json += "  \"requestDocumentReadConfirm\": " + requestDocumentReadConfirm + ",\n";
		if (null != allowedToSign) {
			json += "  \"allowedToSign\": [\n";
			for(String allowed : allowedToSign.split(",")) {
				json += "  { \"nn\": \"" + allowed + "\" },\n";
			}
			json = json.substring(0, json.length() - (json.charAt(json.length() - 2) == ',' ? 2 : 0)) + "],";
		}
		if (null != policyId) {
			json += "  \"policyId\": \"" + policyId + "\",\n";
			if (null != policyDescription) {
				json += "  \"policyDescription\": \"" + policyDescription + "\",\n";
			}
			json += "  \"policyDigestAlgorithm\": \"" + policyDigestAlgorithm + "\",\n";
		}
		json += "  \"out\":\""  + out + "\",\n" +
			"  \"lang\":\""  + lang + "\",\n" +        // used for the text in PDF visible signatures
			"  \"prof\":\"" + profile + "\"\n" +
			"}";
		System.out.println("JSON for the getToken call:\n" + json);
		String token = postJson(getTokenUrl, json);

		System.out.println("  DONE, received token = " + token);

		// 3. Do a redirect to the BOSA DSS front-end
		// Format: https://{host:port}/sign/{token}?redirectURL={callbackURL}&language={language}&name={name}

		System.out.println("\n3. Redirect to the BOSA DSS front-end");
		String callbackURL = localUrl + "/callback?out=" + out + "&toDelete=" + filesToDelete;
		System.out.println("  Callback: " + callbackURL);
		String redirectUrl = bosaDssFrontend + "/sign/" + URLEncoder.encode(token) +
			"?redirectUrl=" + URLEncoder.encode(callbackURL) + "&language=" + LANGUAGE +
			"&name=" + URLEncoder.encode(NAME);
		System.out.println("  URL: " + redirectUrl);
		httpExch.getResponseHeaders().add("Location", redirectUrl);
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();
		System.out.println("  DONE, now waiting till we get a callback...");
	}

	private String makeBool(String value, String name) {
		if (value != null && !("true".equals(value) || "false".equals(value))) {
			System.out.println("\n" + name + " must be 'true' or 'false' (was " + value + ")");
			value = "false";
		}
		return value;
	}

	private String getExtension(String fileName) {
		int pos = fileName.lastIndexOf('.');
		return pos >= 0 ? fileName.substring(pos + 1).toUpperCase() : "NOEXT";
	}

	/**
	 * In handleSign(), we specified a callback to this service after the signature process is done.
	 * E.g. /callback?out=signed_test.pdf&err=1&details=user_cancelled
	 * The first part has been specified completely by us previously, only the 'err' and 'details'
	 * have been added by the caller
	 */
	private void handleCallback(HttpExchange httpExch, String[] queryParams) throws Exception {

		System.out.println("\n4. Callback");

		String out = getParam(queryParams, "out"); // this one we specified ourselves in handleSign()

		// These params were added by the BOSA DSS/front-end in case of an error
		String ref = getParam(queryParams, "ref");
		String err = getParam(queryParams, "err");
		String details = getParam(queryParams, "details");

		String htmlBody = "";
		if (null == err) {
			// If the signing was successfull, download the signed file

			System.out.println("  Downloading file " + out + " from the S3 server");
			MinioClient minioClient = getClient();
			InputStream stream =
				minioClient.getObject(
					GetObjectArgs.builder().bucket(s3UserName).object(out).build());

			if (!outFilesDir.exists())
				outFilesDir.mkdirs();

			File f = new File(outFilesDir, out);
			FileOutputStream fos = new FileOutputStream(f);
			byte[] buf = new byte[16384];
			int bytesRead;
			while ((bytesRead = stream.read(buf, 0, buf.length)) >= 0)
				fos.write(buf, 0, bytesRead);
			stream.close();
			fos.close();
			System.out.println("    File is downloaded to " + f.getAbsolutePath());

			htmlBody = "Thank you for signing<br><A href=\"/files/signed/" + f.getName() + "\">Download signed file</A>";
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
		for(String fileToDelete : (getParam(queryParams, "toDelete") + "," + out + "," + out + ".validationreport.json").split(",")) {
			System.out.println("    Deleting from the S3 server :" + fileToDelete);
			minioClient.removeObject(RemoveObjectArgs.builder().bucket(s3UserName).object(fileToDelete).build());
		}

		// Return a message to the user
		String html = HTML_START + htmlBody + HTML_END;
		respond(httpExch, 200, "text/html", html.getBytes());
	}

	/** Do an HTTP POST of a json (a REST call) */
	private String postJson(String urlStr, String json) throws Exception {
		URL url = new URL(urlStr);
		HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
		urlConn.setRequestProperty("Content-Type", "application/json; utf-8");
		urlConn.setDoOutput(true);

		OutputStream os = urlConn.getOutputStream();
		os.write(json.getBytes("utf-8"));

		InputStream is = urlConn.getInputStream();
		byte[] buf = new byte[5000];
		int len = is.read(buf);
		String ret = new String(buf, 0, len).trim();

		return ret;
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

	private void uploadFile(File f) throws Exception {
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
