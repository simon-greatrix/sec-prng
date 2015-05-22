package prng.internet;

import prng.Config;
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
        Class<? extends NetRandom> source_;
        double weight_;
        final WeightedSource next_;
        
        WeightedSource(WeightedSource prev, String className, double weight) throws Exception {
            next_= prev;
            weight_ = weight;
            Class<?> cl = Class.forName(className);
            source_ = cl.asSubclass(NetRandom.class);
            source_.newInstance();
        }
        
        void balance(double total) {
            weight_ /= total;
            if( next_ != null ) next_.balance(total);
        }
        
        NetRandom newInstance(double rand) {
            if( rand > weight_ && next_!=null ) {
                return next_.newInstance(rand-weight_);
            }
            return source_.newInstance();
        }
    }
    
    private final static NetSeed[] SEEDS = new NetSeed[64];
    
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
                NetSeed seed = store.get(NetSeed.class, "NetRandom."+i);
                if( seed==null || seed.isExpired() ) {
                    SEEDS[i] = null;
                } else {
                    SEEDS[i] = seed;
                }
            }
        }
    }

}
