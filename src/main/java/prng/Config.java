package prng;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    static {
        init();
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
            String key = value.substring(0, e);
            value = value.substring(e + 1);
            String env = System.getProperty(key);
            if( env == null ) {
                env = System.getenv(key);
            }

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
     * Load configuration data from the
     * <code>/prng/secure-prng.properties</code> files.
     */
    private static void init() {
        // find all the files
        Enumeration<URL> resources;
        try {
            resources = Config.class.getClassLoader().getResources(
                    "prng/secure-prng.properties");
        } catch (IOException e) {
            LOG.error("Failed to locate configuration files", e);
            return;
        }

        // load the properties files
        CONFIG.clear();
        while( resources.hasMoreElements() ) {
            URL url = resources.nextElement();
            LOG.info("Loading configuration from {}",url.toExternalForm());
            Properties props = new Properties();
            try (InputStream in = url.openStream()) {
                props.load(in);
            } catch (IOException ioe) {
                // it went wrong :-(
                LOG.error(
                        "Failed to read configuration file " + url.toString(),
                        ioe);
                continue;
            }

            // load configuration so that first loaded wins
            for(Map.Entry<Object, Object> e:props.entrySet()) {
                String k = String.valueOf(e.getKey());
                String v = String.valueOf(e.getValue());
                if( !CONFIG.containsKey(k) ) {
                    CONFIG.put(k, v);
                }
            }
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
     * An iterator over the keys of this configuration
     * 
     * @return an iterator.
     */
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
     * @return the value or nullt if missing
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
}
