package com.bosa.testfps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;


import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.minio.*;
import io.minio.errors.*;
import org.json.JSONObject;

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

	private static Boolean cleanupTempFiles;
	private static String s3Url;
	private static String s3UserName;
	private static String s3Passwd;

	private static File filesDir;
	private static File inFilesDir;
	private static File outFilesDir;
	private static String signValidationSvcUrl;
	private static String signValidationUrl;
	private static String idpGuiUrl;
	private static String idpUrl;
	private static String esealingUrl;
	private static String sepiaSealingUrl;

	private static String sadKeyFile;
	private static String sadKeyPwd;
	private static boolean showSealing;
	private static boolean showIDP;

	private static String bosaDssFrontend;

	private static String localUrl;

	private MinioClient minioClient;

	// Default profiles
	private static String XADES_DEF_PROFILE = "XADES_1";
	private static String PADES_DEF_PROFILE = "PADES_1";

	private static final String UNSIGNED_DIR = "unsigned";
	private static final String SIGNED_DIR = "signed";

	private static final String HTML_START =
			"<!DOCTYPE html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n" +
					"<title>FPS test signing service</title>\n  </head>\n  <body>\n";

	private static final String HTML_END = "</body>\n</html>\n";

	// This is defined by the firewall settings, don't change!
	private static int S3_PART_SIZE = 5 * 1024 * 1024;

	// System.out.println("Authorization Basic (selor:test123) =" + Base64.getEncoder().encodeToString("selor:test123".getBytes(StandardCharsets.UTF_8)));
	// System.out.println("Authorization Basic (sealing:123456) =" + Base64.getEncoder().encodeToString("sealing:123456".getBytes(StandardCharsets.UTF_8)));
	private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString("sealing:123456".getBytes(StandardCharsets.UTF_8));

	private static final Map<String, String> sigProfiles = new HashMap<String, String>();

	/** Start of the program */
	public static final void main(String[] args) throws Exception {

		// Read the config file
		Properties config = new Properties();
		config.load(new FileInputStream("config.txt"));

		int port =     Integer.parseInt(config.getProperty("port"));

		String cleanupTempFilesStr = config.getProperty("cleanupTempFiles");
		cleanupTempFiles =  cleanupTempFilesStr != null ? Boolean.valueOf(cleanupTempFilesStr) : true;

		s3UserName		= config.getProperty("s3UserName");
		s3Passwd		= config.getProperty("s3Passwd");
		s3Url			= config.getProperty("s3Url");
		idpGuiUrl		= config.getProperty("idpGuiUrl");
		idpUrl			= config.getProperty("idpUrl");

		sadKeyFile		= config.getProperty("sadKeyFile");
		sadKeyPwd		= config.getProperty("sadKeyPwd");

		esealingUrl		= config.getProperty("easealingUrl");
		sepiaSealingUrl	= config.getProperty("sepiaSealingUrl");

		signValidationSvcUrl =  config.getProperty("getTokenUrl").replace("/signing/getTokenForDocument", "");
		signValidationUrl =  config.getProperty("signValidationUrl");

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
				handleJsonSealing(httpExch, queryParams);
			} else if (uri.startsWith("/getSealingCredentials")) {
				getSealingCredentials(httpExch);
			} else if (uri.startsWith("/idp_")) {
				handleIdp(httpExch, uri, queryParams);
			} else if (uri.startsWith("/getFile")) {
				handleGetFile(httpExch, queryParams);
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

	private void handleSwagger(Map<String, String> queryParams, HttpExchange httpExch) throws IOException {
		String URL = "";
		switch(queryParams.get("to")) {
			case "signval":
				URL = signValidationUrl;
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
		if (certs.length() != 0) json += certs + ",";

		String keyStore = getValidateFileAsJSONField("keystore", queryParams, "keystore");
		if (keyStore.length() != 0) {
			json += keyStore + ",";
			String password = queryParams.get("password");
			if (password != null) json += "\"password\": \"" + password + "\",";
		}

		if (json.endsWith(",")) json = json.substring(0, json.length() -1);
		json += "} }";
		String reply = postJson(signValidationUrl + "/validation/validateSignature", json, null);
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

	private void handleStatic(HttpExchange httpExch, String uri) {
		List<String> tagsToFilter = new ArrayList<>();
		int httpStatus = 200;
		byte[] bytes = null;
		String contentType = "text/plain";

		try {
			uri = uri.substring(1);
			if (uri.length() == 0) {
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

	private void getSealingCredentials(HttpExchange httpExch) throws Exception {
		byte response[] = null;
		try {
			String json = "{\"requestID\":\"11668764926004483530182899800\",\"lang\":\"en\",\"certificates\":\"chain\",\"certInfo\":false,\"authInfo\":false,\"profile\":\"http://uri.etsi.org/19432/v1.1.1/certificateslistprotocol#\",\"signerIdentity\":null}";

			String reply = postJson(esealingUrl + "/credentials/list", json, AUTHORIZATION);

			reply = getDelimitedValue(reply, "\"credentialIDs\":[", "]").replaceAll("\"", "");
			System.out.println("Esealing credentials : " + reply);
			response = reply.getBytes(StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("No esealing credential found");
		}
		respond(httpExch, 200, "text/html", response);
	}

	private static byte fileData[];
	private static String fileName;

	private void handleGetFile(HttpExchange httpExch, Map<String, String> queryParams) throws IOException {
		if (fileData != null) {
			httpExch.getResponseHeaders().add("Content-Type", fileName);
			httpExch.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
			httpExch.getResponseHeaders().add("Content-Transfer-Encoding", "binary");
			httpExch.getResponseHeaders().add("Content-Disposition","attachment; filename=\"" + fileName + "\"");
			httpExch.getResponseHeaders().add("Pragma", "no-cache");
			httpExch.getResponseHeaders().add("Expires", "0");
			httpExch.sendResponseHeaders(200, fileData.length);
			OutputStream os = httpExch.getResponseBody();
			os.write(fileData);
			os.close();
			httpExch.close();
			fileData = null;
		} else respond(httpExch, 200, "text/html", ("File not found !").getBytes());
	}
//	reply = "<script language=\"javascript\">window.onload=function() { document.getElementById('file').click(); } </script>" +
//			"<a id='file' href='/getFile'></a><h1>Sealed document '" + outFilename + "' was downloaded</h1>";


	// "Low footprint", "down to the bits", freestyle implementation (in the spirit of mintest) of an esal orchestration.
	// We should use proper objects but I'd like to setup a structural solution to importing models from other applications
	// unlike the copy/paste solution used in all the FTS projects

	private void handleJsonSealing(HttpExchange httpExch, Map<String, String> queryParams) throws Exception {
		//http://localhost:8081/seal?inFile=Riddled%20with%20errors.pdf&outFile=out.pdf&profile=PADES_1&lang=en&cred=final_sealing

		String outFilename = queryParams.get("outFile");
		String lang = queryParams.get("lang");
		String enterpriseNumber = "671516647"; // 308357753

		String access_token = null;
		String rawAlias = null;
		String payLoad;
		String certs[];
		String reply;
		String cred = queryParams.get("cred");
		if (cred != null) {
			payLoad = "{\"requestID\":\"11668786643409505247592754000\",\"credentialID\":\"" + cred +
					"\",\"lang\":\"" + lang + "\",\"returnCertificates\":\"chain\",\"certInfo\":true,\"authInfo\":true,\"profile\":\"http://uri.etsi.org/19432/v1.1.1/credentialinfoprotocol#\"}";
			reply = postJson(esealingUrl + "/credentials/info", payLoad, AUTHORIZATION);

			certs = getDelimitedValue(reply, "\"certificates\":[", "]").split(",");
		} else {
			// Get OAuth access token with a signed JWT
			payLoad ="grant_type=client_credentials&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=" + createSepiaOAuthJWT();
			reply = postURLEncoded(sepiaSealingUrl + "/REST/oauth/v5/token", payLoad);
			access_token = getDelimitedValue(reply, "\"access_token\":\"", "\",\"scope");
			System.out.println("Access token : " + access_token);

			reply = getJson(sepiaSealingUrl + "/REST/electronicSignature/v1/certificates?enterpriseNumber=" + enterpriseNumber, "Bearer " + access_token);
			certs = reply.split("\\\\n-----END CERTIFICATE-----\\\\n-----BEGIN CERTIFICATE-----\\\\n");
			certs[0] = certs[0].replaceAll("\\{\"certificateChain\":\"-----BEGIN CERTIFICATE-----\\\\n", "");
			rawAlias = getDelimitedValue(reply, "\"alias\":\"", "\"");
			certs[certs.length - 1] = certs[certs.length - 1].replaceAll("\\\\n-----END CERTIFICATE-----.*", "");
			int i = certs.length;
			while(i-- != 0) certs[i] = "\"" + certs[i] + "\"";
		}

		String cert = null;
		int i = certs.length;
		String certChain[] = new String[certs.length - 1];
		while(i-- != 0) {
			cert = "{\"encodedCertificate\":" + certs[i] + "}";
			if(i != 0) certChain[i - 1] = cert;
		}

		String document = getDocumentAsB64(inFilesDir, queryParams.get("inFile"));

		payLoad = "{\"clientSignatureParameters\":{\"signingCertificate\":" + cert +
				",\"certificateChain\":[" + String.join(",", certChain) +"]},\"signingProfileId\":\"" + queryParams.get("profile") +
				"\",\"toSignDocument\":{\"bytes\":\"" + document + "\"}}";

		reply = postJson(signValidationUrl + "/signing/getDataToSign", payLoad, null);

		String hashToSign = getDelimitedValue(reply, "\"digest\" : \"", "\",");
		String signingDate = getDelimitedValue(reply,"\"signingDate\" : \"", "\"");
		DigestAlgorithm digestAlgo = DigestAlgorithm.valueOf(getDelimitedValue(reply, "digestAlgorithm\" : \"", "\","));

		String signedHash = null;
		if (cred != null) {
			Digest digest = new Digest();
			digest.setHashes(new String[] { hashToSign });
			digest.setHashAlgorithmOID(digestAlgo.oid);
			String sad = makeSAD(digest);
			payLoad = "{\"operationMode\":\"S\",\"requestID\":\"11668768431957487036136225500\"," +
					"\"optionalData\":{\"returnSigningCertificateInfo\":true,\"returnSupportMultiSignatureInfo\":true,\"returnServicePolicyInfo\":true,\"returnSignatureCreationPolicyInfo\":true,\"returnCredentialAuthorizationModeInfo\":true,\"returnSoleControlAssuranceLevelInfo\":true}" +
					",\"validity_period\":null,\"credentialID\":\"" + queryParams.get("cred") +
					"\",\"lang\":\"" + lang + "\"," +
					"\"numSignatures\":1,\"policy\":null,\"signaturePolicyID\":null,\"signAlgo\":\"1.2.840.10045.4.3.2\",\"signAlgoParams\":null,\"response_uri\":null,\"documentDigests\":{\"hashes\":[\"" + hashToSign +
					"\"],\"hashAlgorithmOID\":\"" + digestAlgo.oid + "\"},\"sad\":\"" + sad + "\"}";

			reply = postJson(esealingUrl + "/signatures/signHash", payLoad, AUTHORIZATION);

			signedHash = getDelimitedValue(reply, "\"signatures\":[\"", "\"]}");
		} else {
			payLoad = "{ \"signatureLevel\":\"RAW\", \"digest\":\"" + hashToSign + "\", \"digestAlgorithm\":\"" + digestAlgo + "\", \"signer\":{\"enterpriseNumber\": " + enterpriseNumber + ",\"certificateAlias\":\"" + rawAlias + "\"}}";
			reply = postJson(sepiaSealingUrl + "/REST/electronicSignature/v1/sign", payLoad, "Bearer " + access_token);
			signedHash = getDelimitedValue(reply, "\"signature\":\"", "\"}");
		}

		// Since eSealing is using TEST (see testpki) certificates with their own lifecycles, CRL, no-OCSP, ... influencing revocation freshness
		// We must request a custom policy/constraint to validate those in TA/QA/...
		String validatePolicy = getDocumentAsB64(filesDir, "esealingConstraint.xml");
		payLoad = "{\"toSignDocument\":{\"bytes\":\"" + document + "\",\"digestAlgorithm\":null,\"name\":\"RemoteDocument\"},\"signingProfileId\":\"" + queryParams.get("profile") +
				"\",\"clientSignatureParameters\":{\"signingCertificate\":" + cert +
				",\"certificateChain\":[" + String.join(",", certChain) + "],\"detachedContents\":null,\"signingDate\":\"" + signingDate +
				"\"},\"signatureValue\":\"" + signedHash + "\", \"validatePolicy\": { \"bytes\": \"" + validatePolicy + "\"}}\n";

		reply = postJson(signValidationUrl + "/signing/signDocument", payLoad, null);

		byte outDoc[] = Base64.getDecoder().decode(getDelimitedValue(reply, "\"bytes\" : \"", "\","));
		String outDocString = new String(outDoc);

		String displayAs = queryParams.get("displayAs");
		if ("ascii".equals(displayAs)) {
			reply = "<HTML><pre style=\"white-space: pre-wrap; word-wrap: break-word;\">" + outDocString + "</pre></HTML>";
		} else  if ("JWT".equals(displayAs)) {
			reply = "<HTML><pre style=\"white-space: pre-wrap; word-wrap: break-word;\">" + toStringJWT(outDocString) + "</pre></HTML>";
		} else  if ("JSON".equals(displayAs)) {
			reply = "<HTML><pre style=\"white-space: pre-wrap; word-wrap: break-word;\">" + toStringJSON(outDocString) + "</pre></HTML>";
		} else {
			fileData = outDoc;
			fileName = outFilename;

			reply = "<script language=\"javascript\">window.onload=function() { document.getElementById('file').click(); } </script>" +
					"<a id='file' href='/getFile'></a><h1>Sealed document '" + outFilename + "' was downloaded</h1>";
		}

		respond(httpExch, 200, "text/html", reply.getBytes());
	}

	private static byte[] buildPkcs8KeyFromPkcs1Key(byte[] innerKey) {
		byte result[] = new byte[innerKey.length + 26];
		System.arraycopy(Base64.getDecoder().decode("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKY="), 0, result, 0, 26);
		System.arraycopy(BigInteger.valueOf(result.length - 4).toByteArray(), 0, result, 2, 2);
		System.arraycopy(BigInteger.valueOf(innerKey.length).toByteArray(), 0, result, 24, 2);
		System.arraycopy(innerKey, 0, result, 26, innerKey.length);
		return result;
	}

	private static byte ftsSealPrivateKey[] = buildPkcs8KeyFromPkcs1Key(Base64.getDecoder().decode("MIIEogIBAAKCAQEAs3LsYwdpGgs5X57VnSR5WFHDZTgwFnZ//e/DYm8vZv84F4e2" +
			"3YFjojqqKUP1tvfbJB4AdydZtlMtoDJax+j4T1k7AyAi8L4/Cat89eVQHQVgfVHK" +
			"OLCvT6SouBL85GDs940hKjwF/i1Zi0dAyy++HWsAw9Yzij0x9zbLeDMY8NIP3wmX" +
			"66g3xJPw3mjb/Cmxc79pk1drzFMi0cVaBh0XcHkeb4J0Pj4MkK2Gkbf7t9zjZYSR" +
			"X82E9HbOaefsTjzqKVMpYOwDCER9NClhbu4Qb5Tn1Z4xa1wSDgqYSWUg2WeHFzXN" +
			"hDyDT2Vlw/8hlxt1/0MddViCx16NFssFEOYkfQIDAQABAoIBAD0OCvOen9nmm7y2" +
			"9AMlV8v+9bZIqcPaya2CmD2zirNGfrUyzbsLvPSDdUXZA48fQYZGVu4zi0iHgGyS" +
			"9WQzFdkZiQSFOJ4kfJozqK6ZOOrG24+H9n/XTa6RXX5Tp4uklrubXv9ZsMhMcbz7" +
			"n0YClnK352i6RorwS0HLeOsKp5+3pqyXcr3wrdqJghlGScyZfzB/F+hORS0DRzzI" +
			"fgODuNW7afjHMIiw5yt0NNxupvGJx5bj3brI/mfsRiy2uet0r9fbXNChMiv9RVY6" +
			"1Oz5CVFnzWcFYch0MYkyoprBFcFYr/AS7UjkV6e/BzC0fRiIKB0iAPm2j3ttPXqV" +
			"seI3gFkCgYEA6JJSS0EFAAI2Ho6mMEaoz7B1MLemY91wOZ3CMANPXnrQXXSHYmfN" +
			"4kczKGreHz3mTEugmzpestIRI9ccmG0c999afARUKJm03dKJ43Sq+U/8Q7GKhDYA" +
			"Ayas7hgEoTcccuLUOmZ3PI1tgw7wDaVlY8C9WPUrCGjUmYzbnrSAuYsCgYEAxYal" +
			"oGo2tAOVxZrRWpkbn4qYIGNNIW3KXK1GtVHLfb9l2Moa20gyZlnJuETj+LeVioiM" +
			"lcqcbpuaqSlSIOb++0K/YUonoVXiM1oudhqdlDyVglT0DJH8xGxKCOq6ND7o8ubC" +
			"u6VRXATqsd7r+MTOhJ/m2TFDfAuvTLN7e9qlixcCgYAtCWC8R+wC82qtgiw2fwhj" +
			"p6UZ+QZUomYAEkevaoStJBVDc7Rf3wAkiGsksYUwAZmePqrsRGJgOIOvMBHOhpqs" +
			"eWkZSPFPJ2y54/JlxIrzWoTcSv4q2hYohg3I0Yfb/EMbEEfOw1blt/F0Bql/yv6W" +
			"UZWZK2jY6Qv6bCd/VS70PwKBgDYMoxOjHLbjaD87HuBIlwtv9DKgmYF1NnNnorqI" +
			"2ELfdbH9k52/QrNJDG6Uw0DSk2Pl+3odh/KoN4jkWqnQK6N7Xzzy+qcmBhCBM8dz" +
			"fv0KGusf7evmoqDo9NU9zZfwQvP8evq3wOyKF+J2GmHnEI+v5Y428b1mwSAe2MJK" +
			"URQfAoGAC+fzkXwkFrf3uTWhLvYvNgzikbM2iYy9xiR0K7MonaBLJYsfrO9ObvVC" +
			"T2JvDtEwmg/60pY5ediIc4GMQQhXJNPfBU+D8jORVAtHiaB/0oaciLB7XErxe0/P" +
			"koGuKcRtfdtBjwxjKGtnJn7ZqjmT0iqdv1fvzwzQIS1gwZJ6bGU="));

	private String createSepiaOAuthJWT() throws Exception {
		// Create the JWS header,
		long now = new Date().getTime() / 1000;
		String jwtPayload = "{ \"jti\": \"" + now + "\"," +
				"\"iss\": \"fts:bosa:sepia:client:confidential\"," +
				"\"sub\": \"fts:bosa:sepia:client:confidential\"," +
				"\"aud\": \"https://oauth-v5.acc.socialsecurity.be\"," +
				"\"exp\":" + (now + 1000) + "," +
				"\"nbf\":" + (now - 100) + "," +
				"\"iat\":" + now +
				"}";
		JWSObject jwsObject = new JWSObject(
				new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
				new Payload(jwtPayload));

		// Sign the JWS
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(ftsSealPrivateKey);

		jwsObject.sign(new RSASSASigner(keyFactory.generatePrivate(keySpec)));
		String jwt = jwsObject.serialize();

		return jwt;
	}

	protected String makeSAD(Digest documentDigests) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(new FileInputStream(sadKeyFile), sadKeyPwd.toCharArray());
		Enumeration<String> aliases = ks.aliases();
		PrivateKey sadSignKey = null;
		X509Certificate sadSignCert = null;
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			if (ks.isKeyEntry(alias)) {
				KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)
						ks.getEntry(alias, new KeyStore.PasswordProtection(sadKeyPwd.toCharArray()));
				sadSignKey = entry.getPrivateKey();
				sadSignCert = (X509Certificate) (entry.getCertificateChain())[0];
				break;
			}
		}

		// Serialize the documentDigests to json, this is the JWS header
		ObjectMapper objectMapper = new ObjectMapper();
		StringWriter out = new StringWriter();
		objectMapper.writeValue(out, documentDigests);
		String sadData = out.toString();

		// Create the JWS header,
		// the kid (key id) value = the certificate serial number, hex encoded (no capitals)
		String sadSigSerialNr = sadSignCert.getSerialNumber().toString(16);
		System.out.println("SAD Serial number: " + sadSigSerialNr);

		JWSObject jwsObject = new JWSObject(
				new JWSHeader.Builder(JWSAlgorithm.ES384).keyID(sadSigSerialNr).build(),
				new Payload(sadData));

		// Sign the JWS
		jwsObject.sign(new ECDSASigner((ECPrivateKey) sadSignKey));
		String sad = jwsObject.serialize();

		return sad;
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

		createTokenAndRedirect(signValidationSvcUrl + "/signing/getTokenForDocument" + (multidoc ? "s" : ""), json, outFiles, String.join(",", filesToUpload), queryParams, httpExch);
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

	private static String sanitize(String path) {
		return path.replaceAll("/", "");
	}

	private void deleteFileFromBucket(String fileToDelete) throws Exception {
		fileToDelete = sanitize(fileToDelete);
		System.out.println("    Deleting from the S3 server :" + fileToDelete);
		minioClient.removeObject(RemoveObjectArgs.builder().bucket(s3UserName).object(fileToDelete).build());
	}

	/** Do an HTTP POST of a json (a REST call) */
	private static String postURLEncoded(String urlStr, String json) throws IOException {
		return postRaw(false, urlStr, json, null, "application/x-www-form-urlencoded");
	}

	private static String postJson(String urlStr, String json, String authorization) throws IOException {
		return postRaw(false, urlStr, json, authorization, null);
	}

	private static String getJson(String urlStr, String authorization) throws IOException {
		return postRaw(true, urlStr, null, authorization, null);
	}

	private static String postRaw(boolean isGet, String urlStr, String json, String authorization, String contentType) throws IOException {
		System.out.println("Request from " + urlStr + " :" + json);
		HttpURLConnection urlConn = null;
		try {
			URL url = new URL(urlStr);

			urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestMethod(isGet ? "GET" : "POST");
			if (authorization != null) urlConn.setRequestProperty("Authorization", authorization);

			urlConn.setRequestProperty("Content-Type", contentType == null ? "application/json; utf-8" : contentType);
			if (!isGet) {
				urlConn.setDoOutput(true);
				OutputStream os = urlConn.getOutputStream();
				os.write(json.getBytes("utf-8"));
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

	private void uploadFiles(List<String> filePaths) throws Exception {
		for(String filePath : filePaths) uploadFile(filePath);
	}

	private void uploadFile(String fileName) throws Exception {

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

	private static String mimeTypeFor(String filename) {
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
	private String profileFor(String filename) {
		String type = mimeTypeFor(filename);
		return sigProfiles.containsKey(type) ? sigProfiles.get(type) : "CADES_1";
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

	private static String toStringJWT(String rawJwt) {
		String bits[] = rawJwt.split("\\.");
		return "HEADER: " + toStringB64JSON(bits[0]) + "\nPAYLOAD: " + toStringB64JSON(bits[1]) + "\nSIGNATURE: " + bits[2];
	}

	private static String toStringB64JSON(String b64) {
		return new JSONObject(new String(Base64.getDecoder().decode(b64))).toString(4);
	}

	private static String toStringJSON(String json) {
		return new JSONObject(json).toString(4);
	}

	private static String streamToString(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(4096*4);
		copyStream(in, baos);
		baos.close();
		in.close();
		return new String(baos.toByteArray());
	}

	private static String getDocumentAsB64(File folder, String inFile) throws IOException {
		return Base64.getEncoder().encodeToString(getDocument(folder, inFile));
	}
	private static String getDocumentAsString(File folder, String inFile) throws IOException {
		return streamToString(new FileInputStream(new File(folder, inFile)));
	}
	private static byte[] getDocument(File folder, String inFile) throws IOException {
		FileInputStream fis = new FileInputStream(new File(folder, sanitize(inFile)));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		copyStream(fis, baos);
		baos.close();
		fis.close();
		return baos.toByteArray();
	}

	private static void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[16384];
		int bytesRead;
		while ((bytesRead = in.read(buf, 0, buf.length)) >= 0)
			out.write(buf, 0, bytesRead);
		in.close();
		out.close();
	}

	private static String getDelimitedValue(String str, String beginMark, String endMark) throws Exception {
		int pos = str.indexOf(beginMark);
		if (pos < 0) throw new Exception("No " + beginMark + " ?");
		pos += beginMark.length();
		int endPos = str.indexOf(endMark, pos);
		if (endPos < 0) throw new Exception("No "+ endMark + " after " + beginMark + " ?");
		return str.substring(pos, endPos);
	}

}
