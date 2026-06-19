package com.bosa.testfps;

import com.nimbusds.jose.JWSAlgorithm;

import java.net.URLEncoder;
import java.util.Map;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Main.fspAuthAudience;
import static com.bosa.testfps.Main.zetesSealingSigner;
import static com.bosa.testfps.Sealing.createOAuthJWT;
import static com.bosa.testfps.Tools.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ZetesSealer extends Sealer {

    static OAuthInfo FSPAuth = new OAuthInfo(null, fspClientId, fspAuthAudience, JWSAlgorithm.ES256, zetesSealingSigner);

    @Override
    String[] getCertificates() throws Exception {
        String fspAccessToken = getFSPAccessToken(FSPAuth,"service", null);
        String payLoad = """
					{ "certificates":"chain","certInfo":true,"credentialInfo":true}
				""";
        String reply = postJson(fspSealingUrl + "v2/credentials/list", payLoad,
                Map.of("Authorization", "Bearer " + fspAccessToken, "BelGov-Trace-Id", "RANDOM", "X-Message-Priority", "4", "X-SSL-Client-CN", "smoketest", "X-UsageType", "SEALING"));
        FSPAuth.signerId = getDelimitedValue(reply, "\"credentialIDs\":[\"", "\"],");
        return getDelimitedValue(reply, "\"certificates\":[", "],").split(",");
    }

    @Override
    String signHash(String hashToSign, DigestAlgorithm digestAlgo) throws Exception {
        String authDetails = "[{\"type\":\"credential\",\"credentialID\": \"" + FSPAuth.signerId + "\",\"hashAlgorithmOID\":\"2.16.840.1.101.3.4.2.1\", \"documentDigests\":[{\"hash\":\"" + hashToSign + "\"}]}]";
        String fspAccessToken = getFSPAccessToken(FSPAuth,"credential", authDetails);
        String payLoad = "{\"credentialID\":\"" + FSPAuth.signerId + "\",\"hashes\":[\"" + hashToSign + "\"],\"signAlgo\": \"1.2.840.10045.4.3.2\"}";
        String reply = postJson(fspSealingUrl + "v2/signatures/signHash", payLoad,
                Map.of("Authorization", "Bearer " + fspAccessToken, "BelGov-Trace-Id", "RANDOM", "X-Message-Priority", "4", "X-SSL-Client-CN", "smoketest", "X-UsageType", "SEALING"));
        return getDelimitedValue(reply, "\"signatures\":[\"", "\"]}");
    }



    private static String getFSPAccessToken(OAuthInfo oai, String scope, String authorizationDetails) throws Exception {
        String payLoad = "grant_type=client_credentials&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" +
                "&client_assertion=" + createOAuthJWT(oai) +
                "&scope=" + scope;
        if (authorizationDetails != null) payLoad += "&authorization_details=" + URLEncoder.encode(authorizationDetails, UTF_8);
        String reply = postURLEncoded(fspAuthUrl + "token", payLoad);

        String accToken = getDelimitedValue(reply, "\"access_token\":\"", "\",");
        System.out.println("Access token : " + accToken);
        return accToken;
    }

}
