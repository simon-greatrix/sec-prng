package prng.generator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

/**
 * Specification for a Hash based RNG
 */
public class HashSpec {

    /**
     * Specification for SHA-1 based generator. The 440 bit seed length is the
     * maximum number of bytes the SHA-1 algorithm can accept as a single block.
     * The 440 bit input is padded with an 0x80 byte and a 64-bit message length
     * value to create a 512-bit block.
     */
    public static final HashSpec SPEC_SHA1 = new HashSpec("SHA-1", 440, 160,
            128);

    /**
     * Specification for SHA-256 based generator. The 440 bit seed length is the
     * maximum number of bytes the SHA-256 algorithm can accept as a single
     * block. The 440 bit input is padded with an 0x80 byte and a 64-bit message
     * length value to create a 512-bit block.
     */
    public static final HashSpec SPEC_SHA256 = new HashSpec("SHA-256", 440, 256,
            256);

    /**
     * Specification for SHA-512 based generator. The 888 bit seed length is the
     * maximum number of bytes the SHA-512 algorithm can accept as a single
     * block. The 888 bit input is padded with an 0x80 byte and a 128-bit
     * message length value to create a 1024-bit block.
     */
    public static final HashSpec SPEC_SHA512 = new HashSpec("SHA-512", 888, 512,
            256);

    /** Name of the digest algorithm */
    final String algorithm_;

    /** Number of bytes in the digest output */
    final int outputLength_;

    /** Security provider, if needed */
    final Provider provider_;

    /** Number of seed bytes required by the algorithm */
    final int seedLength_;

    /** Effective security strength in bits */
    final int strength_;


    /**
     * Create new algorithm specification
     * 
     * @param provider
     *            Provider implementation, if neeeded
     * @param algorithm
     *            algorithm name
     * @param bitSeedLength
     *            seed length in bits
     * @param bitOutputLength
     *            output length in bits
     * @param strength
     *            security strength in bits
     */
    public HashSpec(Provider provider, String algorithm, int bitSeedLength,
            int bitOutputLength, int strength) {
        provider_ = provider;
        algorithm_ = algorithm;
        seedLength_ = bitSeedLength / 8;
        outputLength_ = bitOutputLength / 8;
        strength_ = strength;
    }


    /**
     * Create new algorithm specification
     * 
     * @param algorithm
     *            algorithm name
     * @param bitSeedLength
     *            seed length in bits
     * @param bitOutputLength
     *            output length in bits
     * @param strength
     *            security strength in bits
     */
    public HashSpec(String algorithm, int bitSeedLength, int bitOutputLength,
            int strength) {
        this(null, algorithm, bitSeedLength, bitOutputLength, strength);
    }


    /**
     * Get an instance of this Hash function
     * 
     * @return a Hash instance
     */
    public MessageDigest getInstance() {
        try {
            if( provider_ == null )
                return MessageDigest.getInstance(algorithm_);
            return MessageDigest.getInstance(algorithm_, provider_);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("Algorithm " + algorithm_ + " not available");
        }
    }
}