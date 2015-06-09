package prng.generator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    private byte[] constant_;

    /**
     * The hash function
     */
    private final MessageDigest digest_;

    /** Algorithm parameters */
    private final HashSpec spec_;

    /**
     * The "V" Value parameter as defined in the specification.
     */
    private byte[] value_;


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
     * @throws NoSuchAlgorithmException
     */
    public NistHashRandom(SeedSource source, HashSpec spec, int resistance,
            byte[] entropy, byte[] nonce, byte[] personalization) {
        super(source, resistance, spec.seedLength_);
        spec_ = spec;
        digest_ = spec.getInstance();
        byte[] seedMaterial = combineMaterials(entropy, nonce, personalization,
                spec.seedLength_, spec.seedLength_);

        value_ = hashDF(false, seedMaterial);
        constant_ = hashDF(true, value_);
    }


    /**
     * Increment the value (V parameter) by the amount specified
     * 
     * @param incr
     *            the amount to increment by
     */
    private void add(byte[] incr) {
        int len = Math.min(incr.length, value_.length);
        int carry = 0;
        int i = 0;
        while( i < len ) {
            int sum = (value_[i] & 0xff) + (incr[i] & 0xff) + carry;
            value_[i] = (byte) sum;
            i++;
            carry = sum >> 8;
        }
        while( (carry != 0) && (i < value_.length) ) {
            int sum = (value_[i] & 0xff) + carry;
            value_[i] = (byte) sum;
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


    @Override
    protected void implNextBytes(byte[] output) {
        // The maximum bytes per request is 512Kb. To avoid getting close to
        // that we break up large requests into 128Kb sections.
        final int maxLength = 131072;
        int pos = 0;
        int required = output.length;
        while( required > maxLength ) {
            implNextBytes(output, pos, maxLength);
            pos += maxLength;
            required -= maxLength;
        }
        implNextBytes(output, pos, required);
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
        int outLen = spec_.outputLength_;
        int fullLoops = len / outLen;
        int lastSize = len - (fullLoops * outLen);

        byte[] data = value_.clone();
        for(int i = 0;i < fullLoops;i++) {
            // hash data to get the next block
            byte[] hash = digest_.digest(data);
            System.arraycopy(hash, 0, output, off, outLen);
            off += outLen;

            // increment data by 1
            int j = data.length;
            do {
                j--;
                byte b = data[j];
                b++;
                data[j] = b;
                if( b != 0 ) break;
            } while( j > 0 );
        }

        // final block
        if( lastSize > 0 ) {
            byte[] hash = digest_.digest(data);
            System.arraycopy(hash, 0, output, off, lastSize);
        }

        // now update this generator's state
        digest_.update((byte) 3);
        byte[] hash = digest_.digest(value_);
        add(hash);
        add(constant_);
        add(opCount_);
        opCount_++;
    }

    /** Count of number of generator operations */
    private int opCount_ = 0;


    @Override
    protected void implSetSeed(byte[] seed) {
        int inputLength = 1 + value_.length + seed.length;
        byte[] seedMaterial = new byte[inputLength];
        seedMaterial[0] = (byte) 1;
        System.arraycopy(value_, 0, seedMaterial, 1, value_.length);
        System.arraycopy(seed, 0, seedMaterial, 1 + value_.length, seed.length);

        value_ = hashDF(false, seedMaterial);
        constant_ = hashDF(true, value_);
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
        int byteLength = spec_.seedLength_;
        byte[] output = new byte[byteLength];
        // convert bytes required to 32-bit integer specifying number of bits
        byte[] bitsToReturn = new byte[4];
        bitsToReturn[0] = (byte) ((byteLength << 3) & 0xff);
        bitsToReturn[1] = (byte) ((byteLength >> 5) & 0xff);
        bitsToReturn[2] = (byte) ((byteLength >> 13) & 0xff);
        bitsToReturn[3] = (byte) ((byteLength >> 21) & 0xff);

        int outLen = spec_.outputLength_;
        int fullLoops = byteLength / outLen;
        int lastSize = byteLength - (fullLoops * outLen);
        int pos = 0;
        // use full output of digest for the majority of the required output
        for(int i = 1;i <= fullLoops;i++) {
            digest_.update((byte) i);
            digest_.update(bitsToReturn);
            if( zeroPrefix ) digest_.update((byte) 0);
            digest_.update(material);

            byte[] hash = digest_.digest();
            System.arraycopy(hash, 0, output, pos, outLen);
            pos += outLen;
        }

        // use partial output for the final section if required
        if( lastSize != 0 ) {
            digest_.update((byte) (fullLoops + 1));
            digest_.update(bitsToReturn);
            digest_.update(material);
            byte[] hash = digest_.digest();
            System.arraycopy(hash, 0, output, pos, byteLength - pos);
        }
        return output;
    }
}