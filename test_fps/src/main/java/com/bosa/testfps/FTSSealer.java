package com.bosa.testfps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.Enumeration;

import static com.bosa.testfps.Main.*;
import static com.bosa.testfps.Main.sadKeyPwd;
import static com.bosa.testfps.Tools.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class FTSSealer extends Sealer {

    private String cred;
    private String lang;

    public FTSSealer(String cred, String lang) {
        this.cred = cred;
        this.lang = lang;
    }

    @Override
    String[] getCertificates() throws Exception {
        String payLoad = "{\"requestID\":\"11668786643409505247592754000\",\"credentialID\":\"" + cred +
                "\",\"lang\":\"" + lang + "\",\"returnCertificates\":\"chain\",\"certInfo\":true,\"authInfo\":true,\"profile\":\"http://uri.etsi.org/19432/v1.1.1/credentialinfoprotocol#\"}";
        String reply = postJson(config.getProperty("ftsSealerSvcURL") + "/credentials/info", payLoad, AUTHORIZATION);

        return getDelimitedValue(reply, "\"certificates\":[", "]").split(",");
    }

    @Override
    String signHash(String hashToSign, DigestAlgorithm digestAlgo) throws Exception {
        Digest digest = new Digest();
        digest.setHashes(new String[] { hashToSign });
        digest.setHashAlgorithmOID(digestAlgo.oid);
        String sad = makeSAD(digest);
        String payLoad = "{\"operationMode\":\"S\",\"requestID\":\"11668768431957487036136225500\"," +
                "\"optionalData\":{\"returnSigningCertificateInfo\":true,\"returnSupportMultiSignatureInfo\":true,\"returnServicePolicyInfo\":true,\"returnSignatureCreationPolicyInfo\":true,\"returnCredentialAuthorizationModeInfo\":true,\"returnSoleControlAssuranceLevelInfo\":true}" +
                ",\"validity_period\":null,\"credentialID\":\"" + cred +
                "\",\"lang\":\"" + lang + "\"," +
                "\"numSignatures\":1,\"policy\":null,\"signaturePolicyID\":null,\"signAlgo\":\"1.2.840.10045.4.3.2\",\"signAlgoParams\":null,\"response_uri\":null,\"documentDigests\":{\"hashes\":[\"" + hashToSign +
                "\"],\"hashAlgorithmOID\":\"" + digestAlgo.oid + "\"},\"sad\":\"" + sad + "\"}";

        String reply = postJson(config.getProperty("ftsSealerSvcURL") + "/signatures/signHash", payLoad, AUTHORIZATION);

        return getDelimitedValue(reply, "\"signatures\":[\"", "\"]}");
    }



    static String makeSAD(Digest documentDigests) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(Files.newInputStream(Paths.get(sadKeyFile)), sadKeyPwd.toCharArray());
        Enumeration<String> aliases = ks.aliases();
        PrivateKey sadSignKey = null;
        X509Certificate sadSignCert = null;
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)
                        ks.getEntry(alias, new KeyStore.PasswordProtection(sadKeyPwd.toCharArray()));
                sadSignKey = entry.getPrivateKey();
                sadSignCert = (X509Certificate) (entry.getCertificateChain())[0];
                break;
            }
        }

        // Serialize the documentDigests to json, this is the JWS header
        ObjectMapper objectMapper = new ObjectMapper();
        StringWriter out = new StringWriter();
        objectMapper.writeValue(out, documentDigests);
        String sadData = out.toString();

        // Create the JWS header,
        // the kid (key id) value = the certificate serial number, hex encoded (no capitals)
        String sadSigSerialNr = sadSignCert.getSerialNumber().toString(16);
        System.out.println("SAD Serial number: " + sadSigSerialNr);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES384).keyID(sadSigSerialNr).build(),
                new Payload(sadData));

        // Sign the JWS
        jwsObject.sign(new ECDSASigner((ECPrivateKey) sadSignKey));
        String sad = jwsObject.serialize();

        return sad;
    }

    static void getCredentials(HttpExchange httpExch) throws Exception {
        byte[] response = null;
        try {
            String json = "{\"requestID\":\"11668764926004483530182899800\",\"lang\":\"en\",\"certificates\":\"chain\",\"certInfo\":false,\"authInfo\":false,\"profile\":\"http://uri.etsi.org/19432/v1.1.1/certificateslistprotocol#\",\"signerIdentity\":null}";

            String reply = postJson(config.getProperty("ftsSealerSvcURL") + "/credentials/list", json, AUTHORIZATION);

            reply = getDelimitedValue(reply, "\"credentialIDs\":[", "]").replaceAll("\"", "");
            System.out.println("Esealing credentials : " + reply);
            response = reply.getBytes(UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("No esealing credential found");
        }
        respond(httpExch, 200, "text/html", response);
    }
}
