package prng.generator;

/**
 * Something that can provide seed bytes on demand to a PRNG.
 *
 * @author Simon Greatrix
 */
public interface SeedSource {

  /**
   * Get the name of this source.
   *
   * @return the name
   */
  String getName();


  /**
   * Request seed bytes
   *
   * @param size number of bytes requested
   *
   * @return seed bytes
   */
  byte[] getSeed(int size);

}
