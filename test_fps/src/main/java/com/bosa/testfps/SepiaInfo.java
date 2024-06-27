package com.bosa.testfps;

public class SepiaInfo {
    final String enterpriseNumber;
    final String sepiaClientId;
    final byte [] privateKey;
    String access_token;
    String rawAlias;

    public SepiaInfo(String enterpriseNumber, String sepiaClientId, byte[] privateKey) {
        this.enterpriseNumber = enterpriseNumber;
        this.sepiaClientId = sepiaClientId;
        this.privateKey = privateKey;
    }
}
