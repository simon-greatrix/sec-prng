package prng;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.concurrent.Callable;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import prng.collector.EntropyCollector;
import prng.generator.BaseRandom;
import prng.generator.HashSpec;
import prng.generator.NistCipherRandom;
import prng.generator.NistHashRandom;
import prng.generator.NistHmacRandom;
import prng.generator.SeedSource;
import prng.internet.NetManager;
import prng.seeds.DeferredSeed;
import prng.seeds.Seed;
import prng.seeds.SeedStorage;

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

    /**
     * Lazily initialise Fortuna to minimise the risk of a
     * circular dependency because ciphers need random numbers
     * but Fortuna needs a cipher.
     */
    private static class InstanceHolder {
        static {
            INSTANCE = new Fortuna();

            // Net manager requires HTTPS, which uses ciphers.
            NetManager.load();

            // Entropy collector uses random numbers
            EntropyCollector.restart();
        }

        /** The singleton instance of Fortuna */
        static final Fortuna INSTANCE;
    }


    /**
     * Derive entropy from Fortuna
     */
    public static final SeedSource SOURCE = new SeedSource() {
        @Override
        public byte[] getSeed(int bytes) {
            return Fortuna.getSeed(bytes);
        }
    };


    /**
     * Get a seed from a random implementation
     * 
     * @author Simon Greatrix
     *
     */
    private static class SeedMaker implements Callable<byte[]> {
        /** The random implementation to make a seed for */
        final SecureRandomImpl impl;


        /**
         * New seed maker
         * 
         * @param impl
         *            the random implementation
         */
        SeedMaker(SecureRandomImpl impl) {
            this.impl = impl;
        }


        @Override
        public byte[] call() {
            return impl.newSeed();
        }
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
        pool = pool & 31;
        Fortuna instance = InstanceHolder.INSTANCE;
        synchronized (instance) {
            SecureRandomImpl impl = instance.pool[pool];
            impl.setSeed(data);
            SeedStorage.enqueue(
                    new DeferredSeed("Fortuna." + pool, new SeedMaker(impl)));
        }
    }


    /**
     * Create a seed value
     * 
     * @param bytes
     *            the number of bytes required
     * @return the seed value
     */
    public static byte[] getSeed(int bytes) {
        Fortuna instance = InstanceHolder.INSTANCE;
        synchronized (instance) {
            EntropyCollector.resetSpeed();
            return instance.randomData(bytes);
        }
    }

    /** A buffer to hold a single block */
    private byte[] blockBuffer = new byte[16];

    /** AES with 256-bit key */
    private Cipher cipher;

    /** An 128-bit counter */
    private byte[] counter = new byte[16];

    /** SHA-256 digest */
    private MessageDigest digest;

    /** A 256-bit cipher key */
    private byte[] key = new byte[32];

    /** Entropy accumulators */
    private SecureRandomImpl[] pool = new SecureRandomImpl[32];

    /** Number of times this instance has been reseeded. */
    private int reseedCount = 0;


    /**
     * Create singleton instance
     */
    private Fortuna() {
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException gse) {
            throw new Error("Failed to initialise seed generator", gse);
        }

        // Create the entropy accumulators. These are based on NIST randoms,
        // with system entropy initially.
        byte[] entropy = new byte[128];
        for(int i = 0;i < 32;i++) {
            SystemRandom.nextBytes(entropy);
            BaseRandom spi;
            switch (i % 5) {
            case 0:
            default:
                spi = new NistCipherRandom(SystemRandom.SOURCE, 5, entropy,
                        null, null);
                break;
            case 1:
                spi = new NistHashRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA256, 5, entropy, null, null);
                break;
            case 2:
                spi = new NistHashRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA512, 5, entropy, null, null);
                break;
            case 3:
                spi = new NistHmacRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA256, 5, entropy, null, null);
                break;
            case 4:
                spi = new NistHmacRandom(SystemRandom.SOURCE,
                        HashSpec.SPEC_SHA512, 5, entropy, null, null);
                break;
            }
            pool[i] = new SecureRandomImpl(spi);
        }

        // use our saved entropy for more buzz!
        try (SeedStorage store = SeedStorage.getInstance()) {
            for(int i = 0;i < 32;i++) {
                Seed seed = store.get("Fortuna." + i);
                if( seed != null ) {
                    pool[i].setSeed(seed.getSeed());
                }
            }
        }

        for(int i = 0;i < 32;i++) {
            SeedStorage.enqueue(
                    new DeferredSeed("Fortuna." + i, new SeedMaker(pool[i])));
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
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        } catch (InvalidKeyException e) {
            throw new Error(
                    "AES cipher rejected key of " + key.length * 8 + " bits");
        }
        for(int pos = 0;pos < len;) {
            try {
                pos += cipher.update(counter, 0, 16, output, pos);
                pos += cipher.doFinal(output, pos);
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
            byte b = counter[i];
            b++;
            counter[i] = b;
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
            generateBlocks(key, 0, 32);
            pos += 1048576;
        }

        int finalLen = len - pos & 0x7ffffff0;
        if( finalLen > 0 ) {
            generateBlocks(output, pos, finalLen);
            pos += finalLen;
        }

        finalLen = len - pos;
        if( finalLen > 0 ) {
            byte[] buf = blockBuffer;
            generateBlocks(buf, 0, 16);
            System.arraycopy(buf, 0, output, pos, finalLen);
        }
        generateBlocks(key, 0, 32);
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
        reseedCount++;
        int poolCount = 1;
        int mask = 1;
        while( (mask != 0) && (reseedCount & mask) != 0 ) {
            poolCount++;
            mask <<= 1;
        }
        byte[] buf = new byte[32];
        byte[] seed = new byte[poolCount * 32];
        for(int i = 0;i < poolCount;i++) {
            pool[i].nextBytes(buf);
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
        digest.update(key);
        for(int i = 0;i < 32;i++) {
            key[i] = 0;
        }
        key = digest.digest(input);

        // increment counter
        incrementCounter();
    }
}
