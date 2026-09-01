package com.bosa.testfps;

import com.nimbusds.jose.JWSAlgorithm;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.credentials.ClientGrantsProvider;
import io.minio.credentials.Jwt;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

import static com.bosa.testfps.Main.config;
import static com.bosa.testfps.Sealing.createOAuthJWT;
import static com.bosa.testfps.Sealing.getAccessToken;
import static com.bosa.testfps.Tools.*;

public class ScratchPad {
	public static void calcPolicyHashes() {
		calcHash("https://epolicies.just.fgov.be/bosa/eseal-policy/eDepot_Bosa_ANNA_SipENv1.1.pdf", "SHA512");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_ANNA_Test_SipENv1.1.pdf", "SHA512");

		calcHash("https://epolicies.just.fgov.be/bosa/eseal-policy/eDepot_Bosa_Naban_SipENv1.1.pdf", "SHA512");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/edepot/eseal-policy/eDepot_Bosa_Naban_Test_SipENv1.1.pdf", "SHA512");

		calcHash("https://epolicies.just.fgov.be/justact/esign-policy/eSignature_policy_justact_fr_v1.1.pdf", "SHA512");
		calcHash("https://epolicies.just.fgov.be/justact/esign-policy/eSignature_policy_justact_nl_v1.1.pdf", "SHA512");

		calcHash("https://epolicies.just.fgov.be/justact/esign-policy/esignature_policy_justact_de_v1.1.pdf", "SHA512");

		calcHash("https://epolicies.just.fgov.be/justact/eseal-policy/JustAct_Anna_SipENv1.1.pdf", "SHA512");
		calcHash("https://justact-acc.just.fgov.be/en/legal-documents/justact/eseal-policy/JustAct_Anna_Test_SipENv1.1.pdf", "SHA512");
	}

	public static void testSignature() throws Exception {

			// 1. The certificate (base64 DER, no PEM headers needed)
			String certB64 = "MIIFCTCCBI+gAwIBAgIUQGSjAtK1JSImhNnAcuxufUGktYMwCgYIKoZIzj0EAwMwgfoxCzAJBgNVBAYTAkJFMREwDwYDVQQHDAhCcnVzc2VsczEwMC4GA1UECgwnS2luZ2RvbSBvZiBCZWxnaXVtIC0gRmVkZXJhbCBHb3Zlcm5tZW50MT0wOwYDVQQLDDRDQS9SQTogRlBTIEhvbWUgQWZmYWlycyAtIEJJSy1HQ0kgKE5UUkJFLTAzNjI0NzU1MzgpMT8wPQYDVQQLDDZRVFNQOiBGUFMgUG9saWN5IGFuZCBTdXBwb3J0IC0gQk9TQSAoTlRSQkUtMDY3MTUxNjY0NykxDzANBgNVBAUTBjIwMjUwMTEVMBMGA1UEAwwMZVNpZ24gQ0EgZUlEMB4XDTI2MDgxMjEyMzkxMFoXDTI2MDgxMzEyMzkwOVoweTELMAkGA1UEBhMCQkUxEDAOBgNVBAQMB1BlZXJlbnMxGzAZBgNVBCoMEkNocmlzdG9waGUgUGF0cmljazEUMBIGA1UEBRMLNzEwMzMxNDE1NzAxJTAjBgNVBAMMHFRFU1QgVVNFUiBDaHJpc3RvcGhlIFBlZXJlbnMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQUHCaB5XAdSUsLPfW/6JjeVSv3ipxLeq3KsRwqYnw4bhdV68ArLY0StxJ0QZx5RGIELQMvtK0IpdYXafHnMXtho4ICcTCCAm0wHwYDVR0jBBgwFoAUM16/vgdjCLyVwrWVVE4Ngbm5sYowUgYIKwYBBQUHAQEERjBEMEIGCCsGAQUFBzAChjZodHRwOi8vY3J0LnBraWFjYy50c3AuemV0ZXMuY29tL2VpZC9lc2lnbmVpZDIwMjUwMS5jcnQwggEzBgNVHSAEggEqMIIBJjAJBgcEAIvsQAECMIIBFwYIYDgNBgUBh2gwggEJMDcGCCsGAQUFBwIBFitodHRwczovL3JlcG9zaXRvcnkucGtpYWNjLnRzcC56ZXRlcy5jb20vZWlkMIHNBggrBgEFBQcCAjCBwAyBvURlIEdla3dhbGlmaWNlZXJkZSB2ZXJsZW5lciB2YW4gdmVydHJvdXdlbnNkaWVuc3RlbiBpcyBGT0QgQk9TQSAvIExlIHByZXN0YXRhaXJlIGRlIHNlcnZpY2VzIGRlIGNvbmZpYW5jZSBxdWFsaWZpw6kgZXN0IFNQRiBCT1NBIC8gRGVuIHF1YWxpZml6aWVydGVuIFZlcnRyYXVlbnNkaWVuc3RlYW5iaWV0ZXIgaXN0IEbDlkQgQk9TQTB2BggrBgEFBQcBAwRqMGgwCAYGBACORgEBMAgGBgQAjkYBBDATBgYEAI5GAQYwCQYHBACORgEGATA9BgYEAI5GAQUwMzAxFitodHRwczovL3JlcG9zaXRvcnkucGtpYWNjLnRzcC56ZXRlcy5jb20vZWlkEwJlbjAdBgNVHQ4EFgQUvFZu/23h2FhcNz9qx2cIIFnsPFIwDgYDVR0PAQH/BAQDAgZAMAkGA1UdOAQCBQAwDQYHBACL7EkCAQQCBQAwCgYIKoZIzj0EAwMDaAAwZQIxAPSMOdDW/fppLQX9z7cIFaaElVoWyRrFKz11Hpo88UQKXAGTJG27nKW9XcDI51YonAIwNEiDNMHyeOS1a6BiAAI9bd6PkLBk6Hx7LjHym1RzOF1zXW64pvN4FI2v0Qi4QGfD";

			// 2. The digest that was signed (base64, raw hash bytes — SHA-256, per hashAlgorithmOID 2.16.840.1.101.3.4.2.1)
			String digestB64 = "ujBSAlSDwtlkE8RyILmAyLA2aN4kolvG6/se41xnWMw=";

			// 3. The signature to verify (base64, DER-encoded ECDSA signature, per signAlgo 1.2.840.10045.4.3.2 = ecdsa-with-SHA256)
			String signatureB64 = "MEQCIHUbKOKwrmXnmqQ4Hi80+oEpkONiTZ32UHSNLEAU+KUOAiBC6L3gerhz59TiYgq+rWG8BNYpKvECP2UeADDTANcA/g==";

			// --- Load the certificate and extract the public key ---
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) cf.generateCertificate(
					new ByteArrayInputStream(Base64.getDecoder().decode(certB64)));
			PublicKey publicKey = cert.getPublicKey();

