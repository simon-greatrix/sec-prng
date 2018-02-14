package prng.internet;

import java.util.Random;
import prng.EntropySource;
import prng.config.Config;
import prng.generator.IsaacRandom;
import prng.seeds.Seed;
import prng.seeds.SeedStorage;

/**
 * Internet random source manager.
 *
 * @author Simon Greatrix
 */
public class NetManager implements Runnable {

  /**
   * Load data from internet sources
   */
  public static void load() {
    NetManager instance = new NetManager();
    Thread thread = new Thread(instance, "PRNG-NetRandom");
    thread.start();
  }


  /** Number of times a network seed is expected to be used */
  private double expectedUsage;

  /** Seed data drawn from network sources */
  private Seed[] seeds = new Seed[64];

  /** Number of seeds injected into Fortuna */
  private int seedsUsed;

  /** Available network sources */
  private NetRandom[] sources;

  /** Preference weighting for network sources */
  private double[] weights;


  /**
   * Fetch any missing network data
   */
  private void fetch() {
    for (int i = 0; i < 64; i++) {
      Seed seed = seeds[i];
      if (seed == null || seed.isEmpty()) {
        getSource(i);
      }
    }
  }


  /**
   * Get data for the i'th seed from a new network source
   *
   * @param i the seed to fetch data for
   *
   * @return the data fetched
   */
  private byte[] getSource(int i) {
    double r = IsaacRandom.getSharedInstance().nextDouble();
    int source = 0;
    do {
      r -= weights[source];
      if (r <= 0) {
        break;
      }
      source++;
    }
    while (source < weights.length);

    NetRandom nr = sources[source];
    byte[] data = nr.load();
    Seed seed = new Seed("NetRandom." + i, data);
    seeds[i] = seed;
    SeedStorage.enqueue(seed);
    return data;
  }


  /**
   * Initialise the network data
   *
   * @return true if initialization went well
   */
  private boolean init() {
    Config config = Config.getConfig("network");
    expectedUsage = 1.0 / config.getInt("expectedUsage", 32);
    seedsUsed = Math.min(32, config.getInt("seedsUsed", 4));

    // load configuration for sources
    config = Config.getConfig("network.source");
    int count = 0;
    int size = config.size();
    double total = 0;
    double[] myWeights = new double[size];
    NetRandom[] mySources = new NetRandom[size];

    // create source instances
    for (String cl : config) {
      double weight = config.getDouble(cl, 0);
      if (weight <= 0) {
        continue;
      }
      NetRandom source;
      try {
        Class<?> cls = Class.forName(cl);
        source = cls.asSubclass(NetRandom.class).newInstance();
      } catch (Exception e) {
        NetRandom.LOG.error("Failed to create internet source \"{}\"",
            cl, e);
        continue;
      }
      myWeights[count] = weight;
      mySources[count] = source;
      count++;
      total += weight;
    }

    if (count == 0) {
      return false;
    }

    // All sources found, store in arrays
    sources = new NetRandom[count];
    System.arraycopy(mySources, 0, sources, 0, count);
    weights = new double[count];
    System.arraycopy(myWeights, 0, weights, 0, count);
    for (int i = 0; i < count; i++) {
      weights[i] /= total;
    }

    // load current seeds
    try (SeedStorage store = SeedStorage.getInstance()) {
      for (int i = 0; i < 64; i++) {
        seeds[i] = store.get("NetRandom." + i);
      }
    }

    return true;
  }


  /**
   * Inject data into Fortuna
   */
  private void inject() {
    EntropySource entropy = new EntropySource();
    Random rand = IsaacRandom.getSharedInstance();
    byte[] indexes = new byte[seedsUsed];
    rand.nextBytes(indexes);
    for (int i = 0; i < indexes.length; i++) {
      // pick a seed at random
      int index = indexes[i] & 63;
      Seed seed = seeds[index];

      // get data from the seed
      byte[] data;
      double r = rand.nextDouble();
      if (seed == null || seed.isEmpty() || r < expectedUsage) {
        data = getSource(index);
      } else {
        data = seed.getSeed();
      }

      // if no data, skip
      if (data.length == 0) {
        continue;
      }

      // inject some data into Fortuna
      byte[] event = new byte[16];
      rand.nextBytes(event);
      for (int j = 0; j < 16; j++) {
        event[j] = data[event[j] & 127];
      }
      entropy.setEvent(event);
    }
  }


  /**
   * Process the network random data
   */
  @Override
  public void run() {
    if (!init()) {
      return;
    }
    inject();
    fetch();
  }
}
