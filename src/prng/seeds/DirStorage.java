package prng.seeds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class DirStorage extends SeedStorage {
    /** Constant used by Fowler-Noll-Vo hash algorithm */
    private static final int FNV_32_INIT = 0x811c9dc5;

    /** Constant used by Fowler-Noll-Vo hash algorithm */
    private static final int FNV_32_PRIME = 0x01000193;

    private static final int MURMUR_SALT_1 = (int) Double.doubleToLongBits(Math.sqrt(2));

    private static final int MURMUR_SALT_2 = (int) Double.doubleToLongBits(Math.sqrt(3));


    private static void append32(StringBuilder buf, int v) {
        for(int i = 0;i < 7;i++) {
            int m = v & 31;
            v >>>= 5;
            if( m < 10 ) {
                buf.append((char) ('0' + m));
            } else {
                // 'W' = 'a' - 10
                buf.append((char) ('W' + m));
            }
        }
    }


    /**
     * 32 bit FNV Hash.
     * 
     * @param data
     *            the data
     * @return the calculated hash
     */
    private static int hashFNV(ByteBuffer data) {
        int rv = FNV_32_INIT;
        final int off = data.position();
        final int len = data.remaining();
        final int end = off + len;
        for(int i = off;i < end;i++) {
            rv ^= 0xff & data.get(i);
            rv *= FNV_32_PRIME;
        }
        return rv;
    }

    private final static int MURMUR_C1 = 0xcc9e2d51;

    private final static int MURMUR_C2 = 0x1b873593;


    /**
     * 32 bit Murmur Hash v3.
     * 
     * @param data
     *            the data
     * @param seed
     *            the seed
     * @return the calculated hash
     */
    private static int hashMurMur3(ByteBuffer data, int seed) {
        data = data.slice();
        int length = data.remaining();
        int hash = seed;

        // process buffer as little-endian integers
        IntBuffer ib = data.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int intLen = length / 4;
        for(int i = 0;i < intLen;i++) {
            int k = Integer.rotateLeft(ib.get(i) * MURMUR_C1, 15) * MURMUR_C2;
            hash ^= k;
            hash = Integer.rotateLeft(hash, 13) * 5 + 0xe6546b64;
        }

        // tail bytes
        int intEnd = 4 * intLen;
        int bytesRem = length - intEnd;
        if( bytesRem > 0 ) {
            int k = 0;
            int s = 1;
            for(int i = intEnd;i < length;i++) {
                k += s * (0xff & data.get(i));
                s <<= 8;
            }
            k = Integer.rotateLeft(k * MURMUR_C1, 15) * MURMUR_C2;
            hash ^= k;
        }

        // finalization
        hash ^= length;
        hash ^= hash >> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >> 16;
        return hash;
    }


    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        // TODO Auto-generated method stub

    }


    @Override
    protected byte[] getRaw(String name) throws StorageException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    protected void remove(String name) {
        // TODO Auto-generated method stub

    }

}
