package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSASigner;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.security.*;
import java.security.interfaces.ECPrivateKey;

public class ECJWTSignerFromPem extends JWTSigner {
    final ECPrivateKey privateKey;

    ECJWTSignerFromPem(String pem) throws IOException, NoSuchAlgorithmException {
        Security.addProvider(new BouncyCastleProvider());
        Object parsed = new PEMParser(new StringReader(pem)).readObject();
        if (parsed instanceof PEMKeyPair) {
            privateKey = (ECPrivateKey) new JcaPEMKeyConverter().getPrivateKey(((PEMKeyPair) parsed).getPrivateKeyInfo());
        } else if (parsed instanceof ECPrivateKey) privateKey = (ECPrivateKey) new JcaPEMKeyConverter().getPrivateKey((PrivateKeyInfo)parsed);
        else privateKey = null;
    }

    @Override
    public void sign(JWSObject jwsObject) throws JOSEException {
        jwsObject.sign(new ECDSASigner(privateKey));
    }
}
