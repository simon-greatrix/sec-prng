package prng;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System provided secure random sources. We assume there is at least one such
 * source. The sources are multiplexed, with one byte taken from each source in
 * turn. This means that if any source has a good entropic seed, its entropy
 * will be included in all outputs.
 * 
 * @author Simon Greatrix
 *
 */
class SystemRandom {
    /** Logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(SystemRandom.class);
    
    /** Block length that is fetched from each source at one time */
    private static final int BLOCK_LEN = 256;

    /** Pre-fetched data */
    private final static byte[][] BLOCKS;

    /** Current source in pre-fetched data */
    private static int INDEX = 0;

    /** Current byte position in pre-fetched data */
    private static int POSITION = 0;

    /** Number of sources */
    private static final int SOURCE_LEN;

    /** System provided secure random number generators */
    private final static SecureRandom[] SOURCES;

    static {
        Provider[] provs = Security.getProviders();
        ArrayList<SecureRandom> sources = new ArrayList<SecureRandom>();
        for(Provider prov:provs) {
            // do not loop into our own provider
            if( prov instanceof SecureRandomProvider ) continue;

            // check for offered secure random sources
            Set<Service> serv = prov.getServices();
            for(Service s:serv) {
                if( s.getType().equals("SecureRandom") ) {
                    try {
                        System.out.println("Initialising " + s.getAlgorithm()
                                + " from " + prov.getName());
                        SecureRandom rand = SecureRandom.getInstance(
                                s.getAlgorithm(), prov);
                        sources.add(rand);
                    } catch (NoSuchAlgorithmException e) {
                        LOG.error("Provider " + prov.getName()
                                + " does not implement " + s.getAlgorithm()
                                + " after announcing it as a service");
                    }
                }
            }
        }
        
        // always include the "strong" algorithm
        try {
            sources.add(SecureRandom.getInstanceStrong());
        } catch (NoSuchAlgorithmException e) {
            LOG.error("System strong secure random generator is unavailable.",e);
        }        

        int len = sources.size();
        SOURCE_LEN = len;
        SOURCES = sources.toArray(new SecureRandom[len]);
        BLOCKS = new byte[len][BLOCK_LEN];
        for(int i = 0;i < len;i++) {
            SOURCES[i].nextBytes(BLOCKS[i]);
        }
    }


    /**
     * Get data from the system secure random number generators
     * 
     * @param data
     *            array to fill
     */
    static synchronized void get(byte[] data) {
        for(int i = 0;i < data.length;i++) {
            data[i] = BLOCKS[INDEX][POSITION];
            INDEX++;
            if( INDEX == SOURCE_LEN ) {
                INDEX = 0;
                POSITION++;
                if( POSITION == BLOCK_LEN ) {
                    POSITION = 0;
                    for(int j = 0;j < SOURCE_LEN;j++) {
                        SOURCES[j].nextBytes(BLOCKS[j]);
                    }
                }
            }
        }
    }
}
