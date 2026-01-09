package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class RSAJWTSigner extends JWTSigner {
    final PKCS8EncodedKeySpec keySpec;

    RSAJWTSigner(String key, boolean isPkcs1) {
        byte[] rawKey = Base64.getDecoder().decode(key.replaceAll("\n", ""));

        if (isPkcs1) {
            byte[] temp = new byte[rawKey.length + 26];
            System.arraycopy(Base64.getDecoder().decode("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKY="), 0, temp, 0, 26);
            System.arraycopy(BigInteger.valueOf(temp.length - 4).toByteArray(), 0, temp, 2, 2);
            System.arraycopy(BigInteger.valueOf(rawKey.length).toByteArray(), 0, temp, 24, 2);
            System.arraycopy(rawKey, 0, temp, 26, rawKey.length);
            rawKey = temp;
        }
        keySpec = new PKCS8EncodedKeySpec(rawKey);
    }

    @Override
    public void sign(JWSObject jwsObject) throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        jwsObject.sign(new RSASSASigner(keyFactory.generatePrivate(keySpec)));
    }
}
