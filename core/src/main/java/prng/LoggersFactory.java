package prng;

import org.slf4j.Logger;
import prng.config.Config;

/**
 * @author Simon Greatrix on 25/10/2019.
 */
public class LoggersFactory {

  public static Logger getLogger(Class<?> type) {
    return Config.isLoggingEnabled() ? org.slf4j.LoggerFactory.getLogger(type) : new NoOpLogger(type);
  }
}
