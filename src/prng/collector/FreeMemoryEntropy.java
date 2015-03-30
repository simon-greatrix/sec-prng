package prng.collector;

import prng.Config;

/**
 * Use the amount of free memory available to the VM at a given time as a source
 * of entropy
 * 
 * @author Simon Greatrix
 *
 */
public class FreeMemoryEntropy extends EntropyCollector {
    public FreeMemoryEntropy(Config config) {
        super(config,100);
    }


    @Override
    protected boolean initialise() {
        return true;
    }


    @Override
    public void run() {
        long memory = Runtime.getRuntime().freeMemory() >> 2;
        setEvent((short) memory);
    }
}