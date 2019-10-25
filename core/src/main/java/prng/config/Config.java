package prng.config;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import prng.LoggersFactory;

/**
 * Configuration data. The configuration is stored in a properties file. Different sections of the configuration file are distinguished by different prefixes on
 * the property names.
 *
 * @author Simon Greatrix
 */
public class Config implements Iterable<String> {

  /** Logger for configuration related matters */
  public static final Logger LOG;

  /** URI used to indicate configuration from system preferences */
  static final URI URI_PREFERENCE_SYSTEM;

  /** URI used to indicate configuration from user preferences */
  static final URI URI_PREFERENCE_USER;

  /** The raw configuration */
  private static final Map<String, String> CONFIG = new TreeMap<>();

  private static final boolean loggingEnabled;


  /**
   * Expand any system properties or environment variables in the input. A system property or environment variable is enclosed in braces (i.e. {variable}).
   *
   * @param value the input
   *
   * @return the input with values replaced.
   */
  public static String expand(String value) {
    // if null, do not process
    if (value == null) {
      return null;
    }

    // are any replacements indicated?
    int s = value.indexOf('{');
    if (s == -1) {
      return value;
    }

    // build new string
    StringBuilder buf = new StringBuilder();
    do {
      buf.append(value.substring(0, s));

      // advance to start of key, find end of key
      value = value.substring(s + 1);
      int e = value.indexOf('}');
      if (e == -1) {
        // no key end, so done
        buf.append('{');
        break;
      }

      // get key and advance
      final String key = value.substring(0, e);
      value = value.substring(e + 1);
      String env = getProperty(key);
      if (env == null) {
        env = getEnv(key);
      }

      // did we get a replacement?
      if (env == null) {
        // no replacement, leave as it was
        buf.append('{').append(key).append('}');
      } else {
        // replace
        buf.append(env);
      }

      // find next replacement
      s = value.indexOf('{');
    }
    while (s != -1);

    buf.append(value);
    return buf.toString();
  }


  /**
   * Get the configuration for a specified prefix.
   *
   * @param prefix the prefix
   *
   * @return the configuration associated with that prefix
   */
  public static Config getConfig(String prefix) {
    if ((prefix.length() > 0) && !prefix.endsWith(".")) {
      prefix = prefix + ".";
    }
    return new Config(prefix);
  }


  /**
   * Get the configuration for a specified prefix in the context of a given class
   *
   * @param prefix  the prefix
   * @param context the class
   *
   * @return the configuration
   */
  public static Config getConfig(String prefix, Class<?> context) {
    if ((prefix.length() > 0) && !prefix.endsWith(".")) {
      prefix = prefix + ".";
    }
    prefix += context.getName() + ".";
    return new Config(prefix);
  }


