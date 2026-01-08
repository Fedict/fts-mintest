package com.bosa.testfps;

import com.sun.net.httpserver.HttpExchange;

import java.util.Base64;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Sealing.*;
import static com.bosa.testfps.Tools.*;

public class PerfTest {

	private static final String PERFTEST_FILE = "perfTest.html";

	static void view(HttpExchange httpExch) throws Exception {
		String refresh = "<meta http-equiv=\"refresh\" content=\"2\">";
		String file = "";
		try {
			file = new String(fileFromMinio(PERFTEST_FILE));
			if (file.endsWith("</BODY>")) {
				deleteMinioFile(PERFTEST_FILE);
				refresh = "";
			}
		} catch (Exception e) {
			file = e.getMessage();
			if ("The specified key does not exist.".equals(file)) file = "Please wait...";
		}
		file = "<html>" + refresh + file + "</html>";
		respond(httpExch, 200, "text/html", file.getBytes());
	}

	static void start(HttpExchange httpExch) throws Exception {
		System.out.println("\n3. Redirect to mintest perfViewer ");
		httpExch.getResponseHeaders().add("Location", "/viewPerf");
		httpExch.sendResponseHeaders(303, 0);
		httpExch.close();

		System.out.println("Running Perf test");
		deleteMinioFile(PERFTEST_FILE);

		try {
			appendToPerfTest("<BODY><TABLE><TR><TD COLSPAN=2 style=\"width: 600px\"><BR><H2>Minio access through (routed) 'https' URLs</H2></TD></TR>");

			setMinioClientURL(false);
			// Small file
			multiUploadDelete("test.pdf", 400);
			// Larger file
			multiUploadDelete("Multi_acroforms.pdf", 200);

			appendToPerfTest("<TR><TD COLSPAN=2><BR><H2>Minio access through 'docker direct' URLs</H2></TD></TR>");
			setMinioClientURL(true);
			// Small file
			multiUploadDelete("test.pdf", 400);
			// Larger file
			multiUploadDelete("Multi_acroforms.pdf", 200);

			// Full Sign roundtrip
			int count = 100;
			appendToPerfTest("<TR><TD COLSPAN=2><BR><H2>Full signature roundtrip with sealing " + count + " times</H2></TD></TR>");
			multiUpload("test.pdf", count);
			// getTokenForDocuments
			String json = "{ \"bucket\":\"" + s3UserName + "\", \"password\":\"" + s3Passwd +
					"\", \"outDownload\": true, \"previewDocuments\": true, \"outPathPrefix\": \"OUT_\", \"signProfile\": \"PADES_MINTEST_SEALING\"," +
					"\"signTimeout\": 9999, \"inputs\": [";
			for(int i = 0; i < count; i++) {
				json += "{ \"filePath\": \"perf-" + i + "-test.pdf\", \"psfC\":\"1,100,100,200,300\"},";
			}
			json = json.substring(0, json.length() - 1);
			json += "]}";

			long time = System.currentTimeMillis();
			String token = postJson(signValidationSvcUrl + "/signing/getTokenForDocuments", json, null);
			appendToPerfTest("<TR><TD>getTokenForDocuments for " + count + " PDFs (with psfC).</TD><TD>" + (System.currentTimeMillis() - time) +  "ms</TD></TR>");

			OAuthInfo oai = FTSSepia;
			String certificateParameters = makeCertificateParameters(getSepiaCerts(oai));

			long getDataToSignTime = 0;
			long signDocumentTime = 0;
			long sealingTime = 0;
			for(int i = 0; i < count; i++) {
				String payLoad = "{\"token\":\"" + token + "\",\"fileIdToSign\":" + i + ",\"clientSignatureParameters\":{\"pdfSigParams\": {}," + certificateParameters;
				time = System.currentTimeMillis();
				String reply = postJson(signValidationSvcUrl + "/signing/getDataToSignForToken", payLoad + "}}", null);
				getDataToSignTime += System.currentTimeMillis() - time;
				String signingDate = getDelimitedValue(reply,"\"signingDate\" : \"", "\"");
				String hashToSign = getDelimitedValue(reply, "\"digest\" : \"", "\",");
				DigestAlgorithm digestAlgo = DigestAlgorithm.valueOf(getDelimitedValue(reply, "digestAlgorithm\" : \"", "\","));

				time = System.currentTimeMillis();
				reply = postJson(sepiaSealingUrl + "/REST/electronicSignature/v1/sign",
						"{ \"signatureLevel\":\"RAW\", \"digest\":\"" + hashToSign + "\", \"digestAlgorithm\":\"" + digestAlgo +
								"\", \"signer\":{\"enterpriseNumber\": " + oai.enterpriseNumber + ",\"certificateAlias\":\"" + oai.rawAlias + "\"}}",
						"Bearer " + oai.access_token);
				sealingTime += System.currentTimeMillis() - time;
				String signedHash = getDelimitedValue(reply, "\"signature\":\"", "\"}");

				time = System.currentTimeMillis();
				reply = postJson(signValidationSvcUrl + "/signing/signDocumentForToken", payLoad + ",\"signingDate\":\"" + signingDate + "\" }, \"signatureValue\":\"" + signedHash + "\"}", null);
				signDocumentTime += System.currentTimeMillis() - time;
			}
			appendToPerfTest("<TR><TD>getDataToSign</TD><TD>" + getDataToSignTime + " ms</TD></TR><TR><TD>sealing</TD><TD>" + sealingTime + " ms</TD></TR><TR><TD>signDocument</TD><TD>" + signDocumentTime + " ms</TD></TR>");

			multiDelete("test.pdf", count);

			count = 20;
			int nbFiles = 20;
			appendToPerfTest("<TR><TD COLSPAN=2><BR><H2>MultiFile Signature(" + nbFiles + " files) with sealing " + count + " times</H2></TD></TR>");

			String fileBase64 = Base64.getEncoder().encodeToString(getDocument(inFilesDir, "test.pdf"));

			getDataToSignTime = signDocumentTime = sealingTime = 0;
			for(int i = 0; i < count ; i++) {
				String payLoad = "{\"toSignDocuments\": [";
				int index = nbFiles;
				while(index != 0) {
					payLoad += "{\"bytes\": \"" + fileBase64 + "\",\"name\": \"test_" + --index + ".pdf\" }";
					if (index != 0) payLoad += ",";
				}
				payLoad += "],\"token\":\"" + System.currentTimeMillis() + "\",\"signingProfileId\":\"XADES_MINTEST_MULTIFILE_SEALING\",\"clientSignatureParameters\":{\"pdfSigParams\": {}," + certificateParameters;

				time = System.currentTimeMillis();
				String reply = postJson(signValidationSvcUrl + "/signing/getDataToSignMultiple", payLoad + "}}", null);
				getDataToSignTime += System.currentTimeMillis() - time;
				String signingDate = getDelimitedValue(reply,"\"signingDate\" : \"", "\"");
				String dataToSign = getDelimitedValue(reply, "\"digest\" : \"", "\",");
				DigestAlgorithm digestAlgo = DigestAlgorithm.valueOf(getDelimitedValue(reply, "digestAlgorithm\" : \"", "\","));

				time = System.currentTimeMillis();
				reply = postJson(sepiaSealingUrl + "/REST/electronicSignature/v1/sign",
						"{ \"signatureLevel\":\"RAW\", \"digest\":\"" + dataToSign + "\", \"digestAlgorithm\":\"" + digestAlgo +
								"\", \"signer\":{\"enterpriseNumber\": " + oai.enterpriseNumber + ",\"certificateAlias\":\"" + oai.rawAlias + "\"}}",
						"Bearer " + oai.access_token);
				sealingTime += System.currentTimeMillis() - time;
				String signedData = getDelimitedValue(reply, "\"signature\":\"", "\"}");

				time = System.currentTimeMillis();
				reply = postJson(signValidationSvcUrl + "/signing/signDocumentMultiple", payLoad + ",\"signingDate\":\"" + signingDate + "\" }, \"signatureValue\":\"" + signedData + "\"}", null);
				signDocumentTime += System.currentTimeMillis() - time;
			}

			appendToPerfTest("<TR><TD>getDataToSignMultiple</TD><TD>" + getDataToSignTime + " ms</TD></TR><TR><TD>sealing</TD><TD>" + sealingTime + " ms</TD></TR><TR><TD>signDocumentMultiple</TD><TD>" + signDocumentTime + " ms</TD></TR>");

			appendToPerfTest("</TABLE></BODY>");
		} catch (Exception e) {
			fileToMinio(PERFTEST_FILE, ("<BODY><H2>Error during the perf Test : " + e.getMessage() + "</H2></BODY>").getBytes());
		}
	}

