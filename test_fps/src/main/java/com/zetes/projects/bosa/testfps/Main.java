package com.zetes.projects.bosa.testfps;

import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.List;
import java.util.LinkedList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.messages.DeleteObject;
import java.util.HashMap;
import java.util.Map;
import javax.activation.MimetypesFileTypeMap;

/**
 * Test/sample code for an FPS service where users (citizens) can sign documents.
 * The flow is as follows:
 * <pre>
 * - the FPS creates an unsigned doc for a user and uploads it to the BOSA S3 server
 * - the FPS obtains a token (a string) for this doc from the BOSA DSS server
 * - the FPS redirects the user to the BOSA DSS front-end server
 * - after a signed doc is created (and put on the BOSA S3 server), the user does a callback to the FPS
 * - the FPS retreives the signed doc from the BOSA S3 server and deletes the unsigned and signed docs
 * </pre>
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

	private static File outFilesDir;

	private static String getTokenUrl;

	private static String bosaDssFrontend;

	private static String localUrl;

	private MinioClient minioClient;

	private static final Map<String, String> sigProfiles = new HashMap<String, String>() {{
            put("application/xml", "XADES_1");
            put("application/pdf", "PADES_1");
        }};
	
	private static final String UNSIGNED_DIR = "unsigned";
	private static final String SIGNED_DIR = "signed";

	private static final String HTML_START =
		"<!DOCTYPE html>\n<html lang=\"en\">\n  <head>\n    <meta charset=\"utf-8\">\n" +
		"    <title>FPS test signing service</title>\n  </head>\n  <body>\n";
	private static final String HTML_END = "  </body>\n</html>\n";

	/* Start of the program */
	public static final void main(String[] args) throws Exception {
		// Read the config file

		Properties config = new Properties();
		config.load(new FileInputStream("config.txt"));

		int port =     Integer.parseInt(config.getProperty("port"));

		s3UserName =   config.getProperty("s3UserName");
		s3Passwd =     config.getProperty("s3Passwd");
		s3Url =        config.getProperty("s3Url");

		filesDir =     new File(config.getProperty("fileDir"));
		outFilesDir =  new File(config.getProperty("outFileDir"));
		if(null == outFilesDir) {
			outFilesDir = new File(filesDir, SIGNED_DIR);
		}

		getTokenUrl =  config.getProperty("getTokenUrl");

		bosaDssFrontend =  config.getProperty("bosaDssFrontend");

		localUrl =     config.getProperty("localUrl");

		// Start the HTTP server

		startService(port);
	}

	/* Start the HTTP server, incomming request will go to the handle() method below */
	public static void startService(int port) throws Exception {
		HttpServer server = HttpServer.create();
		server.bind(new InetSocketAddress(port), 10);
		server.createContext("/", new Main());
		server.start();

		System.out.println("Service started - press Ctrl-C to stop\n");
		System.out.println("Surf with your browser to http://localhost:" + port);
	}
        
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
			String uri = httpExch.getRequestURI().toString();   // e.g. /testpki/crl/citizenca.crl?badSig=true

			if (uri.startsWith("/callback?")) {
				handleCallback(httpExch, uri);
			}
			else if (uri.startsWith("/sign?name=")) {
				handleSign(httpExch, uri);
			}
			else {
				showHomePage(httpExch);
			}
		}
		catch (Exception e) {
			try {
				System.out.println("ERR in Standalone.handle(): " + e.toString());
				respond(httpExch, 500, "text/plain", e.toString().getBytes());
			}
			catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	/** Show a list of docs that can be signed. Normally this will be only 1 doc. */
	private void showHomePage(HttpExchange httpExch) throws Exception {
		// Get the unsigned file names
		File dir = new File(filesDir, UNSIGNED_DIR);
		String[] fileNames = dir.list();

		// Create an html that contains this filename list
		StringBuilder html = new StringBuilder();
		html.append(HTML_START)
			.append("    <h1>FPS test signing service</h1>\n")
			.append("    <p>Welcome user, select a file to sign:</p>\n")
			.append("    <ul>\n");
		for (String fileName: fileNames)
			html.append("      <li><a href=\"sign?name=").append(fileName)
				.append("\">").append(fileName).append("</a>\n");
		html.append("    </ul>\n").append(HTML_END);

		// And return this html
		respond(httpExch, 200, "text/html", html.toString().getBytes());
	}

	/**
	 * The /sign enpoint: the user clicked on a document to sign and got here.
	 * E.g. /sign?name=test.pdf
	 */
	private void handleSign(HttpExchange httpExch, String uri) throws Exception {
		int idx = uri.indexOf("=");
		String inFileName = uri.substring(idx + 1);
		String outFileName = "signed_" + inFileName;

		File dir = new File(filesDir, UNSIGNED_DIR);
		File f = new File(dir, inFileName);

		System.out.println("\nUser wants to sign doc '" + inFileName + "'");

		// 1. Upload the unsigned to to the S3 server
		// Note: this could have been done in advance

		System.out.println("1. Uploading the unsigned doc to the S3 server...");
		MinioClient minioClient = getClient();
		minioClient.uploadObject(
			UploadObjectArgs.builder()
				.bucket(s3UserName)
				.object(inFileName)
				.filename(f.getAbsolutePath())
				.build());
		System.out.println("  DONE");

		// 2. Do a 'getToken' request to the BOSA DSS
		// This is a HTTP POST containing a json

		System.out.println("2. Doing a 'getToken' to the BOSA DSS");
		
		String json = "{\n" +
			"  \"name\":\"" + s3UserName + "\",\n" +
			"  \"pwd\":\""  + s3Passwd +   "\",\n" +
			"  \"in\":\""   + inFileName + "\",\n" +
			"  \"out\":\""  + outFileName + "\",\n" +
			"  \"prof\":\"" + profileFor(inFileName) + "\"\n" +
			"}";
		System.out.println("JSON for the getToken call:\n" + json);
		String token = postJson(getTokenUrl, json);

		System.out.println("  DONE, received token = " + token);

		// 3. Do a redirect to the BOSA DSS front-end

		System.out.println("3. Redirect to the BOSA DSS front-end");
		String callbackURL = localUrl + "/callback?filename=" + outFileName;
		System.out.println("  Callback: " + callbackURL);
		String redirectUrl = bosaDssFrontend + "/sign/" + URLEncoder.encode(token) +
			"?redirectUrl=" + URLEncoder.encode(callbackURL);
		System.out.println("  URL: " + redirectUrl);
		httpExch.getResponseHeaders().add("Location", redirectUrl);
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();
		System.out.println("  DONE, now waiting till we get a callback...\n");
	}

	/**
	 * In handleSign(), we specified a callback to this service after the signature process is done.
	 * E.g. /callback?filename=signed_test.pdf&err=1&details=user_cancelled
	 * The first part has been specified complete by us previously, only the 'err' and 'details'
	 * have been added by the caller
	 */
	private void handleCallback(HttpExchange httpExch, String uri) throws Exception {

		System.out.println("Callback: " + uri);

		// Parse the query parameters
		int idx = uri.indexOf("/callback?") + "/callback?".length();
		String query = uri.substring(idx);
		String[] queryParams = query.split("&");
		String fileName = getParam(queryParams, "filename");
		String err_str = getParam(queryParams, "err");
		int err;
		if(err_str != null) {
			err = Integer.parseInt(getParam(queryParams, "err"));
		} else {
			err = 0;
		}
		String details = getParam(queryParams, "details");
		String ref = getParam(queryParams, "ref");
		System.out.println("  Result: " + err + " = " + err2str(err) +
			(null == details ? "" : ( ", detatils: " + details)) +
			(null == ref ? "" : (", ref: " + ref)));

		String htmlBody = "";
		if (0 == err) {
			// If the signing was successfull, download the signed file

			System.out.println("  Downloading file " + fileName + " from the S3 server");
			MinioClient minioClient = getClient();
			InputStream stream =
				minioClient.getObject(
					GetObjectArgs.builder().bucket(s3UserName).object(fileName).build());

			File f = new File(outFilesDir, fileName);
			FileOutputStream fos = new FileOutputStream(f);
			byte[] buf = new byte[16384];
			int bytesRead;
			while ((bytesRead = stream.read(buf, 0, buf.length)) >= 0)
				fos.write(buf, 0, bytesRead);
			stream.close();
			fos.close();
			System.out.println("    File is downloaded to " + f.getAbsolutePath());

			htmlBody = "Thank you for signing";
		}
		else if (1 == err) {
			htmlBody = "Signing failed\n<br><br>Click <a href=\"/\">here</a> to try again\n";
		}

		// Delete the unsigned and signed docs from the S3 server (it's OK if the signed file doesn't exist)
		List<DeleteObject> filesToDelete = new LinkedList<DeleteObject>();
		String unsignedFileName = fileName.substring(fileName.indexOf("signed_") + "signed_".length());
		filesToDelete.add(new DeleteObject(unsignedFileName));
		filesToDelete.add(new DeleteObject(fileName));

		MinioClient minioClient = getClient();
		minioClient.removeObjects(
			RemoveObjectsArgs.builder().bucket(s3UserName).objects(filesToDelete).build());
		System.out.println("    Unsigned and signed file(s) are deleted from the S3 server");

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
		
	/** Send back response to client */
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

	/** 'param' consist of name=value pairs, we want the value for the requested name */
	private String getParam(String[] params, String name) throws Exception {
		for (String p : params) {
			if (p.startsWith(name))
				return p.substring(p.indexOf("=") + 1);
		}
		return null;
	}

	private static String err2str(int err) {
		switch(err) {
			case 0: return "OK";
			case 1: return "User cancelled";
			case 2: return "Signature time has expired";
			case 3: return "Server error";
		}
		return "Unknown error code";
	}
}
