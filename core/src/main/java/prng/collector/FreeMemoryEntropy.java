package prng.collector;

import prng.config.Config;

/**
 * Use the amount of free memory available to the VM at a given time as a source of entropy
 *
 * @author Simon Greatrix
 */
public class FreeMemoryEntropy extends EntropyCollector {

  /**
   * Create a collector that uses the current free memory amount to produce entropy
   *
   * @param config configuration for this
   */
  public FreeMemoryEntropy(Config config) {
    super(config, 100);
  }


  @Override
  protected boolean initialise() {
    return true;
  }


  @Override
  protected void runImpl() {
    long memory = Runtime.getRuntime().freeMemory() >> 2;
    setEvent((short) memory);
  }

}
