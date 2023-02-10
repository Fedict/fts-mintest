package com.bosa.testfps;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.HashMap;
import java.util.Map;

/** See https://github.com/esig/dss.git
 *  dss-sources/dss-enumerations/src/main/java/eu/europa/esig/dss/enumerations/DigestAlgorithm.java */
public enum DigestAlgorithm {

    // see DEPRECATED http://www.w3.org/TR/2012/WD-xmlsec-algorithms-20120105/
    // see http://www.w3.org/TR/2013/NOTE-xmlsec-algorithms-20130411/

    SHA1("SHA1", "SHA-1", "1.3.14.3.2.26", "http://www.w3.org/2000/09/xmldsig#sha1", 20),

    SHA224("SHA224", "SHA-224", "2.16.840.1.101.3.4.2.4", "http://www.w3.org/2001/04/xmldsig-more#sha224", 28),

    SHA256("SHA256", "SHA-256", "2.16.840.1.101.3.4.2.1", "http://www.w3.org/2001/04/xmlenc#sha256", 32),

    SHA384("SHA384", "SHA-384", "2.16.840.1.101.3.4.2.2", "http://www.w3.org/2001/04/xmldsig-more#sha384", 48),

    SHA512("SHA512", "SHA-512", "2.16.840.1.101.3.4.2.3", "http://www.w3.org/2001/04/xmlenc#sha512", 64),

    // see https://tools.ietf.org/html/rfc6931
    SHA3_224("SHA3-224", "SHA3-224", "2.16.840.1.101.3.4.2.7", "http://www.w3.org/2007/05/xmldsig-more#sha3-224", 28),

    SHA3_256("SHA3-256", "SHA3-256", "2.16.840.1.101.3.4.2.8", "http://www.w3.org/2007/05/xmldsig-more#sha3-256", 32),

    SHA3_384("SHA3-384", "SHA3-384", "2.16.840.1.101.3.4.2.9", "http://www.w3.org/2007/05/xmldsig-more#sha3-384", 48),

    SHA3_512("SHA3-512", "SHA3-512", "2.16.840.1.101.3.4.2.10", "http://www.w3.org/2007/05/xmldsig-more#sha3-512", 64),

    RIPEMD160("RIPEMD160", "RIPEMD160", "1.3.36.3.2.1", "http://www.w3.org/2001/04/xmlenc#ripemd160"),

    MD2("MD2", "MD2", "1.2.840.113549.2.2", "http://www.w3.org/2001/04/xmldsig-more#md2"),

    MD5("MD5", "MD5", "1.2.840.113549.2.5", "http://www.w3.org/2001/04/xmldsig-more#md5"),

    WHIRLPOOL("WHIRLPOOL", "WHIRLPOOL", "1.0.10118.3.0.55", "http://www.w3.org/2007/05/xmldsig-more#whirlpool");

    public final String name;
    public final String javaName;
    public final String oid;
    public final String xmlId;
    /* In case of MGF usage */
    public final int saltLength;

    DigestAlgorithm(final String name, final String javaName, final String oid, final String xmlId) {
        this(name, javaName, oid, xmlId, 0);
    }

    DigestAlgorithm(final String name, final String javaName, final String oid, final String xmlId, final int saltLength) {
        this.name = name;
        this.javaName = javaName;
        this.oid = oid;
        this.xmlId = xmlId;
        this.saltLength = saltLength;
    }
}
