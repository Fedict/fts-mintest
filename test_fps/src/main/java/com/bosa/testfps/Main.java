package com.bosa.testfps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.minio.*;
import io.minio.errors.*;

import static com.bosa.testfps.Tools.*;

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

	static Boolean cleanupTempFiles;
	static String s3Url;
	static String dpS3Url;
	static String s3UserName;
	static String s3Passwd;

	static File filesDir;
	static File inFilesDir;
	static File outFilesDir;
	static String signValidationSvcUrl;
	static String validationUrl;
	static String signUrl;
	static String sealingSignUrl;
	static String idpGuiUrl;
	static String idpUrl;
	static String esealingUrl;
	static String sepiaSealingUrl;

	static String sadKeyFile;
	static String sadKeyPwd;
	static boolean showSealing;
	static boolean showIDP;

	static String bosaDssFrontend;

	static String localUrl;

	static MinioClient minioClient;

	// Default profiles
	static String XADES_DEF_PROFILE = "XADES_1";
	static String PADES_DEF_PROFILE = "PADES_1";

	static final String UNSIGNED_DIR = "unsigned";
	static final String SIGNED_DIR = "signed";

	static final String HTML_START =
			"<!DOCTYPE html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n" +
					"<title>FPS test signing service</title>\n  </head>\n  <body>\n";

	static final String HTML_END = "</body>\n</html>\n";

	// This is defined by the firewall settings, don't change!
	static int S3_PART_SIZE = 5 * 1024 * 1024;

	// System.out.println("Authorization Basic (selor:test123) =" + Base64.getEncoder().encodeToString("selor:test123".getBytes(StandardCharsets.UTF_8)));
	// System.out.println("Authorization Basic (sealing:123456) =" + Base64.getEncoder().encodeToString("sealing:123456".getBytes(StandardCharsets.UTF_8)));
	static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString("sealing:123456".getBytes(StandardCharsets.UTF_8));

	static final Map<String, String> sigProfiles = new HashMap<String, String>();
	static boolean isDocker;

	/** Start of the program */
	public static void main(String[] args) throws Exception {

		calcPolicyHashes();

		Properties properties = System.getProperties();
		System.out.println("************************* System Properties *****************************************");
		properties.forEach((k, v) -> System.out.println(k + ":" + v));

		// Read the config file
		Properties config = new Properties();
		config.load(Files.newInputStream(Paths.get("config.txt")));

		System.out.println("*********************** Application Properties ***************************************");
		config.forEach((k, v) -> System.out.println(k + ":" + v));

		System.out.println("********************************* Go *************************************************");

		int port =     Integer.parseInt(config.getProperty("port"));

		String cleanupTempFilesStr = config.getProperty("cleanupTempFiles");
		cleanupTempFiles = cleanupTempFilesStr == null || Boolean.parseBoolean(cleanupTempFilesStr);

		s3UserName		= config.getProperty("s3UserName");
		s3Passwd		= config.getProperty("s3Passwd");
		s3Url			= config.getProperty("s3Url");
		dpS3Url			= config.getProperty("dps3Url");
		idpGuiUrl		= config.getProperty("idpGuiUrl");
		idpUrl			= config.getProperty("idpUrl");

		sadKeyFile		= config.getProperty("sadKeyFile");
		sadKeyPwd		= config.getProperty("sadKeyPwd");

		esealingUrl		= config.getProperty("easealingUrl");
		sepiaSealingUrl	= config.getProperty("sepiaSealingUrl");

		signValidationSvcUrl =  config.getProperty("getTokenUrl").replace("/signing/getTokenForDocument", "");
		signUrl			= config.getProperty("signUrl");
		sealingSignUrl =  config.getProperty("sealingSignUrl");
		if (sealingSignUrl == null) sealingSignUrl = signUrl;
		validationUrl	= config.getProperty("validationUrl");
		if (validationUrl == null) validationUrl = signUrl;

		filesDir		= new File(config.getProperty("fileDir"));
		inFilesDir		= new File(filesDir, UNSIGNED_DIR);
		String tmp		= config.getProperty("outFileDir");
		outFilesDir		= (null == tmp) ? new File(filesDir, SIGNED_DIR) : new File(tmp);

		bosaDssFrontend = config.getProperty("bosaDssFrontend");

		localUrl		= config.getProperty("localUrl");

		showSealing = "true".equals(config.getProperty("showSealing"));
		showIDP			= "true".equals(config.getProperty("showIDP"));

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
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.createContext("/", new Main());
		server.start();

		System.out.println("Service started - press Ctrl-C to stop\n");
		System.out.println("Surf with your browser to http://localhost:" + port);
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
			Map <String,String> queryParams = new HashMap<>();
			if (idx >= 0) {
				for(String qp : uri.substring(idx + 1).split("&")) {
					int pos = qp.indexOf('=');
					if (pos < 0) queryParams.put(qp.substring(0, qp.length()), "");
					else queryParams.put(qp.substring(0, pos), qp.substring(pos+1, qp.length()));
				}
			}

			if (uri.startsWith("/callback?")) {
				handleCallback(httpExch, queryParams);
			} else if (uri.startsWith("/swagger")) {
				handleSwagger(queryParams, httpExch);
			} else if (uri.startsWith("/hook")) {
				handleHook(httpExch);
			} else if (uri.startsWith("/getFileList")) {
				getFileList(httpExch);
			} else if (uri.startsWith("/validate_jump")) {
				validate(httpExch, queryParams);
			} else if (uri.startsWith("/sign?json=")) {
				handleJsonSign(httpExch, queryParams);
			} else if (uri.startsWith("/seal?")) {
				Sealing.handleJsonSealing(httpExch, queryParams);
			} else if (uri.startsWith("/getSealingCredentials")) {
				Sealing.getCredentials(httpExch);
			} else if (uri.startsWith("/idp_")) {
				handleIdp(httpExch, uri, queryParams);
			} else if (uri.startsWith("/getFile")) {
				handleGetFile(httpExch);
			} else if (uri.startsWith("/perf")) {
				PerfTest.start(httpExch);
			} else if (uri.startsWith("/viewPerf")) {
				PerfTest.view(httpExch);
			} else if (uri.startsWith("/test")) {
				randomTest(httpExch);
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

	private void randomTest(HttpExchange httpExch) {

	}

	private void handleSwagger(Map<String, String> queryParams, HttpExchange httpExch) throws IOException {
		String URL = "";
		switch(queryParams.get("to")) {
			case "signval":
				URL = signUrl;
				break;
			case "idp":
				URL = idpUrl;
				break;
			case "seal":
				URL = esealingUrl;
				break;
		}
		httpExch.getResponseHeaders().add("Location", URL + "/swagger-ui/index.html");
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();
	}

	private void handleIdp(HttpExchange httpExch, String uri, Map<String, String> queryParams) throws Exception {

		if (uri.startsWith("/idp_jump")) {
			String redirectUrl = idpGuiUrl + "?redirect_uri=" + URLEncoder.encode(localUrl + "/idp_land", StandardCharsets.UTF_8.name()) + "&client_id=" + queryParams.get("client_id") + "&scope=" + URLEncoder.encode(queryParams.get("scope"));

			System.out.println("  URL: " + redirectUrl);
			httpExch.getResponseHeaders().add("Location", redirectUrl);
			httpExch.sendResponseHeaders(303, 0);
			httpExch.close();
		} else {
			String response = queryParams.get("error");
			if (response == null) {
				JWEObject jweObject = JWEObject.parse(queryParams.get("code"));
				String jwtString = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jweObject);
				response = formatParam(queryParams, "scope") + formatParam(queryParams, "code") + "<pre>" + jwtString + "</pre>";
			}
			respond(httpExch, 200, "text/html", ("<HTML>" + response + "</HTML>").getBytes());
		}
	}

	private static String formatParam(Map<String, String> queryParams, String name) throws UnsupportedEncodingException {
		return "<p>" + name + ": " + URLDecoder.decode(queryParams.get(name), StandardCharsets.UTF_8.name()) + "</p>";
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

	private void validate(HttpExchange httpExch, Map<String, String> queryParams) throws Exception {

		System.out.println("Validating file : " + queryParams);

		String json = "{" +
						"\"signedDocument\": {" + getValidateFileAsJSONField("bytes", queryParams, "file") + "}," +
						"\"trust\": {";

		String certs = getValidateFileAsJSONField("certs", queryParams, "cert*");
		if (!certs.isEmpty()) json += certs + ",";

		String keyStore = getValidateFileAsJSONField("keystore", queryParams, "keystore");
		if (!keyStore.isEmpty()) {
			json += keyStore + ",";
			String password = queryParams.get("password");
			if (password != null) json += "\"password\": \"" + password + "\",";
		}

		if (json.endsWith(",")) json = json.substring(0, json.length() -1);
		json += "} }";
		String reply = postJson(validationUrl + "/validation/validateSignature", json, null);
		respond(httpExch, 200, "text/plain", reply.getBytes());
	}

	private String getValidateFileAsJSONField(String jsonFieldName, Map<String, String> queryParams, String fieldName) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("\"").append(jsonFieldName).append("\": ");
		if (fieldName.endsWith("*")) {
			sb.append("[ ");
			fieldName = fieldName.substring(0, fieldName.length() - 1);
			int count = 0;
			while(true) {
				String fName = queryParams.get(fieldName + Integer.toString(count++));
				if (fName == null) {
					if (count == 1) return "";
					break;
				}
				getValidateFile(sb, fName);
				sb.append(",");
			}
			sb.setLength(sb.length() - 1);
			sb.append(" ]");
		} else {
			String fName = queryParams.get(fieldName);
			if (fName == null) return "";
			getValidateFile(sb, fName);
		}
		return sb.toString();
	}

	private void getValidateFile(StringBuffer sb, String fileName) throws IOException {
		System.out.println("Reading file : " + fileName);
		Path filePath = Paths.get("files/validate/" + sanitize(fileName));
		sb.append("\"").append(Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)) ).append("\"");
	}

	private void handleStatic(HttpExchange httpExch, String uri) throws IOException {
		List<String> tagsToFilter = new ArrayList<>();
		int httpStatus = 200;
		byte[] bytes = null;
		String contentType = "text/plain";

		try {
			uri = uri.substring(1);
			if (uri.isEmpty()) {
				uri = "static/index.html";
				if (!showSealing) tagsToFilter.add("SEALING");
				if (!showIDP) tagsToFilter.add("IDP");
			}
			else {
				// Harden inputs
				if (!uri.startsWith("static")) throw new NoSuchFileException("Not so fast here !");
				uri = uri.replaceAll("\\.\\.", "").replaceAll("~", "");
			}

			Path path = Paths.get(uri);

			System.out.println("Reading static file: " + uri);
			bytes = filterTags(Files.readAllBytes(path), tagsToFilter);

			contentType = mimeTypeFor(path.getFileName().toString());

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

	private byte[] filterTags(byte[] bytes, List<String> tagsToFilter) {
		String bytesStr = null;
		for (String tag : tagsToFilter) {
			if (bytesStr == null) bytesStr = new String(bytes);
			int beginPos = bytesStr.indexOf('<' + tag + '>');
			if (beginPos != -1) {
				String end = "</" + tag + '>';
				int endPos = bytesStr.indexOf(end, beginPos);
				if (endPos != -1) bytesStr = bytesStr.substring(0, beginPos) + bytesStr.substring(endPos + end.length());
			}
		}
		return bytesStr != null ? bytesStr.getBytes() : bytes;
	}

	/**
	 * get list of input files
	 */
	private void getFileList(HttpExchange httpExch) throws Exception {
		// Get the unsigned file names
		respond(httpExch, 200, "text/html", String.join(",", Arrays.asList(inFilesDir.list())).getBytes());
	}

	private String makeJson(String rawJson) throws Exception {
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
		return sb.toString();
	}

	private void handleJsonSign(HttpExchange httpExch, Map<String, String> queryParams) throws Exception {
		String json = makeJson(queryParams.get("json"));

		List<String> filesToUpload = new ArrayList<String>();
		String outFiles;
		boolean multidoc = json.contains("inputs");
		if (multidoc) {
			System.out.println("Multifile");
			json = json.replaceFirst("\\{", "{\n\"bucket\":\"" +s3UserName + "\",\n" +
					"\"password\":\"" + s3Passwd + "\",");

			outFiles = getToken(json, "outFilePath");
			if (outFiles != null && outFiles.isEmpty()) outFiles = null;

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

		createTokenAndRedirect(signValidationSvcUrl + "/signing/getTokenForDocument" + (multidoc ? "s" : ""), json, outFiles, String.join(",", filesToUpload), queryParams, httpExch);
	}

	private void createTokenAndRedirect(String url, String json, String out, String filesToDelete, Map<String, String> queryParams, HttpExchange httpExch) throws Exception {
		System.out.println("JSON for the getToken call:\n" + json);
		String token = postJson(url, json, null);

		System.out.println("  DONE, received token = " + token);

		// 3. Do a redirect to the BOSA DSS front-end
		// Format: https://{host:port}/sign/{token}?redirectURL={callbackURL}&language={language}&name={name}

		System.out.println("\n3. Redirect to the BOSA DSS front-end");
		String callbackURL = localUrl + "/callback?out=" + out + "&toDelete=" + filesToDelete;
		System.out.println("  Callback: " + callbackURL);
		String redirectUrl = bosaDssFrontend + "/sign/" + URLEncoder.encode(token) +
				"?redirectUrl=" + URLEncoder.encode(callbackURL);
		redirectUrl += "&HookURL=" + URLEncoder.encode(localUrl + "/hook");

		for(String key : queryParams.keySet()) {
			if (!key.equals("json")) redirectUrl += "&" + key + "=" + queryParams.get(key);
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
	private void handleCallback(HttpExchange httpExch, Map<String, String> queryParams) throws Exception {

		System.out.println("\n4. Callback");

		String outFiles = queryParams.get("out"); // this one we specified ourselves in handleSign()

		// These params were added by the BOSA DSS/front-end in case of an error
		String ref = queryParams.get("ref");
		String err = queryParams.get("err");
		String details = queryParams.get("details");

		String htmlBody = "";
		if (null == err) {
			// If the signing was successful, download the signed file

			for(String out : outFiles.split(",")) {
				System.out.println("  Trying to downloading file " + out + " from the S3 server");
				MinioClient minioClient = getClient();

				try {
					InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(s3UserName).object(out).build());
					if (!outFilesDir.exists()) outFilesDir.mkdirs();

					File f = new File(outFilesDir, sanitize(out));
					copyStream(stream, Files.newOutputStream(f.toPath()));
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

		if (cleanupTempFiles) {
			String filesToDelete = queryParams.get("toDelete");
			if (!filesToDelete.equals("")) {
				for(String fileToDelete : queryParams.get("toDelete").split(",")) {
					deleteFileFromBucket(fileToDelete);
				}
			}

			for(String fileToDelete : outFiles.split(",")) {
				deleteFileFromBucket(fileToDelete);
				deleteFileFromBucket(fileToDelete + ".validationreport.json");
			}
		}

		// Return a message to the user
		String html = HTML_START + htmlBody + HTML_END;
		respond(httpExch, 200, "text/html", html.getBytes());
	}

	private static void calcPolicyHashes() {
		calcHash("https://Justonweb.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_ANNA_SipENv1.0.pdf", "SHA256");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_ANNA_Test_SipENv1.1.pdf", "SHA256");
		calcHash("https://Justonweb.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_Naban_SipENv1.0.pdf", "SHA256");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_Naban_Test_SipENv1.1.pdf", "SHA256");

		calcHash("https://Justonweb.be/en/legal-documents/justact/eseal-policy/JustAct_Anna_SipENv1.0.pdf", "SHA256");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/justact/eseal-policy/JustAct_Anna_Test_SipENv1.1.pdf", "SHA256");

		calcHash("https://justact.just.fgov.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_ANNA_Test_SipENv1.1.pdf", "SHA256");
		calcHash("", "SHA256");



	}
}
