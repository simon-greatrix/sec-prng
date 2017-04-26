package prng.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.SecureRandomProvider;

/**
 * Configuration data. The configuration is stored in a properties file.
 * Different sections of the configuration file are distinguished by different
 * prefixes on the property names.
 * 
 * @author Simon Greatrix
 *
 */
public class Config implements Iterable<String> {
    /** The raw configuration */
    private static final Map<String, String> CONFIG = new TreeMap<String, String>();

    /** Logger for configuration related matters */
    public static final Logger LOG = LoggerFactory.getLogger(Config.class);

    /** URI used to indicate configuration from system preferences */
    static final URI URI_PREFERENCE_SYSTEM;

    /** URI used to indicate configuration from user preferences */
    static final URI URI_PREFERENCE_USER;

    static {
        URI_PREFERENCE_SYSTEM = URI.create("prefs:system");
        String userId = System.getProperty("user.name", "");
        try {
            userId = URLEncoder.encode(userId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error("UTF-8 must be supported by JVM");
        }
        URI_PREFERENCE_USER = URI.create("prefs:user/" + userId);

        init(null);
    }


    /**
     * Expand any system properties or environment variables in the input. A
     * system property or environment variable is enclosed in braces (i.e.
     * {variable}).
     * 
     * @param value
     *            the input
     * @return the input with values replaced.
     */
    public static String expand(String value) {
        // if null, do not process
        if( value == null ) return null;

        // are any replacements indicated?
        int s = value.indexOf('{');
        if( s == -1 ) return value;

        // build new string
        StringBuilder buf = new StringBuilder();
        do {
            buf.append(value.substring(0, s));

            // advance to start of key, find end of key
            value = value.substring(s + 1);
            int e = value.indexOf('}');
            if( e == -1 ) {
                // no key end, so done
                buf.append('{');
                break;
            }

            // get key and advance
            final String key = value.substring(0, e);
            value = value.substring(e + 1);
            String env = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {

                        @Override
                        public String run() {
                            String env0 = System.getProperty(key);
                            if( env0 == null ) {
                                env0 = System.getenv(key);
                            }
                            return env0;
                        }
                    });

            // did we get a replacement?
            if( env == null ) {
                // no replacement, leave as it was
                buf.append('{').append(key).append('}');
            } else {
                // replace
                buf.append(env);
            }

            // find next replacement
            s = value.indexOf('{');
        } while( s != -1 );

        buf.append(value);
        return buf.toString();
    }


    /**
     * Get the configuration for a specified prefix.
     * 
     * @param prefix
     *            the prefix
     * @return the configuration associated with that prefix
     */
    public static Config getConfig(String prefix) {
        if( prefix.length() > 0 && !prefix.endsWith(".") ) {
            prefix = prefix + ".";
        }
        return new Config(prefix);
    }


    /**
     * Get the configuration for a specified prefix in the context of a given
     * class
     * 
     * @param prefix
     *            the prefix
     * @param context
     *            the class
     * @return the configuration
     */
    public static Config getConfig(String prefix, Class<?> context) {
        if( prefix.length() > 0 && !prefix.endsWith(".") ) {
            prefix = prefix + ".";
        }
        prefix += context.getName() + ".";
        return new Config(prefix);
    }


    /**
     * Merge a set of properties into the current configuration. If the
     * configuration already contains a value for a given key, the property is
     * skipped.
     * 
     * @param props
     *            the properties to load
     * @param sources
     *            the known sources
     * @param source
     *            the current source
     */
    private static void initMerge(Properties props, Map<String, URI> sources,
            URI source) {
        for(Map.Entry<Object, Object> e:props.entrySet()) {
            String k = String.valueOf(e.getKey());
            String v = String.valueOf(e.getValue());
            if( !CONFIG.containsKey(k) ) {
                CONFIG.put(k, v);
                // store the source
                if( sources != null ) {
                    sources.put(k, source);
                }
            }
        }
    }


    /**
     * Fetch configuration from the preferences API
     * @param prefSupplier the source of the preferences
     * @param desc the description of the preferences for logging
     * @return the loaded properties
     */
    private static Properties fetchPreferences(
            Supplier<Preferences> prefSupplier, String desc) {
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
            LOG.info("Unable to access preferences for \"" + desc + "\"", se);
        }

        // Start loading the properties
        Properties props = new Properties();
        if( prefs == null ) {
            return props;
        }

