/**
 * Secure Random Number Generation in Java.
 * <p>
 * <h2>Installation</h2>
 * 
 * To use this library in an application, you should do one of the following:
 * <p>
 * 
 * <h3>As a normal security Provider</h3>
 * <p>
 * <code>
 * SecureRandom rand = new SecureRandom("Nist-SHA256",new prng.SecureRandomProvider());
 * </code>
 * <p>
 * 
 * <h3>As a default security Provider</h3>
 * <p>
 * <code>
 * prng.SecureRandomProvider.install(true);
 * </code>
 * <p>
 * Installs the secure random provider as the default secure random provider, so
 * calls to <code>new SecureRandom()</code> will use on of its algorithms.
 * <p>
 * 
 * <h3>As an application override</h3>
 * <p>
 * <code>
 * java -javaagent:prng-1.0.0.jar <i>...rest of command line</i>
 * </code>
 * <p>
 * Install the secure random provider and the default secure random number
 * generator implementation prior to invoking the application's
 * <code>main</code> method.
 * <p>
 * 
 * <h3>As a platform extension</h3>
 * 
 * <p>
 * The provider may be made a standard provider for all applications using a
 * given Java Runtime Environment.
 * 
 * <ol>
 * <li>Copy <code>prng-1.0.0.jar</code> to
 * <code><i>[java home]</i>/jre/lib/ext</code>
 * <li>Open the file
 * <code><i>[java home]</i>/jre/lib/security/java.security</code> in a text
 * editor.
 * <li>Add a line such as:<br>
 * <code>security.provider.1=prng.SecureRandomProvider</code><br>
 * <li>Adjust all the other <code>security.provider.<i>N</i></code> entries so
 * each one has a unique name with the order you desire
 * <li>Save the file
 * </ol>
 * 
 * <hr>
 * <h2>Permissions</h2>
 * The library requires permission to use unlimited strength cryptography. Consult the JCE documentation for how to configure that.<p>
 * 
 * The library uses the following JRE security permissions:<p>
 * 
 * <dl>
 * <dt>SecurityPermission insertProvider (JDK8+)</dt>
 * <dt>SecurityPermission insertProvider.SecureRandomProvider</dt>
 * <dd>Required to add this provider</dd>
 * 
 * <dt>SecurityPermission getProperty.securerandom.strongAlgorithms</dt>
 * <dt>SecurityPermission setProperty.securerandom.strongAlgorithms</dt>
 * <dd>Required to set the "strong" secure random algorithm.</dd>
 * 
 * <dt>RuntimePermission preferences</dt>
 * <dd>Required for storing seed data in user or system preferences</dd>
 * 
 * <dt>PropertyPermission * read,write</dt>
 * <dd>Required to resolve properties mentioned in the configuration file. Required to create nonce factory. Note: the "write" permission is never used, but <code>System.getProperties()</code> method requires it.</dd>

 * <dt>RuntimePermission getenv.*</dt>
 * <dd>Required to environment variables mentioned in the configuration file. Required to create nonce factory.</dd>
 * 
 * <dt>ManagementPermission monitor</dt>
 * <dd>Required to create nonce factory.</dd>
 * 
 * <dt>SocketPermission * connect,resolve</dt>
 * <dd>Required for local host and internet entropy URLs. Local host is used in creating the Type 1 UUIDs.</dd>
 * 
 * <dt>URLPermission * get,post</dt>
 * <dd>Required for internet entropy URLs.</dd>
 * 
 * 
 * <dt>AWTPermission createRobot</dt>
 * <dt>AWTPermission readDisplayPixels</dt>
 * <dd>Used to collect entropy from the current display. The pixels of a random section of the display are passed into a secure hash, and the hash is used as entropy.</dd>
 * </dl>
 * 
 * <hr>
 * <h2>Configuration</h2>
 * The library is configured via the <code>prng/secure-prng.properties</code>
 * file. See the comments in the file itself for what options are available. All
 * options are described in the file.
 * <p>
 * 
 * <hr>
 * <h2>Logging</h2>
 * This library uses the <a href="www.slf4j.org">SLF4J</a> for logging. The
 * following loggers are defined:
 * <dl>
 * <dt>prng.SecureRandomProvider</dt>
 * <dd>Messages related to activating the provider.
 * <dt>prng.SystemRandom</dt>
 * <dd>Messages related to use of the standard JRE SecureRandom instances</dd>
 * 
 * <dt>prng.collector.EntropyCollector</dt>
 * <dd>Messages related to creating the entropy collectors, and collecting
 * entropy.</dd>
 * 
 * <dt>prng.internet.NetRandom
 * <dt>
 * <dd>Messages related to internet sources entropy.</dd>
 * 
 * <dt>prng.seeds.SeedStorage</dt>
 * <dd>Messages related to the storage and retrieval of seed entropy.</dd>
 * 
 * <dt>prng.utility.Config</dt>
 * <dd>Messages concerning the use of the
 * <code>prng/secure-prng.properties</code> file.</dd>
 * 
 * <dt>prng.utility.TimeBasedUUID</dt>
 * <dd>Messages related to creating a Type-1 UUID source.</dd>
 * 
 * </dl>
 * 
 * @author Simon Greatrix
 */
package prng;

