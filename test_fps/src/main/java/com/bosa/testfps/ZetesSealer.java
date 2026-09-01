package com.bosa.testfps;

import com.nimbusds.jose.JWSAlgorithm;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Sealing.getAccessToken;
import static com.bosa.testfps.Tools.*;

public class ZetesSealer extends Sealer {
    private final OAuthInfo FSPAuth;
    private final String random;

    public ZetesSealer() throws Exception {
        System.out.println("ZetesSealer");

        byte[] randomBytes = new byte[16];
        new SecureRandom().nextBytes(randomBytes);
        random = Base64.getEncoder().encodeToString(randomBytes);

        FSPAuth = new OAuthInfo(null,
                config.getProperty("fspClientId"),
                config.getProperty("fspAuthAudience"),
                JWSAlgorithm.ES256,
                new ECJWTSignerFromPem(config.getProperty("fspSealingKey")));
    }

    @Override
    String[] getCertificates() throws Exception {
        String fspAccessToken = getFSPAccessToken(FSPAuth,"service", null);
        String payLoad = """
					{ "certificates":"chain","certInfo":true,"credentialInfo":true}
				""";
        String reply = postJson(config.getProperty("fspSealingUrl") + "v2/credentials/list", payLoad,
                Map.of("Authorization", "Bearer " + fspAccessToken, "BelGov-Trace-Id", random, "X-UsageType", "SEALING"));
        FSPAuth.signerId = getDelimitedValue(reply, "\"credentialIDs\":[\"", "\"],");
        return getDelimitedValue(reply, "\"certificates\":[", "],").split(",");
    }

    @Override
    String signHash(String hashToSign, DigestAlgorithm digestAlgo) throws Exception {
        String authDetails = "[{\"type\":\"credential\",\"credentialID\": \"" + FSPAuth.signerId + "\",\"hashAlgorithmOID\":\"2.16.840.1.101.3.4.2.1\", \"documentDigests\":[{\"hash\":\"" + hashToSign + "\"}]}]";
        String fspAccessToken = getFSPAccessToken(FSPAuth,"credential", authDetails);
        String payLoad = "{\"credentialID\":\"" + FSPAuth.signerId + "\",\"hashes\":[\"" + hashToSign + "\"],\"signAlgo\": \"1.2.840.10045.4.3.2\"}";
        String reply = postJson(config.getProperty("fspSealingUrl") + "v2/signatures/signHash", payLoad,
                Map.of("Authorization", "Bearer " + fspAccessToken, "BelGov-Trace-Id", random, "X-UsageType", "SEALING"));
        return getDelimitedValue(reply, "\"signatures\":[\"", "\"]}");
    }

    private static String getFSPAccessToken(OAuthInfo oai, String scope, String authorizationDetails) throws Exception {
        String reply = getAccessToken(oai, scope, authorizationDetails, config.getProperty("fspAuthUrl") + "token");
        String accToken = getDelimitedValue(reply, "\"access_token\":\"", "\",");
        System.out.println("Access token : " + accToken);

        return accToken;
    }

}
