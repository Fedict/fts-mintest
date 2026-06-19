package com.bosa.testfps;

import com.nimbusds.jose.JWSAlgorithm;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Sealing.createOAuthJWT;
import static com.bosa.testfps.Tools.*;
import static com.bosa.testfps.Tools.getDelimitedValue;

public class SepiaSealer extends Sealer {

    private static final JWTSigner ftsSealSigner = new RSAJWTSigner("""
MIIEogIBAAKCAQEAs3LsYwdpGgs5X57VnSR5WFHDZTgwFnZ//e/DYm8vZv84F4e2
3YFjojqqKUP1tvfbJB4AdydZtlMtoDJax+j4T1k7AyAi8L4/Cat89eVQHQVgfVHK
OLCvT6SouBL85GDs940hKjwF/i1Zi0dAyy++HWsAw9Yzij0x9zbLeDMY8NIP3wmX
66g3xJPw3mjb/Cmxc79pk1drzFMi0cVaBh0XcHkeb4J0Pj4MkK2Gkbf7t9zjZYSR
X82E9HbOaefsTjzqKVMpYOwDCER9NClhbu4Qb5Tn1Z4xa1wSDgqYSWUg2WeHFzXN
hDyDT2Vlw/8hlxt1/0MddViCx16NFssFEOYkfQIDAQABAoIBAD0OCvOen9nmm7y2
9AMlV8v+9bZIqcPaya2CmD2zirNGfrUyzbsLvPSDdUXZA48fQYZGVu4zi0iHgGyS
9WQzFdkZiQSFOJ4kfJozqK6ZOOrG24+H9n/XTa6RXX5Tp4uklrubXv9ZsMhMcbz7
n0YClnK352i6RorwS0HLeOsKp5+3pqyXcr3wrdqJghlGScyZfzB/F+hORS0DRzzI
fgODuNW7afjHMIiw5yt0NNxupvGJx5bj3brI/mfsRiy2uet0r9fbXNChMiv9RVY6
1Oz5CVFnzWcFYch0MYkyoprBFcFYr/AS7UjkV6e/BzC0fRiIKB0iAPm2j3ttPXqV
seI3gFkCgYEA6JJSS0EFAAI2Ho6mMEaoz7B1MLemY91wOZ3CMANPXnrQXXSHYmfN
4kczKGreHz3mTEugmzpestIRI9ccmG0c999afARUKJm03dKJ43Sq+U/8Q7GKhDYA
Ayas7hgEoTcccuLUOmZ3PI1tgw7wDaVlY8C9WPUrCGjUmYzbnrSAuYsCgYEAxYal
oGo2tAOVxZrRWpkbn4qYIGNNIW3KXK1GtVHLfb9l2Moa20gyZlnJuETj+LeVioiM
lcqcbpuaqSlSIOb++0K/YUonoVXiM1oudhqdlDyVglT0DJH8xGxKCOq6ND7o8ubC
u6VRXATqsd7r+MTOhJ/m2TFDfAuvTLN7e9qlixcCgYAtCWC8R+wC82qtgiw2fwhj
p6UZ+QZUomYAEkevaoStJBVDc7Rf3wAkiGsksYUwAZmePqrsRGJgOIOvMBHOhpqs
eWkZSPFPJ2y54/JlxIrzWoTcSv4q2hYohg3I0Yfb/EMbEEfOw1blt/F0Bql/yv6W
UZWZK2jY6Qv6bCd/VS70PwKBgDYMoxOjHLbjaD87HuBIlwtv9DKgmYF1NnNnorqI
2ELfdbH9k52/QrNJDG6Uw0DSk2Pl+3odh/KoN4jkWqnQK6N7Xzzy+qcmBhCBM8dz
fv0KGusf7evmoqDo9NU9zZfwQvP8evq3wOyKF+J2GmHnEI+v5Y428b1mwSAe2MJK
URQfAoGAC+fzkXwkFrf3uTWhLvYvNgzikbM2iYy9xiR0K7MonaBLJYsfrO9ObvVC
T2JvDtEwmg/60pY5ediIc4GMQQhXJNPfBU+D8jORVAtHiaB/0oaciLB7XErxe0/P
koGuKcRtfdtBjwxjKGtnJn7ZqjmT0iqdv1fvzwzQIS1gwZJ6bGU=
			""", true);

    private static final JWTSigner justiceSigner = new RSAJWTSigner("""
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
YsTM+MgdZlTY4GPhDhcwjQhg9n5+Ccw=
					""", false);

    static OAuthInfo FTSSepia = new OAuthInfo("671516647", "fts:bosa:sepia:client:confidential", "https://oauth-v5.acc.socialsecurity.be", JWSAlgorithm.RS256, ftsSealSigner);
    static OAuthInfo JusticeSepia = new OAuthInfo("308357753", "spf-justice:justact:client:confidential", "https://oauth-v5.acc.socialsecurity.be", JWSAlgorithm.RS256, justiceSigner);
    static OAuthInfo oai = FTSSepia;

    @Override
    String[] getCertificates() throws Exception {
        return getSepiaCerts(oai);
    }

    @Override
    String signHash(String hashToSign, DigestAlgorithm digestAlgo) throws Exception {
        String payLoad = "{ \"signatureLevel\":\"RAW\", \"digest\":\"" + hashToSign + "\", \"digestAlgorithm\":\"" + digestAlgo + "\", \"signer\":{\"enterpriseNumber\": " + oai.enterpriseNumber + ",\"certificateAlias\":\"" + oai.signerId + "\"}}";
        String reply = postJson(sepiaSealingUrl + "/REST/electronicSignature/v1/sign", payLoad, "Bearer " + oai.access_token);
        return getDelimitedValue(reply, "\"signature\":\"", "\"}");
    }

    static String[] getSepiaCerts(OAuthInfo oai) throws Exception {
        // Get OAuth access token with a signed JWT
        String payLoad ="grant_type=client_credentials&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=" + createOAuthJWT(oai);
        String reply = postURLEncoded(sepiaSealingUrl + "/REST/oauth/v5/token", payLoad);
        oai.access_token = getDelimitedValue(reply, "\"access_token\":\"", "\",\"scope");
        System.out.println("Access token : " + oai.access_token);

        reply = getJson(sepiaSealingUrl + "/REST/electronicSignature/v1/certificates?enterpriseNumber=" + oai.enterpriseNumber, "Bearer " + oai.access_token);
        String [] certs = reply.split("\\\\n-----END CERTIFICATE-----\\\\n-----BEGIN CERTIFICATE-----\\\\n");
        certs[0] = certs[0].replaceAll("\\{\"certificateChain\":\"-----BEGIN CERTIFICATE-----\\\\n", "");
        oai.signerId = getDelimitedValue(reply, "\"alias\":\"", "\"");
        certs[certs.length - 1] = certs[certs.length - 1].replaceAll("\\\\n-----END CERTIFICATE-----.*", "");
        int i = certs.length;
        while(i-- != 0) certs[i] = "\"" + certs[i] + "\"";
        return certs;
    }
}
