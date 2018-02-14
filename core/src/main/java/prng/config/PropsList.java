package prng.config;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
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
  ArrayList<Props> props = new ArrayList<>();

  /** Combined properties */
  private Map<String, String> merged = new TreeMap<>();


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
    AccessController.doPrivileged(new PrivilegedAction<Void>() {
      @Override
      public Void run() {
        loadWithPrivilege();
        return null;
      }
    });
  }


  /**
   * Load the property files with access privilege
   */
  void loadWithPrivilege() {
    // First load from the class-path
    Enumeration<URL> resources;
    try {
      resources = Config.class.getClassLoader().getResources(
          "prng/secure-prng.properties");
    } catch (IOException e) {
      Config.LOG.error("Failed to locate configuration files", e);
      resources = Collections.emptyEnumeration();
    }

    // load the properties files
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      props.add(new Props(url));
    }

    merge();

    // Look for over-rides specified via properties
    String config = Config.getProperty("prng.config.properties.url");
    URL url = null;
    if (config != null) {
      config = Config.expand(config);
      try {
        url = new URL(config);
      } catch (MalformedURLException e) {
        Config.LOG.error(
            "Failed to parse property \"prng.config.properties.url\"=\""
                + config + "\" as a URL",
            e);
      }
    } else {
      // No URL specified, was there a file?
      config = Config.getProperty("prng.config.properties");
      if (config != null) {
        config = Config.expand(config);
        try {
          url = new File(config).toURI().toURL();
        } catch (MalformedURLException e) {
          Config.LOG.error("Could not get URL for file " + config);
        }
      }
    }

    // if we got a URL, load the specified properties
    if (url != null) {
      props.add(0, new Props(url));
      merge();
    }

    if (Boolean.valueOf(merged.get("config.preferences.enable.system"))) {
      props.add(new Props(
          () -> Preferences.systemNodeForPackage(
              SecureRandomProvider.class),
          Config.URI_PREFERENCE_SYSTEM));
      merge();
    }

    if (Boolean.valueOf(merged.get("config.preferences.enable.user"))) {
      props.add(0,
          new Props(
              () -> Preferences.userNodeForPackage(
                  SecureRandomProvider.class),
              Config.URI_PREFERENCE_USER));
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