        try {
            // copy each preference into the properties
            for(String k:prefs.keys()) {
                String v = prefs.get(k, null);
                if( v != null ) {
                    props.setProperty(k, v);
                }
            }
        } catch (BackingStoreException bse) {
            LOG.error("Failed to access prefernce storage.", bse);
        }
        return props;
    }


    /**
     * Load configuration data from the
     * <code>/prng/secure-prng.properties</code> files.
     * @param sources where each configuration item comes from
     * @return the loaded configuration
     */
    static Map<String,String> init(Map<String, URI> sources) {
        // find all the files
        Enumeration<URL> resources;
        try {
            resources = Config.class.getClassLoader().getResources(
                    "prng/secure-prng.properties");
        } catch (IOException e) {
            LOG.error("Failed to locate configuration files", e);
            return Collections.emptyMap();
        }

        CONFIG.clear();

        // Load user preferences
        Properties props = fetchPreferences(
                () -> Preferences.userNodeForPackage(
                        SecureRandomProvider.class),
                "user");
        initMerge(props, sources, URI_PREFERENCE_USER);

        // Load system preferences
        props = fetchPreferences(() -> Preferences.systemNodeForPackage(
                SecureRandomProvider.class), "system");
        initMerge(props, sources, URI_PREFERENCE_SYSTEM);

        // load the properties files
        while( resources.hasMoreElements() ) {
            URL url = resources.nextElement();
            LOG.info("Loading configuration from {}", url.toExternalForm());
            props = new Properties();
            try (InputStream in = url.openStream()) {
                props.load(in);
            } catch (IOException ioe) {
                // it went wrong :-(
                LOG.error("Failed to read configuration file " + url.toString(),
                        ioe);
                continue;
            }

            // load configuration so that first loaded wins
            URI uri = null;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                LOG.warn("Unable to convert source URL \""
                        + url.toExternalForm() + "\" to URI");
            }
            initMerge(props, sources, uri);
        }

        // log what was loaded
        if( LOG.isInfoEnabled() ) {
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
        
        return Collections.unmodifiableMap(CONFIG);
    }

    /** Configuration for the specified category */
    protected Map<String, String> config_ = new HashMap<String, String>();


    /**
     * Create new configuration
     * 
     * @param prefix
     *            the prefix
     */
    private Config(String prefix) {
        int len = prefix.length();
        for(Map.Entry<String, String> e:CONFIG.entrySet()) {
            String k = e.getKey();
            if( !k.startsWith(prefix) ) continue;
            config_.put(k.substring(len), e.getValue());
        }
    }


    /**
     * Get a textual property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public String get(String key) {
        return config_.get(key);
    }


    /**
     * Get a textual property
     * 
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value
     * @return the value
     */
    public String get(String key, String dflt) {
        String v = get(key);
        return (v == null) ? dflt : v;
    }


    /**
     * Get a boolean property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public Boolean getBoolean(String key) {
        return Boolean.valueOf(config_.get(key));
    }


    /**
     * Get a boolean property
     * 
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value
     * @return the value
     */
    public boolean getBoolean(String key, boolean dflt) {
        Boolean v = getBoolean(key);
        return (v == null) ? dflt : v;
    }


    /**
     * Get a double property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public Double getDouble(String key) {
        String txt = config_.get(key);
        if( txt == null ) return null;
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
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value to use if unspecified
     * @return the value or default if missing
     */
    public double getDouble(String key, double dflt) {
        Double v = getDouble(key);
        return (v == null) ? dflt : v.doubleValue();
    }


    /**
     * Get a float property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public Float getFloat(String key) {
        String txt = config_.get(key);
        if( txt == null ) return null;
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
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value to use if unspecified
     * @return the value or default if missing
     */
    public float getFloat(String key, float dflt) {
        Float v = getFloat(key);
        return (v == null) ? dflt : v.floatValue();
    }


    /**
     * Get an integer property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public Integer getInt(String key) {
        String txt = config_.get(key);
        if( txt == null ) return null;
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
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value to use if unspecified
     * @return the value or default if missing
     */
    public int getInt(String key, int dflt) {
        Integer v = getInt(key);
        return (v == null) ? dflt : v.intValue();
    }


    /**
     * Get a long property.
     * 
     * @param key
     *            the lookup key
     * @return the value or null if missing
     */
    public Long getLong(String key) {
        String txt = config_.get(key);
        if( txt == null ) return null;
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
     * @param key
     *            the lookup key
     * @param dflt
     *            the default value to use if unspecified
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
        return Collections.unmodifiableSet(config_.keySet());
    }


    /**
     * Get the number of entries in this configuration
     * 
     * @return the number of entries
     */
    public int size() {
        return config_.size();
    }
}
