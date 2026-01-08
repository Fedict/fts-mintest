package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class RSAJWTSigner extends JWTSigner {
    final PKCS8EncodedKeySpec keySpec;

    RSAJWTSigner(PKCS8EncodedKeySpec keySpec) {
        this.keySpec = keySpec;
    }

    @Override
    public void sign(JWSObject jwsObject) throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        jwsObject.sign(new RSASSASigner(keyFactory.generatePrivate(keySpec)));
    }
}
