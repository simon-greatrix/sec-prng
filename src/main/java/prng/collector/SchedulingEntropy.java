package prng.collector;

import prng.util.Config;

/**
 * Use the amount of time between calls as a source of entropy
 * 
 * @author Simon Greatrix
 *
 */
public class SchedulingEntropy extends EntropyCollector {
    /** Last time this was called */
    private long lastTime_ = System.nanoTime();


    /**
     * Create a collector that uses scheduling accuracy to produce entropy
     * 
     * @param config
     *            configuration for this
     */
    public SchedulingEntropy(Config config) {
        super(config, 50);
    }


    @Override
    protected boolean initialise() {
        return true;
    }


    @Override
    protected void runImpl() {
        long now = System.nanoTime();
        long diff = now - lastTime_;
        lastTime_ = now;
        setEvent((short) diff);
    }
}