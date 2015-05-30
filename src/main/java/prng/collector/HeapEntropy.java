package prng.collector;

import prng.Config;

/**
 * Collect entropy from how objects are allocated on the heap.
 * @author Simon Greatrix
 *
 */
public class HeapEntropy extends EntropyCollector {

    /**
     * Create a collector that uses heap allocation to produce
     * entropy
     * 
     * @param config
     *            configuration for this
     */
    public HeapEntropy(Config config) {
        super(config, 100);
    }
    
    @Override
    protected boolean initialise() {
        return true;
    }

    @Override
    public void run() {
        setEvent(System.identityHashCode(new Object()));
    }
}
