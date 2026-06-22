package com.bosa.testfps;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;


import java.io.*;
import java.security.*;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import io.minio.*;

import org.json.JSONObject;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Tools.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Sealing {

	// "Low footprint", "down to the bits", freestyle implementation (in the spirit of mintest) of an esal orchestration.
	// We should use proper objects but I'd like to setup a structural solution to importing models from other applications
	// unlike the copy/paste solution used in all the FTS projects

	static void handleJsonSealing(HttpExchange httpExch, Map<String, String> queryParams) throws Exception {
		//http://localhost:8081/seal?inFile=Riddled%20with%20errors.pdf&outFile=out.pdf&profile=PADES_1&lang=en&cred=final_sealing

		String outFilename = sanitize(queryParams.get("outFile"));

		String payLoad;
		String reply;

		Sealer sealer = Sealer.create(queryParams);
		String[] certs = sealer.getCertificates();
		String certificateParameters = makeCertificateParameters(certs);
		String document = getDocumentAsB64(inFilesDir, sanitize(queryParams.get("inFile")));

		payLoad = "{\"clientSignatureParameters\":{" + certificateParameters + "},\"signingProfileId\":\"" + queryParams.get("profile") +
				"\",\"toSignDocument\":{\"bytes\":\"" + document + "\"}}";

		reply = postJson(config.getProperty("sealingSignSvcURL") + "/signing/getDataToSign", payLoad);

		String hashToSign = getDelimitedValue(reply, "\"digest\" : \"", "\",");
		String signingDate = getDelimitedValue(reply,"\"signingDate\" : \"", "\"");
		DigestAlgorithm digestAlgo = DigestAlgorithm.valueOf(getDelimitedValue(reply, "digestAlgorithm\" : \"", "\","));

		String signedHash = sealer.signHash(hashToSign, digestAlgo);

		// Since eSealing is using TEST (see testpki) certificates with their own lifecycles, CRL, no-OCSP, ... influencing revocation freshness
		// We must request a custom policy/constraint to validate those in TA/QA/...
		payLoad = "{\"toSignDocument\":{\"bytes\":\"" + document + "\",\"digestAlgorithm\":null,\"name\":\"RemoteDocument\"},\"signingProfileId\":\"" + queryParams.get("profile") +
				"\",\"clientSignatureParameters\":{" + certificateParameters + ",\"detachedContents\":null,\"signingDate\":\"" + signingDate +
				"\"},\"signatureValue\":\"" + signedHash + "\"}\n";

		reply = postJson(config.getProperty("sealingSignSvcURL") + "/signing/signDocument", payLoad);

		byte[] outDoc = Base64.getDecoder().decode(getDelimitedValue(reply, "\"bytes\" : \"", "\","));
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


	static String makeCertificateParameters(String[] certs) {
		String cert = null;
		int i = certs.length;
		String[] certChain = new String[certs.length - 1];
		while(i-- != 0) {
			cert = "{\"encodedCertificate\":" + certs[i] + "}";
			if(i != 0) certChain[i - 1] = cert;
		}
		return "\"signingCertificate\":" + cert + ",\"certificateChain\":[" + String.join(",", certChain) +"]";
	}

	static String createOAuthJWT(OAuthInfo oai) throws Exception {
		// Create the JWS header,
		long now = new Date().getTime() / 1000;
		String jwtPayload = "{ \"jti\": \"" + now + "\"," +
				"\"iss\": \"" + oai.clientId + "\"," +
				"\"sub\": \"" + oai.clientId + "\"," +
				"\"aud\": \"" + oai.audience + "\"," +
				"\"exp\":" + (now + 1000) + "," +
				"\"nbf\":" + (now - 100) + "," +
				"\"iat\":" + now +
				"}";
		JWSHeader.Builder jwtHdr = new JWSHeader.Builder(oai.algo);
		String kid = oai.signer.getKid();
		if (kid != null) jwtHdr.keyID(kid);
		JWSObject jwsObject = new JWSObject(jwtHdr.build(), new Payload(jwtPayload));

		// Sign the JWS
		oai.signer.sign(jwsObject);

        return jwsObject.serialize();
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

		jweObject.decrypt(new DirectDecrypter(Base64.getDecoder().decode(b64key)));
		String[] parts = jweObject.getPayload().toString().split("\\."); // split out the "parts" (header, payload and signature)

		return new JSONObject(new String(Base64.getDecoder().decode(parts[1])));
	}
}
