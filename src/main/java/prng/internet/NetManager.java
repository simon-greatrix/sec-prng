package prng.internet;

import prng.Config;
import prng.seeds.Seed;
import prng.seeds.SeedStorage;

/**
 * Internet random source manager.
 * 
 * 
 * 
 * @author Simon
 *
 */
public class NetManager {
    
    public static interface Callback {
        public void setSeed(byte[] data);
    }
    
    private static class WeightedSource {
        NetRandom source_;
        double weight_;
        final WeightedSource next_;
        
        WeightedSource(WeightedSource prev, String className, double weight) throws Exception {
            next_= prev;
            weight_ = weight;
            Class<?> cl = Class.forName(className);
            source_ = cl.asSubclass(NetRandom.class).newInstance();
        }
        
        void balance(double total) {
            weight_ /= total;
            if( next_ != null ) next_.balance(total);
        }
        
        NetRandom getInstance(double rand) {
            if( rand > weight_ && next_!=null ) {
                return next_.getInstance(rand-weight_);
            }
            return source_;
        }
    }
    
    private final static Seed[] SEEDS = new Seed[64];
    
    private final static WeightedSource SOURCES;
    
    static {
        Config config = Config.getConfig("network");
        double total = 0;
        config = Config.getConfig("network.source");
        WeightedSource prev = null;
        for(String cl:config) {
            double weight = config.getDouble(cl, 0);
            if( weight <= 0 ) continue;
            try {
                prev = new WeightedSource(prev,cl,weight);
            } catch ( Exception e ) {
                NetRandom.LOG.error("Failed to create internet source \"{}\"",cl,e);
                continue;
            }
            total += weight;
        }
        
        SOURCES = prev;
        if( prev!=null ) {
            prev.balance(total);
        }
        
        try ( SeedStorage store = SeedStorage.getInstance() ) {
            for(int i=0;i<64;i++) {
                SEEDS[i] = store.get("NetRandom."+i);
            }
        }
    }

}
