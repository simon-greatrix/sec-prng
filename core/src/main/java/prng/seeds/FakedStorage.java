package prng.seeds;

import java.nio.charset.StandardCharsets;
import prng.SystemRandom;

/**
 * Faked fallback storage. If you are using this, something has probably gone wrong.
 *
 * @author Simon Greatrix
 */
public class FakedStorage extends SeedStorage {

  @Override
  protected byte[] getRaw(String name) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
    // No record of what size might be expected, so just go with 512 bits.
    byte[] seed = new byte[64];
    SystemRandom.nextBytes(seed);
    return seed;
  }


  @Override
  protected void putRaw(String name, byte[] data) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
    SystemRandom.injectSeed(data);
  }


  @Override
  protected void remove(String name) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
  }

}
