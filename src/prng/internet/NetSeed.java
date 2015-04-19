package prng.internet;

import prng.seeds.Seed;
import prng.seeds.SeedInput;
import prng.seeds.SeedOutput;
import prng.seeds.SeedStorage;

public class NetSeed extends Seed {
    /** Default time. "Default" in ASCII */
    public static final long TIME_DEFAULT = -0x44656661756c74l;

    /** Unset time. "Unset" in ASCII */
    public static final long TIME_UNSET = -0x556e736574l;
    
    /** Time at which this entropy was loaded */
    private long loadTime_ = TIME_UNSET;

    /** Number of times this entropy has been used */
    private int usageCount_ = 0;
    
    public NetSeed(String name, byte[] data) {
        super(name,data);
        loadTime_ = System.currentTimeMillis();
    }

    public NetSeed() {
        // do nothing
    }

    @Override
    public void initialize(SeedInput input) throws Exception {
        super.initialize(input);
        loadTime_ = input.readLong();
        usageCount_ = input.readInt();
    }

    @Override
    public void save(SeedOutput output) {
        super.save(output);
        output.writeLong(loadTime_);
        output.writeInt(usageCount_);
    }

    @Override
    public byte[] getSeed() {
        usageCount_++;
        return SeedStorage.scramble(super.getSeed());
    }

    public int getUsageCount() {
        return usageCount_;
    }
    
    public long getLoadTime() {
        return loadTime_;
    }
}
