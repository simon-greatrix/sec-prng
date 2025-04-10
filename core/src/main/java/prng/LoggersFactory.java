package prng;

import org.slf4j.Logger;
import prng.config.Config;

/**
 * Factory for creating loggers. This is a simple wrapper around SLF4J that allows logging to be disabled.
 *
 * @author Simon Greatrix on 25/10/2019.
 */
public class LoggersFactory {

  public static Logger getLogger(Class<?> type) {
    return Config.isLoggingEnabled() ? org.slf4j.LoggerFactory.getLogger(type) : new NoOpLogger(type);
  }

}
