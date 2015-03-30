package prng.nist;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * An implementation of the NIST HMAC-based Deterministic Random Number
 * Generator as defined in SP800-90A.
 * 
 * @author Simon Greatrix
 * 
 */
public class NistHmacRandom extends BaseRandom {
    /**
     * Implementation built around SHA-1
     * 
     * @author Simon Greatrix
     *
     */
    public static class RandomHmacSHA1 extends NistHmacRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomHmacSHA1() {
            super(HashSpec.SPEC_SHA1, 0, null, null, null);
        }
    }

    /**
     * Implementation built around SHA-256
     * 
     * @author Simon Greatrix
     *
     */
    public static class RandomHmacSHA256 extends NistHmacRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomHmacSHA256() {
            super(HashSpec.SPEC_SHA256, 0, null, null, null);
        }
    }

    /**
     * Implementation built around SHA-512
     * 
     * @author Simon Greatrix
     *
     */
    public static class RandomHmacSHA512 extends NistHmacRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomHmacSHA512() {
            super(HashSpec.SPEC_SHA512, 0, null, null, null);
        }
    }

    /** Empty byte array */
    private final static byte[] NO_BYTES = new byte[0];

    /** Serial version UID */
    private static final long serialVersionUID = 1l;


    /**
     * The hash function
     */
    private final MessageDigest digest_;

    /**
     * The "Key" parameter as defined in the specification.
     */
    private byte[] key_;
    

    /** Algorithm parameters */
    private final HashSpec spec_;

    /**
     * The "V" Value parameter as defined in the specification.
     */
    private byte[] value_;


    /**
     * Create a new deterministic random number generator
     * 
     * @param spec
     *            digest specification (required)
     * @param resistance
     *            the number of operations between reseeds
     * @param entropy
     *            the initial entropy
     * @param nonce
     *            an optional nonce
     * @param personalization
     *            an optional personalization value
     * @throws NoSuchAlgorithmException
     */
    public NistHmacRandom(HashSpec spec, int resistance, byte[] entropy,
            byte[] nonce, byte[] personalization) {
        super(resistance,spec.seedLength_);
        spec_ = spec;
        digest_ = spec.getInstance();

        byte[] seedMaterial = combineMaterials(entropy,nonce,personalization,spec.seedLength_,spec.seedLength_);

        key_ = new byte[spec.outputLength_];
        value_ = new byte[spec.outputLength_];
        Arrays.fill(value_, (byte) 1);
        update(seedMaterial);
    }


    @Override
    protected void implNextBytes(byte[] bytes) {
        int off = 0;
        int outLen = spec_.outputLength_;
        int len = bytes.length;
        int fullLoops = len / outLen;
        int lastSize = len - (fullLoops * outLen);

        for(int i = 0;i < fullLoops;i++) {
            value_ = hmac(key_, value_);
            System.arraycopy(value_, 0, bytes, off, outLen);
            off += outLen;
        }

        // final block
        if( lastSize > 0 ) {
            value_ = hmac(key_, value_);
            System.arraycopy(value_, 0, bytes, off, lastSize);
        }

        update(NO_BYTES);
    }


    @Override
    protected void implSetSeed(byte[] seed) {
        if( seed == null ) seed = new byte[0];
        update(seed);
    }


    /**
     * Calculate a HMAC where the message is a single value
     * 
     * @param key
     *            HMAC key
     * @param value
     *            HMAC message
     * @return hmac value
     */
    private byte[] hmac(byte[] key, byte[] value) {
        byte[] ipad = key.clone();
        byte[] opad = key.clone();
        int len = key.length;
        for(int i = 0;i < len;i++) {
            ipad[i] ^= (byte) 0x36;
            opad[i] ^= (byte) 0x5c;
        }

        digest_.update(ipad);
        digest_.update(value);
        byte[] hash = digest_.digest();
        digest_.update(opad);
        digest_.update(hash);
        return digest_.digest();
    }


    /**
     * Calculate a HMAC where the message consists of three parts
     * 
     * @param key
     *            HMAC key
     * @param value
     *            HMAC message part 1
     * @param extra
     *            HMAC message part 2
     * @param message
     *            HMAC message part 3
     * @return hmac value
     */
    private byte[] hmac(byte[] key, byte[] value, byte extra, byte[] message) {
        byte[] ipad = key.clone();
        byte[] opad = key.clone();
        int len = key.length;
        for(int i = 0;i < len;i++) {
            ipad[i] ^= (byte) 0x36;
            opad[i] ^= (byte) 0x5c;
        }

        digest_.update(ipad);
        digest_.update(value);
        digest_.update((byte) 0);
        digest_.update(message);
        byte[] hash = digest_.digest();
        digest_.update(opad);
        digest_.update(hash);
        return digest_.digest();
    }


    /**
     * Update the key and value using the given entropy input
     * 
     * @param entropy
     *            entropy (may be empty)
     */
    private void update(byte[] entropy) {
        key_ = hmac(key_, value_, (byte) 0, entropy);
        value_ = hmac(key_, value_);

        if( entropy.length == 0 ) return;
        key_ = hmac(key_, value_, (byte) 1, entropy);
        value_ = hmac(key_, value_);
    }
}
