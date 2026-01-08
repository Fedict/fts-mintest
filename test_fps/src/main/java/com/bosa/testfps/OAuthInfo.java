package com.bosa.testfps;

import com.nimbusds.jose.JWSAlgorithm;

public class OAuthInfo {
    final String enterpriseNumber;
    final String clientId;
    final String audience;
    final JWTSigner signer;
    final JWSAlgorithm algo;
    String access_token;
    String rawAlias;

    public OAuthInfo(String enterpriseNumber, String clientId, String audience, JWSAlgorithm algo, JWTSigner signer) {
        this.enterpriseNumber = enterpriseNumber;
        this.audience = audience;
        this.clientId = clientId;
        this.signer = signer;
        this.algo = algo;
    }
}