			System.out.println("Certificate Subject : " + cert.getSubjectX500Principal());
			System.out.println("Public Key Algorithm: " + publicKey.getAlgorithm());

			// --- Decode inputs ---
			byte[] digestBytes = Base64.getDecoder().decode(digestB64);
			byte[] signatureBytes = Base64.getDecoder().decode(signatureB64);

			// --- Verify: NONEwithECDSA because we already have the SHA-256 digest, ---
			// --- not the original message (the JCE won't hash it again for us).    ---
			Signature verifier = Signature.getInstance("NONEwithECDSA");
			verifier.initVerify(publicKey);
			verifier.update(digestBytes);

			boolean valid = verifier.verify(signatureBytes);

			System.out.println("Signature valid: " + valid);

			// Optional sanity check: is the certificate itself currently valid (not expired)?
			try {
				cert.checkValidity();
				System.out.println("Certificate is within its validity period.");
			} catch (CertificateExpiredException | CertificateNotYetValidException e) {
				System.out.println("Certificate validity check failed: " + e.getMessage());
			}
		}

	public static void oauthTest() throws Exception {
		//final String bucket = "keycloak-ta";
		final String bucket = config.getProperty("s3UserName");

		final String minioEndpoint = config.getProperty("dps3Url");

		JWTSigner signer = new ECJWTSignerFromPem(config.getProperty("oauthKeyPair"));
        OAuthInfo fspAuth = new OAuthInfo(null, bucket, config.getProperty("oauthAudience"), JWSAlgorithm.ES256, signer);

		String body = getAccessToken(fspAuth, null, null, config.getProperty("oauthHost"));
		String accessToken = body.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
		String expiresIn = body.replaceAll(".*\"expires_in\":(\\d+).*", "$1");

		Jwt jwt = new Jwt(accessToken, Integer.parseInt(expiresIn));

		ClientGrantsProvider provider = new ClientGrantsProvider(() -> jwt, minioEndpoint, null, null, null);

		MinioClient client = MinioClient.builder()
				.endpoint(minioEndpoint)
				.credentialsProvider(provider)
				.build();
		client.traceOn(System.out);

		try {
			byte[] content = "Hello from client test".getBytes();
			client.putObject(
					PutObjectArgs.builder()
							.bucket(bucket)
							.object("hello.txt")
							.stream(new ByteArrayInputStream(content), content.length, -1)
							.build()
			);
			System.out.println("Fichier créé avec succès.");
		} catch (io.minio.errors.ErrorResponseException e) {
			System.out.println(e.errorResponse().code());
			System.out.println(e.errorResponse().message());
		}
	}
}
