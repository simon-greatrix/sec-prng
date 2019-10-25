package prng.config;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import prng.SecureRandomProvider;

/**
 * A list of loaded property configuration files
 *
 * @author Simon Greatrix
 */
public class PropsList {

  /** The loaded properties */
  final LinkedList<Props> props = new LinkedList<>();

  /** Combined properties */
  private final Map<String, String> merged = new TreeMap<>();


  /**
   * Get the merged configuration
   *
   * @return the merged configuration
   */
  public Map<String, String> get() {
    return Collections.unmodifiableMap(merged);
  }


  /**
   * Get the i'th property file
   *
   * @param i the index
   *
   * @return the property file
   */
  public Props get(int i) {
    return props.get(i);
  }


  /**
   * Load the configuration.
   */
  public void load() {
    AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
      loadWithPrivilege();
      return null;
    });
  }


  private void loadFromUrl(String config, boolean isFile) {
    if (config == null) {
      return;
    }
    config = Config.expand(config);

    URL url = null;
    if (isFile) {
      try {
        url = new File(config).toURI().toURL();
      } catch (MalformedURLException e) {
        Config.LOG.error("Could not get URL for file {}", config);
      }
    } else {
      try {
        url = new URL(config);
      } catch (MalformedURLException e) {
        Config.LOG.error("Failed to parse value \"{}\" as a URL", config, e);
      }
    }

    // if we got a URL, load the specified properties
    if (url != null) {
      // local file over-rides anything on class-path
      props.add(0, new Props(url));
      merge();
    }
  }


  private void loadPropertyResources(String path) {
    Enumeration<URL> resources;
    try {
      resources = Config.class.getClassLoader().getResources(path);
    } catch (IOException e) {
      Config.LOG.error("Failed to locate configuration files", e);
      resources = Collections.emptyEnumeration();
    }

    // load the properties files
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      props.add(0, new Props(url));
    }
  }


  /**
   * Load the property files with access privilege
   */
  void loadWithPrivilege() {
    // First load from the class-path
    loadPropertyResources("prng/secure-prng-override.properties");
    loadPropertyResources("prng/secure-prng-defaults.properties");
    loadPropertyResources("prng/secure-prng-defaults.properties");

    // Now from files or URLs
    loadFromUrl(Config.getProperty("prng.config.properties.url"), false);
    loadFromUrl(Config.getProperty("prng.config.properties"), true);
    loadFromUrl(Config.getEnv("PRNG_CONFIG_PROPERTIES_URL"), false);
    loadFromUrl(Config.getEnv("PRNG_CONFIG_PROPERTIES"), true);

    merge();

    // Now from recursively references sources
    HashSet<String> locations = new HashSet<>();
    while (true) {
      String loc = merged.get("prng.config.properties.url");
      if (loc == null || !locations.add(loc)) {
        break;
      }
      loadFromUrl(loc, false);
      merge();
    }

    // Now for files
    locations.clear();
    while (true) {
      String loc = merged.get("prng.config.properties");
      if (loc == null || !locations.add(loc)) {
        break;
      }
      loadFromUrl(loc, true);
      merge();
    }

    if (Boolean.parseBoolean(merged.get("config.preferences.enable.system"))) {
      // System properties over-ride anything on the deployment
      props.add(0, new Props(
          () -> Preferences.systemNodeForPackage(
              SecureRandomProvider.class),
          Config.URI_PREFERENCE_SYSTEM
      ));
      merge();
    }

    if (Boolean.parseBoolean(merged.get("config.preferences.enable.user"))) {
      // User properties over-ride everything
      props.add(
          0,
          new Props(
              () -> Preferences.userNodeForPackage(
                  SecureRandomProvider.class),
              Config.URI_PREFERENCE_USER
          )
      );
      merge();
    }
  }


  /**
   * Merge loaded files in order
   */
  private void merge() {
    merged.clear();
    for (Props p : props) {
      p.inherit(merged);
    }
  }


  /**
   * Get the number of property files loaded
   *
   * @return the number of files
   */
  public int size() {
    return props.size();
  }
}
