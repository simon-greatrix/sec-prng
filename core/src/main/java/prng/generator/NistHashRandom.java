package prng.generator;

import java.security.MessageDigest;

/**
 * An implementation of the NIST Hash-based Deterministic Random Number
 * Generator as defined in SP800-90A.
 *
 * @author Simon Greatrix
 *
 */
public class NistHashRandom extends BaseRandom {
    /**
     * Implementation built around SHA-1
     *
     * @author Simon Greatrix
     *
     */
    public static class RandomSHA1 extends NistHashRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomSHA1() {
            super(null, HashSpec.SPEC_SHA1, 0, null, null, null);
        }
    }

    /**
     * Implementation built around SHA-256
     *
     * @author Simon Greatrix
     *
     */
    public static class RandomSHA256 extends NistHashRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomSHA256() {
            super(null, HashSpec.SPEC_SHA256, 0, null, null, null);
        }
    }

    /**
     * Implementation built around SHA-512
     *
     * @author Simon Greatrix
     *
     */
    public static class RandomSHA512 extends NistHashRandom {
        /** serial version UID */
        private static final long serialVersionUID = 1l;


        /** New instance */
        public RandomSHA512() {
            super(null, HashSpec.SPEC_SHA512, 0, null, null, null);
        }
    }

    /** Serial version UID */
    private static final long serialVersionUID = 1l;

    /**
     * The "C" Constant parameter as defined in the specification.
     */
    private byte[] constant;

    /**
     * The hash function
     */
    private final MessageDigest digest;

    /** Maximum number of bytes to generate in one iteration */
    private final int maxLength;

    /** Count of number of generator operations */
    private int opCount = 0;

    /** Algorithm parameters */
    private final HashSpec spec;

    /**
     * The "V" Value parameter as defined in the specification.
     */
    private byte[] value;


    /**
     * Create a new deterministic random number generator
     *
     * @param source
     *            entropy source (null means use the default source)
     * @param spec
     *            digest specification (required)
     * @param resistance
     *            number of operations between reseeds. Zero reseeds on every
     *            operation, one reseeds on every alternate operation, and so
     *            on.
     * @param entropy
     *            optional initial entropy
     * @param nonce
     *            an optional nonce
     * @param personalization
     *            an optional personalization value
     */
    public NistHashRandom(SeedSource source, HashSpec spec, int resistance,
            byte[] entropy, byte[] nonce, byte[] personalization) {
        super(source, new InitialMaterial(source,entropy,nonce,personalization,spec.seedLength,spec.seedLength), resistance, spec.seedLength, spec.outputLength);
        this.spec = spec;
        digest = spec.getInstance();

        // The maximum bytes per request is 512Kb. To avoid getting close to
        // that we break up large requests into 128Kb sections. Or at least as
        // close to 128Kb as we can get with complete output blocks.
        int max = 1024 * 128;
        int overflow = max % spec.outputLength;
        if( overflow != 0 ) {
            max += spec.outputLength - overflow;
        }
        maxLength = max;
    }


    /**
     * Increment the value (V parameter) by the amount specified
     *
     * @param incr
     *            the amount to increment by
     */
    private void add(byte[] incr) {
        int len = Math.min(incr.length, value.length);
        int carry = 0;
        int i = 0;
        while( i < len ) {
            int sum = (value[i] & 0xff) + (incr[i] & 0xff) + carry;
            value[i] = (byte) sum;
            i++;
            carry = sum >> 8;
        }
        while( (carry != 0) && (i < value.length) ) {
            int sum = (value[i] & 0xff) + carry;
            value[i] = (byte) sum;
            i++;
            carry = sum >> 8;
        }
    }


    /**
     * Increment the value (V parameter) by the amount specified
     *
     * @param incr
     *            the amount to increment by
     */
    private void add(int incr) {
        byte[] val = new byte[4];
        val[0] = (byte) (incr & 0xff);
        val[1] = (byte) ((incr >> 8) & 0xff);
        val[2] = (byte) ((incr >> 16) & 0xff);
        val[3] = (byte) ((incr >> 26) & 0xff);
        add(val);
    }


    /**
     * The NIST hashDF function which is used to update this PRNGs state with
     * external data.
     *
     * @param zeroPrefix
     *            if true, a zero is prefixed to the material
     * @param material
     *            the hash material
     * @return the generated bytes
     */
    private byte[] hashDF(boolean zeroPrefix, byte[] material) {
        int byteLength = spec.seedLength;
        byte[] output = new byte[byteLength];
        // convert bytes required to 32-bit integer specifying number of bits
        byte[] bitsToReturn = new byte[4];
        bitsToReturn[0] = (byte) ((byteLength << 3) & 0xff);
        bitsToReturn[1] = (byte) ((byteLength >> 5) & 0xff);
        bitsToReturn[2] = (byte) ((byteLength >> 13) & 0xff);
        bitsToReturn[3] = (byte) ((byteLength >> 21) & 0xff);

        int outLen = spec.outputLength;
        int fullLoops = byteLength / outLen;
        int lastSize = byteLength - (fullLoops * outLen);
        int pos = 0;
        // use full output of digest for the majority of the required output
        for(int i = 1;i <= fullLoops;i++) {
            digest.update((byte) i);
            digest.update(bitsToReturn);
            if( zeroPrefix ) {
                digest.update((byte) 0);
            }
            digest.update(material);

            byte[] hash = digest.digest();
            System.arraycopy(hash, 0, output, pos, outLen);
            pos += outLen;
        }

        // use partial output for the final section if required
        if( lastSize != 0 ) {
            digest.update((byte) (fullLoops + 1));
            digest.update(bitsToReturn);
            digest.update(material);
            byte[] hash = digest.digest();
            System.arraycopy(hash, 0, output, pos, byteLength - pos);
        }
        return output;
    }


    /**
     * Generate the next output bytes from this PRNG.
     *
     * @param output
     *            array to place output into
     * @param off
     *            where to start output
     * @param len
     *            amount of bytes to output
     */
    private void implNextBytes(byte[] output, int off, int len) {
        // the NIST "hashgen" function
        int outLen = spec.outputLength;
        int fullLoops = len / outLen;
        int lastSize = len - (fullLoops * outLen);

        byte[] data = value.clone();
        for(int i = 0;i < fullLoops;i++) {
            // hash data to get the next block
            byte[] hash = digest.digest(data);
            System.arraycopy(hash, 0, output, off, outLen);
            off += outLen;

            // increment data by 1
            int j = data.length;
            do {
                j--;
                byte b = data[j];
                b++;
                data[j] = b;
                if( b != 0 ) {
                    break;
                }
            } while( j > 0 );
        }

        // final block
        if( lastSize > 0 ) {
            byte[] hash = digest.digest(data);
            System.arraycopy(hash, 0, output, off, lastSize);
            setSpares(hash, lastSize, hash.length - lastSize);
        }

        // now update this generator's state
        digest.update((byte) 3);
        byte[] hash = digest.digest(value);
        add(hash);
        add(constant);
        add(opCount);
        opCount++;
    }


    @Override
    protected void implNextBytes(int off, byte[] output) {
        // The maximum bytes per request is 512Kb. To avoid getting close to
        // that we break up large requests into 128Kb sections.
        final int maxLen = maxLength;
        int pos = off;
        int required = output.length - off;
        while( required > maxLen ) {
            implNextBytes(output, pos, maxLen);
            pos += maxLen;
            required -= maxLen;
        }
        implNextBytes(output, pos, required);
    }


    @Override
    protected void implSetSeed(byte[] seed) {
        int inputLength = 1 + value.length + seed.length;
        byte[] seedMaterial = new byte[inputLength];
        seedMaterial[0] = (byte) 1;
        System.arraycopy(value, 0, seedMaterial, 1, value.length);
        System.arraycopy(seed, 0, seedMaterial, 1 + value.length, seed.length);

        value = hashDF(false, seedMaterial);
        constant = hashDF(true, value);
    }
}