	private static void appendToPerfTest(String s) throws Exception {
		String fileContent = "";
		try {
			fileContent = new String(fileFromMinio(PERFTEST_FILE));
		} catch(Exception e) { }
		fileToMinio(PERFTEST_FILE, (fileContent + s).getBytes());
	}

	private static void multiUploadDelete(String fileName, int count) throws Exception {
		multiUpload(fileName, count);
		multiDelete(fileName, count);
	}

	private static void multiUpload(String fileName, int count) throws Exception {
		byte[] fileData = getDocument(inFilesDir, fileName);
		appendToPerfTest("<TR><TD>Upload file " + fileName + " ( " + fileData.length + " bytes ) to Minio " + count + " times.</TD>");
		long time = System.currentTimeMillis();
		for(int i = 0; i < count; i++) fileToMinio("perf-" + i + "-" + fileName, fileData);
		appendToPerfTest("<TD>" + (System.currentTimeMillis() - time) + " ms</TD></TR>");
	}

	private static void multiDelete(String fileName, int count) throws Exception {
		appendToPerfTest("<TR><TD>Delete file " + fileName + " from Minio " + count + " times.</TD>");
		long time = System.currentTimeMillis();
		for(int i = 0; i < count; i++) deleteMinioFile("perf-" + i + "-" + fileName);
		appendToPerfTest("<TD>" + (System.currentTimeMillis() - time) + " ms</TD></TR>");
	}
}
