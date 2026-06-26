package com.bosa.testfps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSASigner;
import lombok.Getter;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.util.Base64;


public class ECJWTSignerFromPem extends JWTSigner {
    private ECPrivateKey privateKey;
    private ECPublicKey publicKey;

    @Getter
    private String kid;

    ECJWTSignerFromPem(String pem) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Object parsed = new PEMParser(new StringReader(pem)).readObject();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        if (parsed  instanceof PEMKeyPair) {
            privateKey = (ECPrivateKey) converter.getPrivateKey(((PEMKeyPair) parsed).getPrivateKeyInfo());
            publicKey = (ECPublicKey) converter.getPublicKey(((PEMKeyPair) parsed).getPublicKeyInfo());
        } else throw new Exception("PEM Issue");

        testKeypair(privateKey, publicKey);

        kid = Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));

        System.out.println("Private Key : " + Base64.getEncoder().encodeToString(privateKey.getEncoded()));
        System.out.println("Public Key : " + Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        System.out.println("Kid : " + kid);
    }

    private void testKeypair(ECPrivateKey privateKey, ECPublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
        ecdsaSign.initSign(privateKey);
        byte[] data = "Data to sign".getBytes();
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
};