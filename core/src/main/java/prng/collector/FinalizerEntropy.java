package prng.collector;

import prng.config.Config;

/**
 * Source entropy from how long it takes to finalize objects.
 * 
 * @author Simon Greatrix
 *
 */
public class FinalizerEntropy extends EntropyCollector {

    /** Number of objects held */
    private final static int HASH_SIZE = 31;

    /**
     * Array where objects are held for a while before they are garbage
     * collected.
     */
    Object[] objects = new Object[HASH_SIZE];

    /**
     * A finalization event. The finalization of this object triggers an entropy
     * event.
     */
    class FinalizationEvent {
        /** When this event was created */
        final long createTime = System.nanoTime();


        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            // event is the number of nano seconds the object existed for and
            // its ID.
            long info = Long.rotateRight(System.nanoTime() - createTime, 32)
                    ^ hashCode();
            setEvent(info);
        }
    }


    public FinalizerEntropy(Config config) {
        super(config, 100);
    }


    @Override
    protected boolean initialise() {
        return true;
    }


    @Override
    protected void runImpl() {
        Object o = new FinalizationEvent();
        int hc = o.hashCode() % HASH_SIZE;
        objects[hc] = o;
    }

}
