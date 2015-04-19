package prng;

/**
 * Something that can provide seed bytes on demand
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