  /**
   * Get an environment variable with privilege.
   *
   * @param key the variable name to retrieve
   *
   * @return the value, or null if the variable does not exist or we lack the privilege to get it.
   */
  public static String getEnv(String key) {
    return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
      try {
        return System.getProperty(key);
      } catch (SecurityException se) {
        RuntimePermission p = new RuntimePermission(
            "getenv." + key);
        LOG.warn("Unable to read environment variable \"" + key
            + "\". Missing permission " + p);
        return null;
      }
    });
  }


  /**
   * Get a system property with privilege.
   *
   * @param key the system property to retrieve
   *
   * @return the property, or null if the property does not exist or we lack the privilege to get it.
   */
  public static String getProperty(String key) {
    return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
      try {
        return System.getProperty(key);
      } catch (SecurityException se) {
        PropertyPermission p = new PropertyPermission(key, "read");
        LOG.warn("Unable to read system property \"" + key
            + "\". Missing permission " + p);
        return null;
      }
    });
  }


  /**
   * Load configuration data from the <code>/prng/secure-prng.properties</code> files.
   */
  static void init() {
    PropsList props = new PropsList();
    props.load();
    CONFIG.clear();
    CONFIG.putAll(props.get());

    // log what was loaded
    if (LOG.isInfoEnabled()) {
      Properties log = new Properties();
      log.putAll(CONFIG);
      try (StringWriter writer = new StringWriter()) {
        log.store(writer, "Configuration as loaded");
        LOG.info(writer.toString());
      } catch (Exception ioe) {
        LOG.info(CONFIG.toString());
        LOG.error("Failed to write out configuration", ioe);
      }
    }
  }


  public static boolean isLoggingEnabled() {
    return loggingEnabled;
  }


  static {
    String v = getEnv("PRNG_LOGGING");
    if (v != null && !v.isEmpty()) {
      loggingEnabled = Boolean.parseBoolean(v);
    } else {
      v = getProperty("prng.logging");
      if (v != null && !v.isEmpty()) {
        loggingEnabled = Boolean.parseBoolean(v);
      } else {
        // Off by default
        loggingEnabled = false;
      }
    }

    LOG = LoggersFactory.getLogger(Config.class);
    URI_PREFERENCE_SYSTEM = URI.create("prefs:system");
    String userId = getProperty("user.name");
    if (userId == null) {
      userId = "*";
    }
    try {
      userId = URLEncoder.encode(userId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new Error("UTF-8 must be supported by JVM");
    }
    URI_PREFERENCE_USER = URI.create("prefs:user/" + userId);

    init();
  }

  /** Configuration for the specified category */
  protected final Map<String, String> config = new HashMap<>();


  /**
   * Create new configuration
   *
   * @param prefix the prefix
   */
  private Config(String prefix) {
    int len = prefix.length();
    for (Map.Entry<String, String> e : CONFIG.entrySet()) {
      String k = e.getKey();
      if (!k.startsWith(prefix)) {
        continue;
      }
      config.put(k.substring(len), e.getValue());
    }
  }


  /**
   * Get a textual property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public String get(String key) {
    return config.get(key);
  }


  /**
   * Get a textual property
   *
   * @param key  the lookup key
   * @param dflt the default value
   *
   * @return the value
   */
  public String get(String key, String dflt) {
    String v = get(key);
    return (v == null) ? dflt : v;
  }


  /**
   * Get a boolean property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public Boolean getBoolean(String key) {
    return Boolean.valueOf(config.get(key));
  }


  /**
   * Get a boolean property
   *
   * @param key  the lookup key
   * @param dflt the default value
   *
   * @return the value
   */
  public boolean getBoolean(String key, boolean dflt) {
    Boolean v = getBoolean(key);
    return (v == null) ? dflt : v;
  }


  /**
   * Get a double property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public Double getDouble(String key) {
    String txt = config.get(key);
    if (txt == null) {
      return null;
    }
    try {
      return Double.valueOf(txt);
    } catch (NumberFormatException e) {
      LOG.warn("Bad data for double parameter {}: {}", key, txt);
    }
    return null;
  }


  /**
   * Get a double property.
   *
   * @param key  the lookup key
   * @param dflt the default value to use if unspecified
   *
   * @return the value or default if missing
   */
  public double getDouble(String key, double dflt) {
    Double v = getDouble(key);
    return (v == null) ? dflt : v.doubleValue();
  }


  /**
   * Get a float property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public Float getFloat(String key) {
    String txt = config.get(key);
    if (txt == null) {
      return null;
    }
    try {
      return Float.valueOf(txt);
    } catch (NumberFormatException e) {
      LOG.warn("Bad data for float parameter {}: {}", key, txt);
    }
    return null;
  }


  /**
   * Get a float property.
   *
   * @param key  the lookup key
   * @param dflt the default value to use if unspecified
   *
   * @return the value or default if missing
   */
  public float getFloat(String key, float dflt) {
    Float v = getFloat(key);
    return (v == null) ? dflt : v.floatValue();
  }


  /**
   * Get an integer property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public Integer getInt(String key) {
    String txt = config.get(key);
    if (txt == null) {
      return null;
    }
    try {
      return Integer.valueOf(txt);
    } catch (NumberFormatException e) {
      LOG.warn("Bad data for integer parameter {}: {}", key, txt);
    }
    return null;
  }


  /**
   * Get an integer property.
   *
   * @param key  the lookup key
   * @param dflt the default value to use if unspecified
   *
   * @return the value or default if missing
   */
  public int getInt(String key, int dflt) {
    Integer v = getInt(key);
    return (v == null) ? dflt : v.intValue();
  }


  /**
   * Get a long property.
   *
   * @param key the lookup key
   *
   * @return the value or null if missing
   */
  public Long getLong(String key) {
    String txt = config.get(key);
    if (txt == null) {
      return null;
    }
    try {
      return Long.valueOf(txt);
    } catch (NumberFormatException e) {
      LOG.warn("Bad data for long parameter {}: {}", key, txt);
    }
    return null;
  }


  /**
   * Get a long property.
   *
   * @param key  the lookup key
   * @param dflt the default value to use if unspecified
   *
   * @return the value or default if missing
   */
  public long getLong(String key, long dflt) {
    Long v = getLong(key);
    return (v == null) ? dflt : v.longValue();
  }


  /**
   * An iterator over the keys of this configuration
   *
   * @return an iterator.
   */
  @Override
  public Iterator<String> iterator() {
    return keySet().iterator();
  }


  /**
   * Get the set of all keys in this configuration.
   *
   * @return the keys
   */
  public Set<String> keySet() {
    return Collections.unmodifiableSet(config.keySet());
  }


  /**
   * Get the number of entries in this configuration
   *
   * @return the number of entries
   */
  public int size() {
    return config.size();
  }
}
