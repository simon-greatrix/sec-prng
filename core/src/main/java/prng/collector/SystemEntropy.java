package prng.collector;

import prng.SystemRandom;
import prng.config.Config;

/**
 * Collect entropy from the combined system secure random number generators.
 *
 * @author Simon Greatrix on 02/04/2018.
 */
public class SystemEntropy extends EntropyCollector {

  public SystemEntropy(Config config) {
    super(config, 50);
  }


  @Override
  protected boolean initialise() {
    return true;
  }


  @Override
  protected void runImpl() {
    byte[] data = SystemRandom.getSeed(256);
    post(data);
  }
}
