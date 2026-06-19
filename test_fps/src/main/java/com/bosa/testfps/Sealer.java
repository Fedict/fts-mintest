package com.bosa.testfps;

import java.util.Map;

import static com.bosa.testfps.Tools.*;

public abstract class Sealer {
    public static Sealer create(Map<String, String> queryParams) {
        String cred = sanitize(queryParams.get("cred"));

        if (cred == null) return new SepiaSealer();

        if (cred.startsWith("ZETES")) return new ZetesSealer();

        String lang = sanitize(queryParams.get("lang"));
        return new FTSSealer(cred, lang);
    }

    abstract String[] getCertificates() throws Exception;

    abstract String signHash(String hashToSign, DigestAlgorithm digestAlgo) throws Exception;
}
