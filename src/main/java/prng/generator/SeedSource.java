package prng.generator;

/**
 * Something that can provide seed bytes on demand to a PRNG.
 * 
 * @author Simon Greatrix
 *
 */
public interface SeedSource {
    /**
     * Request seed bytes
     * 
     * @param size
     *            number of bytes requested
     * @return seed bytes
     */
    public byte[] getSeed(int size);
}
