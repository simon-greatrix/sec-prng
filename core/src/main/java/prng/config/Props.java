package prng.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Loaded property configuration that knows from where it was loaded
 * 
 * @author Simon Gretrix
 *
 */
public class Props extends Properties {

    /** Serial version UID */
    private static final long serialVersionUID = 1L;

    /** Did this batch of properties load successfully? */
    private final boolean loadedOk;

    /** Where these properties came from */
    private final URI source;


    /**
     * Load preferences as properties
     * 
     * @param prefSupplier
     *            source of preferences
     * @param src
     *            the nominal URI for these preferences
     */
    public Props(Supplier<Preferences> prefSupplier, URI src) {
        source = src;
        Preferences prefs = null;
        // Get the preferences using a privileged action.
        try {
            prefs = AccessController.doPrivileged(
                    new PrivilegedAction<Preferences>() {
                        @Override
                        public Preferences run() {
                            return prefSupplier.get();
                        }
                    });
        } catch (SecurityException se) {
            Config.LOG.info("Unable to access preferences for \""
                    + src.toASCIIString() + "\"", se);
        }

        // Start loading the properties
        if( prefs == null ) {
            loadedOk = false;
            return;
        }

        boolean loadOk = true;
        try {
            // copy each preference into the properties
            for(String k:prefs.keys()) {
                String v = prefs.get(k, null);
                if( v != null ) {
                    setProperty(k, v);
                }
            }
        } catch (BackingStoreException bse) {
            loadOk = false;
            clear();
            Config.LOG.error("Failed to access preference storage.", bse);
        } finally {
            loadedOk = loadOk;
        }
    }


    /**
     * Load properties from the specified URL
     * 
     * @param url
     *            the URL to load from
     */
    public Props(URL url) {
        boolean loadOk = true;
        Config.LOG.info("Loading configuration from {}", url.toExternalForm());
        try (InputStream in = url.openStream()) {
            load(in);
        } catch (IOException | IllegalArgumentException ioe) {
            // it went wrong :-(
            loadOk = false;
            clear();
            Config.LOG.error(
                    "Failed to read configuration file " + url.toString(), ioe);
        } finally {
            loadedOk = loadOk;
        }

        // get URI from URL
        URI uri = null;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            Config.LOG.warn("Unable to convert source URL \""
                    + url.toExternalForm() + "\" to URI");
        } finally {
            source = uri;
        }
    }


    /**
     * Get where these properties were loaded from
     * 
     * @return the source
     */
    public URI getSource() {
        return source;
    }


    /**
     * Set all key-value pairs on map that are set on this and not set on map.
     *
     * @param map
     *            the map which should inherit values from this
     */
    public void inherit(Map<String, String> map) {
        for(Map.Entry<Object, Object> e:entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if( (!map.containsKey(k)) && (k instanceof String)
                    && (v instanceof String) ) {
                map.put((String) k, (String) v);
            }
        }
    }


    /**
     * Did these properties load without error?
     * 
     * @return true if loaded OK
     */
    public boolean isLoadedOk() {
        return loadedOk;
    }
}
