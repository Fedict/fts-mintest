package com.bosa.testfps;

public class Digest {
    private String[] hashes;
    private String hashAlgorithmOID;

    public Digest() {
    }

    /**
     * @param hashes           base64-encoded hash value
     * @param hashAlgorithmOID e.g. "1.3.14.3.2.26" for SHA1, "2.16.840.1.101.3.4.2.1" for SHA256, "2.16.840.1.101.3.4.2.2" for SHA384
     */
    public Digest(String[] hashes, String hashAlgorithmOID) {
        this.hashes = hashes;
        this.hashAlgorithmOID = hashAlgorithmOID;
    }

    public String[] getHashes() {
        return hashes;
    }

    public void setHashes(String[] hashes) {
        this.hashes = hashes;
    }

    public String getHashAlgorithmOID() {
        return hashAlgorithmOID;
    }

    public void setHashAlgorithmOID(String hashAlgorithmOID) {
        this.hashAlgorithmOID = hashAlgorithmOID;
    }
}
