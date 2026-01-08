package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ECJWTSigner extends JWTSigner {
    final ECPrivateKey privateKey;
    final String kid;

    ECJWTSigner(String privateKey) throws IOException, NoSuchAlgorithmException {
        Security.addProvider(new BouncyCastleProvider());
        Object parsed = new PEMParser(new StringReader(privateKey)).readObject();
        KeyPair pair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair)parsed);
        this.privateKey = (ECPrivateKey) pair.getPrivate();

        kid = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()));
    }

    @Override
    public void sign(JWSObject jwsObject) throws JOSEException {
        jwsObject.sign(new ECDSASigner(privateKey));
    }

    @Override
    public String getKid() { return kid; }
}
