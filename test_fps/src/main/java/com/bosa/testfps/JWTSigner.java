package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

public class JWTSigner {
    public void sign(JWSObject jwsObject) throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, NoSuchProviderException, IOException {}

    public String getKid() {
        return null;
    }
}
