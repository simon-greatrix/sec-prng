package prng.internet;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.Config;
import prng.seeds.Seed;
import prng.seeds.SeedInput;
import prng.seeds.SeedOutput;
import prng.seeds.SeedStorage;

/**
 * Limited use seed data. The data has a use-by date and a limited number of
 * uses before it is deemed no longer usable.
 * 
 * @author Simon Greatrix
 *
 */
public class NetSeed extends Seed {
    /** Logger for this class */
    protected static final Logger LOG = LoggerFactory.getLogger(NetSeed.class);

    /** Unset time. Negative of "Unset" in ASCII */
    private static final long TIME_UNSET = -0x556e736574l;

    /** Value for when there is no data */
    private static final byte[] EMPTY = new byte[0];

    /** Minimum age before entropy is refreshed */
    private static final long MIN_AGE;

    /** Maximum age after which entropy must be refreshed */
    private static final long MAX_AGE;

    /** Minimum number of uses before entropy is refreshed */
    private static final int MIN_USAGE;

    static {
        Config config = Config.getConfig("network");
        MIN_AGE = config.getLong("minAge",
                TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
        MAX_AGE = config.getLong("maxAge",
                TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS));
        MIN_USAGE = config.getInt("minUsage", 32);
    }

    /**
     * The time the entropy was loaded. Old entropy can be replaced. The initial
     * value is ASCII for "Unset".
     */
    private long loadTime_ = TIME_UNSET;

    /** Does this seed need to be saved? */
    transient boolean needSave_ = false;

    /** Current position */
    private int position_;

    /** Number of times this entropy has been used */
    private int usageCount_ = 0;


    /**
     * NetSeed to be loaded from storage
     */
    public NetSeed() {
        super();
    }


    /**
     * NetSeed just created from Internet.
     * 
     * @param name
     *            this seed's name
     * @param data
     *            this seed's data
     */
    public NetSeed(String name, byte[] data) {
        super(name, data);
    }


    /**
     * Get 256-bit entropy from this source.
     * 
     * @return seed bytes
     */
    @Override
    public byte[] getSeed() {
        synchronized (this) {
            if( data_.length == 0 ) return EMPTY;
            if( isExpired() ) {
                data_ = EMPTY;
                return EMPTY;
            }

            byte[] output = new byte[32];
            int pos = position_;
            int rem = 128 - pos;

            // if we have enough entropy, use some of what we have now
            if( 32 <= rem ) {
                System.arraycopy(data_, 0, output, pos, 32);
                position_ += 32;
                return output;
            }

            // scramble to create new blocks
            data_ = SeedStorage.scramble(data_);
            usageCount_++;
            position_ = 32;
            System.arraycopy(data_, 0, output, 0, 32);
            needSave_ = true;
            return output;
        }
    }


    @Override
    public void initialize(SeedInput input) throws Exception {
        super.initialize(input);
        loadTime_ = input.readLong();
        usageCount_ = input.readInt();
        position_ = input.readInt();
    }


    @Override
    public void save(SeedOutput output) {
        synchronized (this) {
            super.save(output);
            output.writeLong(loadTime_);
            output.writeInt(usageCount_);
            output.writeInt(position_);
            needSave_ = false;
        }
    }


    /**
     * Is this seed data too used or too old to continue with?
     */
    boolean isExpired() {
        // must refresh if no entropy
        if( data_.length != 128 ) return true;

        // must refresh if older than a week and used at least once
        long age = System.currentTimeMillis() - loadTime_;
        if( age > MAX_AGE && usageCount_ > 1 ) return true;

        // must refresh if older than a day and used more than 32 times
        if( (age > MIN_AGE) && (usageCount_ >= MIN_USAGE) ) return true;

        return false;
    }


    @Override
    public String toString() {
        return "NetSeed [loadTime=" + loadTime_ + ", name=" + getName()
                + ", position=" + position_ + ", usageCount=" + usageCount_
                + "]";
    }

}
