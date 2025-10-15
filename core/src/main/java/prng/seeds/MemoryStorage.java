package prng.seeds;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import prng.SystemRandom;

/**
 * In-memory only storage. If you are using this, something has probably gone wrong.
 *
 * @author Simon Greatrix
 */
public class MemoryStorage extends SeedStorage {

  private final ConcurrentHashMap<String, byte[]> storage = new ConcurrentHashMap<>();


  @Override
  protected byte[] getRaw(String name) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
    return storage.get(name);
  }


  @Override
  protected void putRaw(String name, byte[] data) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
    SystemRandom.injectSeed(data);
    storage.put(name, data);
  }


  @Override
  protected void remove(String name) {
    SystemRandom.injectSeed(name.getBytes(StandardCharsets.UTF_8));
    storage.remove(name);
  }

}
