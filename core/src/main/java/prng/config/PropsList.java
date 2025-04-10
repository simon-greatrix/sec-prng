package prng.config;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
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

  /** The loaded properties. The element in position 0 is the MOST important. */
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
   * Load the configuration.
   */
  public void load() {
    // We work from least important to most important
    // First load from the class-path
    loadPropertyResources("prng/secure-prng-defaults.properties");
    loadPropertyResources("prng/secure-prng.properties");
    loadPropertyResources("prng/secure-prng-override.properties");

    // merge to see if system properties are enabled
    merge();

    if (Boolean.parseBoolean(merged.get("config.preferences.enable.system"))) {
      // System properties over-ride anything on the deployment
      props.addFirst(new Props(
          () -> Preferences.systemNodeForPackage(
              SecureRandomProvider.class),
          Config.URI_PREFERENCE_SYSTEM
      ));
    }

    // Now from files or URLs
    loadFromUrl(Config.getProperty("prng.config.properties.url"), false);
    loadFromUrl(Config.getProperty("prng.config.properties"), true);
    loadFromUrl(Config.getEnv("PRNG_CONFIG_PROPERTIES_URL"), false);
    loadFromUrl(Config.getEnv("PRNG_CONFIG_PROPERTIES"), true);

    // merge to see if user properties are enabled
    merge();

    if (Boolean.parseBoolean(merged.get("config.preferences.enable.user"))) {
      // User properties over-ride everything
      props.addFirst(
          new Props(
              () -> Preferences.userNodeForPackage(
                  SecureRandomProvider.class),
              Config.URI_PREFERENCE_USER
          )
      );
    }

    merge();
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
      props.addFirst(new Props(url));
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

    // Load the properties files. These come as most to least important, so we have to reverse them
    LinkedList<Props> reversed = new LinkedList<>();
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      reversed.addLast(new Props(url));
    }
    // Adding the reverse list at position zero puts the first loaded properties at the start and the ones loaded later further down the list, as required.
    props.addAll(0, reversed);
  }


  /**
   * Merge loaded files in order.
   */
  private void merge() {
    merged.clear();
    // Inherit only sets properties that are not already set, so we work from MOST to LEAST important.
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
