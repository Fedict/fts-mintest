package com.zetes.projects.bosa.testfps;

import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
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
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
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
		"    <title>FPS test signing service</title>\n  </head>\n  <body>\n";
	private static final String HTML_END = "  </body>\n</html>\n";

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
				e.printStackTrace();
				respond(httpExch, 500, "text/plain", e.toString().getBytes());
			}
			catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	/**
	 * Show a list of docs that can be signed. Typically this will be only 1 doc but here we show more.
	 * To keep the code short, we also use some conventions for the file names:
	 * - if there's a .xslt file with the same name as an .xml file, that .xslt is used to visualise the xls
	 * - if the file starts with 'psf0' then we'll add 'psfN=signature_0' in the getTokenForDocument call
	 *      and we assume the document is a PDF that contains a visible signature field called 'signature_0'
	 * - if the file starts with 'psf1' then we'll add 'coordinates' and a psp to the getTokenForDocument call
	 *      and we'll upload the test1.psp file to the S3 server
	 */
	private void showHomePage(HttpExchange httpExch) throws Exception {
		// Get the unsigned file names
		String[] fileNames = inFilesDir.list();

		// Create an html that contains this filename list
		StringBuilder html = new StringBuilder();
		html.append(HTML_START)
			.append("    <h1>FPS test signing service</h1>\n")
			.append("    <p>Welcome user, select a file to sign:</p>\n")
			.append("    <ul>\n");
		for (String fileName: fileNames) {
			if (fileName.endsWith(".xslt") || fileName.endsWith(".psp"))
				continue; // skip xslt and psp files
			html.append("      <li><a href=\"sign?name=").append(fileName).append("\">").append(fileName);
			if (null != getXslt(fileName))
				html.append(" (with xslt)");
			if (fileName.startsWith("psf0"))
				html.append(" (with PDF visible signature field named '" + PDF_SIG_FIELD_NAME + "')");
			if (fileName.startsWith("psf1"))
				html.append(" (with PDF visible signature field coords '" + PDF_SIG_FIELD_COORDS + "' and profile 'test1.psp')");
			if (fileName.startsWith("psf2"))
				html.append(" (with photo in the PDF visible signature and profile 'test2.psp')");
			html.append("</a>\n");
		}
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

		System.out.println("\nUser wants to sign doc '" + inFileName + "'");

		// 1. Upload the unsigned file to the S3 server
		// Note: this could have been done in advance

		System.out.println("\n1. Uploading the unsigned doc (+ xslt, psp) to the S3 server...");
		uploadFile(new File(inFilesDir, inFileName));

		File xsltFile = getXslt(inFileName);
		if (null != xsltFile) {
			System.out.println("   Uploading the corresponding xslt file to the S3 server...");
			uploadFile(xsltFile);
		}

		if (inFileName.startsWith("psf1") || inFileName.startsWith("psf2")) {
			String pspFileName = inFileName.startsWith("psf1") ? "test1.psp" : "test2.psp";
			File pspFile = new File(inFilesDir, pspFileName);
			System.out.println("   Uploading test1.psp (PDF visible signature profile) to the S3 server...");
			uploadFile(pspFile);
		}

		System.out.println("  DONE");

		// 2. Do a 'getToken' request to the BOSA DSS
		// This is a HTTP POST containing a json

		System.out.println("\n2. Doing a 'getTokenForDocument' to the BOSA DSS");

		String json = "{\n" +
			"  \"name\":\"" + s3UserName + "\",\n" +
			"  \"pwd\":\""  + s3Passwd +   "\",\n" +
			"  \"in\":\""   + inFileName + "\",\n";
		if (null != xsltFile)
			json += ( "  \"xslt\":\""   + xsltFile.getName() + "\",\n" );
		if (inFileName.startsWith("psf0"))
			json += "  \"psfN\":\"" + PDF_SIG_FIELD_NAME + "\",\n";
		if (inFileName.startsWith("psf1")) {
			json += "  \"psfC\":\"" + PDF_SIG_FIELD_COORDS + "\",\n";
			json += "  \"psp\":\"test1.psp\",\n";
		}
		if (inFileName.startsWith("psf2")) {
			json += "  \"psfC\":\"" + PDF_SIG_FIELD_COORDS + "\",\n";
			json += "  \"psfP\":true,\n";
			json += "  \"psp\":\"test2.psp\",\n";
		}
		json += "  \"out\":\""  + outFileName + "\",\n" +
			"  \"lang\":\""  + LANGUAGE + "\",\n" +        // used for the text in PDF visible signatures
			"  \"prof\":\"" + profileFor(inFileName) + "\"\n" +
			"}";
		System.out.println("JSON for the getToken call:\n" + json);
		String token = postJson(getTokenUrl, json);

		System.out.println("  DONE, received token = " + token);

		// 3. Do a redirect to the BOSA DSS front-end
		// Format: https://{host:port}/sign/{token}?redirectURL={callbackURL}&language={language}&name={name}

		System.out.println("\n3. Redirect to the BOSA DSS front-end");
		String callbackURL = localUrl + "/callback?filename=" + outFileName;
		System.out.println("  Callback: " + callbackURL);
		String redirectUrl = bosaDssFrontend + "/sign/" + URLEncoder.encode(token) +
			"?redirectUrl=" + URLEncoder.encode(callbackURL) + "&language=" + LANGUAGE + "&name=" + NAME;
		System.out.println("  URL: " + redirectUrl);
		httpExch.getResponseHeaders().add("Location", redirectUrl);
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();
		System.out.println("  DONE, now waiting till we get a callback...");
	}

	/**
	 * In handleSign(), we specified a callback to this service after the signature process is done.
	 * E.g. /callback?filename=signed_test.pdf&err=1&details=user_cancelled
	 * The first part has been specified completely by us previously, only the 'err' and 'details'
	 * have been added by the caller
	 */
	private void handleCallback(HttpExchange httpExch, String uri) throws Exception {

		System.out.println("\n4. Callback: " + uri);

		// Parse the query parameters
		int idx = uri.indexOf("/callback?") + "/callback?".length();
		String query = uri.substring(idx);
		String[] queryParams = query.split("&");
		String fileName = getParam(queryParams, "filename"); // this one we specified ourselves in handleSign()

		// These params were added by the BOSA DSS/front-end in case of an error
		String ref = getParam(queryParams, "ref");
		String err = getParam(queryParams, "err");
		String details = getParam(queryParams, "details");

		String htmlBody = "";
		if (null == err) {
			// If the signing was successfull, download the signed file

			System.out.println("  Downloading file " + fileName + " from the S3 server");
			MinioClient minioClient = getClient();
			InputStream stream =
				minioClient.getObject(
					GetObjectArgs.builder().bucket(s3UserName).object(fileName).build());

			if (!outFilesDir.exists())
				outFilesDir.mkdirs();

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
		List<DeleteObject> filesToDelete = new LinkedList<DeleteObject>();
		String unsignedFileName = fileName.substring(fileName.indexOf("signed_") + "signed_".length());
		filesToDelete.add(new DeleteObject(unsignedFileName));
		filesToDelete.add(new DeleteObject(fileName));                            // it's OK if this file doesn't exist
		filesToDelete.add(new DeleteObject(fileName + ".validationreport.json")); // it's OK if this file doesn't exist
		File xsltFile = getXslt(unsignedFileName);
		if (null != xsltFile)
			filesToDelete.add(new DeleteObject(xsltFile.getName()));
		// (we could also delete the PSP file if no longer needed)

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

	/**
	 * If 'fileName' is an xml file, then return the corresponding xslt file if it exists.
	 * In this demo service, we assume that the xml and xslt file names only differ by their exention,
	 *  e.g. quotes.xml and quotes.xslt
	 */
	private File getXslt(String fileName) throws Exception {
		if (!fileName.endsWith(".xml"))
			return null;
		String xsltFileName = fileName.substring(0, fileName.length() - 3) + "xslt"; // replace "xml" by "xslt"
		File xsltFile = new File(inFilesDir, xsltFileName);
		return xsltFile.exists() ? xsltFile : null;
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
}
