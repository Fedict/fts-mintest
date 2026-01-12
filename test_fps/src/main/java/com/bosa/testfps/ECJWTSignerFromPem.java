package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSASigner;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;


public class ECJWTSignerFromPem extends JWTSigner {
    final ECPrivateKey privateKey;

    ECJWTSignerFromPem(String pem) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Security.addProvider(new BouncyCastleProvider());
        Object parsed = new PEMParser(new StringReader(pem)).readObject();
        if (parsed instanceof PEMKeyPair) {
            privateKey = (ECPrivateKey) new JcaPEMKeyConverter().getPrivateKey(((PEMKeyPair) parsed).getPrivateKeyInfo());

            testKeypair(privateKey, (ECPublicKey) new JcaPEMKeyConverter().getPublicKey(((PEMKeyPair) parsed).getPublicKeyInfo()));

        } else if (parsed instanceof PrivateKeyInfo) privateKey = (ECPrivateKey) new JcaPEMKeyConverter().getPrivateKey((PrivateKeyInfo)parsed);
        else privateKey = null;
    }

    private void testKeypair(ECPrivateKey privateKey, ECPublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
        ecdsaSign.initSign(privateKey);
        byte[] data = "Message à signer".getBytes(StandardCharsets.UTF_8);
        ecdsaSign.update(data);

        byte[] signature = ecdsaSign.sign();

        Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
        ecdsaVerify.initVerify(publicKey);
        ecdsaVerify.update(data);
        boolean isValid = ecdsaVerify.verify(signature);

        if (!isValid) throw new SignatureException("Signature verification failed");
    }

    @Override
    public void sign(JWSObject jwsObject) throws JOSEException {
        jwsObject.sign(new ECDSASigner(privateKey));
    }
}
;