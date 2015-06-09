package prng.utility;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * A factory for nonces where 256-bit security is required.
 * <p>
 * 
 * For such usage a nonce should not be expected to repeat more often than a
 * (0.5 * security-strength)-bit random number is expected to repeat. Due to the
 * birthday problem a (0.5 * 256)-bit or 128 bit random number is expected to
 * repeat within 2^64 values.
 * <p>
 * The time-based UUID comprises a 60 bit clock time, a 16 bit sequence number
 * and a 96 bit network ID. The combination of clock time and sequence exceeds
 * the required values before repetition on a particular network address.
 * <p>
 * In order to create nonces that are unique across different processes on the
 * same machine, it is necessary to combine the type 1 UUID with a process
 * identifier.
 * <p>
 */

public class NonceFactory {
    /** Identifier for this JVM instance (256 bits/32 bytes)*/
    private static final byte[] IDENTIFIER;

    /** Personalization string (256 bit/32 bytes)*/
    private static final byte[] PERSONALIZATION;

    static {
        DigestDataOutput dig = new DigestDataOutput("SHA-256");

        RuntimeMXBean runBean = ManagementFactory.getRuntimeMXBean();

        // The "name" often contains the process ID, making name and start time
        // a unique identifier for this VM.
        dig.writeUTF(runBean.getName());
        dig.writeLong(runBean.getStartTime());

        // If multiple applications are running in the same JVM, the class
        // loader and load time will make it unique
        dig.writeUTF(NonceFactory.class.getClassLoader().getClass().getName());
        dig.writeInt(System.identityHashCode(NonceFactory.class.getClassLoader()));
        dig.writeLong(System.nanoTime());
        dig.writeLong(Thread.currentThread().getId());

        IDENTIFIER = dig.digest();

        // Now create a personalization string. Start with the identifier info
        dig = new DigestDataOutput("SHA-512");
        dig.writeUTF(runBean.getName());
        dig.writeLong(runBean.getStartTime());
        dig.writeUTF(NonceFactory.class.getClassLoader().getClass().getName());
        dig.writeInt(System.identityHashCode(NonceFactory.class.getClassLoader()));
        dig.writeLong(System.nanoTime());
        dig.writeLong(Thread.currentThread().getId());
        
        // now the class path
        dig.writeUTF(runBean.getClassPath());
        
        // input arguments to this instance
        List<String> args = runBean.getInputArguments();
        dig.writeInt(args.size());
        for(String s:args) {
            dig.writeUTF(s);
        }

        // system and environment properties
        Map<String, String> env = runBean.getSystemProperties();
        dig.writeInt(env.size());
        for(Entry<String, String> e:env.entrySet()) {
            dig.writeUTF(e.getKey());
            dig.writeUTF(e.getValue());
        }

        env = System.getenv();
        dig.writeInt(env.size());
        for(Entry<String, String> e:env.entrySet()) {
            dig.writeUTF(e.getKey());
            dig.writeUTF(e.getValue());
        }

        PERSONALIZATION = dig.digest();
    }


    /**
     * Create a 256-bit nonce.
     * 
     * @return a 256-bit nonce.
     */
    public static byte[] create() {
        UUID uuid = TimeBasedUUID.create();
        DigestDataOutput dig = new DigestDataOutput("SHA-256");
        dig.writeLong(uuid.getMostSignificantBits());
        dig.writeLong(uuid.getLeastSignificantBits());
        dig.write(IDENTIFIER);
        return dig.digest();
    }


    /**
     * Get the application personalization string
     * 
     * @return the personalization string
     */
    public static byte[] personalization() {
        return PERSONALIZATION.clone();
    }
}
