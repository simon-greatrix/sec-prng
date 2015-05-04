package prng;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import prng.collector.EntropyCollector;
import prng.nist.HashSpec;
import prng.nist.NistCipherRandom;
import prng.nist.NistHashRandom;
import prng.nist.NistHmacRandom;
import prng.nist.SeedSource;

/**
 * Implementation of a Fortuna-like secure random number source. Fortuna has
 * many internal pools to collect potential entropy in. As long as some pool
 * collects some entropy, the output becomes unpredictable.
 * <p>
 * 
 * 
 * 
 * @author Simon Greatrix
 *
 */
public class Fortuna {

    /** The singleton instance of Fortuna */
    private static final Fortuna INSTANCE;

    /**
     * Derive entropy from Fortuna
     */
    public static final SeedSource SOURCE = new SeedSource() {
        @Override
        public byte[] getSeed(int bytes) {
            return Fortuna.getSeed(bytes);
        }
    };

    static {
        INSTANCE = new Fortuna();
        EntropyCollector.initialiseStandard();
    }


    /**
     * Create a seed value
     * 
     * @param bytes
     *            the number of bytes required
     * @return the seed value
     */
    public static byte[] getSeed(int bytes) {
        Fortuna instance = INSTANCE;
        synchronized (instance) {
            return instance.randomData(bytes);
        }
    }

    /** A buffer to hold a single block */
    private byte[] blockBuffer_ = new byte[16];

    /** AES with 256-bit key */
    private Cipher cipher_;

    /** An 128-bit counter */
    private byte[] counter_ = new byte[16];

    /** SHA-256 digest */
    private MessageDigest digest_;

    /** A 256-bit cipher key */
    private byte[] key_ = new byte[32];

    /** Entropy accumulators */
    private SecureRandom[] pool_ = new SecureRandom[32];

    /** Number of times this instance has been reseeded. */
    private int reseedCount_ = 0;


    /**
     * Create singleton instance
     */
    private Fortuna() {
        try {
            cipher_ = Cipher.getInstance("AES/ECB/NoPadding");
            digest_ = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException gse) {
            throw new Error("Failed to initialise seed generator", gse);
        }

        // Create the entropy accumulators. These are based on NIST randoms,
        // with system entropy initially.
        byte[] entropy = new byte[128];
        for(int i = 0;i < 32;i++) {
            SystemRandom.nextBytes(entropy);
            SecureRandomSpi spi;
            switch (i % 5) {
            case 0:
            default:
                spi = new NistCipherRandom(SystemRandom.SOURCE, 971, entropy,
                        null, null);
                break;
            case 1:
                spi = new NistHashRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA256, 977, entropy, null, null);
                break;
            case 2:
                spi = new NistHashRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA512, 983, entropy, null, null);
                break;
            case 3:
                spi = new NistHmacRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA256, 991, entropy, null, null);
                break;
            case 4:
                spi = new NistHmacRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA512, 997, entropy, null, null);
                break;
            }
            pool_[i] = new SecureRandomImpl(spi);
        }
    }


    /**
     * Internal function. Generate pseudo random data. The length must be a
     * multiple of 16
     * 
     * @param output
     *            output buffer
     * @param off
     *            start of output in buffer
     * @param len
     *            number of bytes to generate
     */
    private void generateBlocks(byte[] output, int off, int len) {
        try {
            cipher_.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key_, "AES"));
        } catch (InvalidKeyException e) {
            throw new Error("AES cipher rejected key of " + key_.length * 8
                    + " bits");
        }
        for(int pos = 0;pos < len;pos += 16) {
            cipher_.update(counter_);
            try {
                cipher_.doFinal(output, pos);
            } catch (GeneralSecurityException e) {
                throw new Error("Cipher failed", e);
            }
            incrementCounter();
        }
    }


    /**
     * Increment the counter
     */
    private void incrementCounter() {
        for(int i = 0;i < 16;i++) {
            byte b = counter_[i];
            b++;
            counter_[i] = b;
            if( b != 0 ) break;
        }
    }


    /**
     * Generate pseudo random data of the required size
     * 
     * @param len
     *            the required size in bytes
     * @return new random data
     */
    private byte[] pseudoRandomData(int len) {
        byte[] output = new byte[len];
        int runs = len / 1048576;
        int pos = 0;
        // generate at most 2^20=1048576 bytes at a time
        for(int i = 0;i < runs;i++) {
            generateBlocks(output, pos, 1048576);
            generateBlocks(key_, 0, 32);
            pos += 1048576;
        }

        int finalLen = len - pos & 0x7ffffff0;
        if( finalLen > 0 ) {
            generateBlocks(output, pos, finalLen);
            pos += finalLen;
        }

        finalLen = len - pos;
        if( finalLen > 0 ) {
            byte[] buf = blockBuffer_;
            generateBlocks(buf, 0, 16);
            System.arraycopy(buf, 0, output, pos, finalLen);
        }
        generateBlocks(key_, 0, 32);
        return output;
    }


    /**
     * Prepare the generator for the next operation and create random data
     * 
     * @param len
     *            number of bytes required
     * @return random data
     */
    byte[] randomData(int len) {
        reseedCount_++;
        int poolCount = 1;
        int mask = 1;
        while( (mask != 0) && (reseedCount_ & mask) != 0 ) {
            poolCount++;
            mask <<= 1;
        }
        byte[] buf = new byte[32];
        byte[] seed = new byte[poolCount * 32];
        for(int i = 0;i < poolCount;i++) {
            pool_[i].nextBytes(buf);
            System.arraycopy(buf, 0, seed, i * 32, 32);
        }
        reseed(seed);
        return pseudoRandomData(len);
    }


    /**
     * Insert additional seed data into this.
     * 
     * @param input
     *            new seed data
     */
    private void reseed(byte[] input) {
        // derive new key
        digest_.update(key_);
        for(int i = 0;i < 32;i++) {
            key_[i] = 0;
        }
        key_ = digest_.digest(input);

        // increment counter
        incrementCounter();
    }


    /**
     * Add event data into the specified entropy pool
     * 
     * @param pool
     *            the pool's ID
     * @param data
     *            the data
     */
    protected static void addEvent(int pool, byte[] data) {
        Fortuna instance = Fortuna.INSTANCE;
        synchronized (instance) {
            instance.pool_[pool].setSeed(data);
        }
    }
}
