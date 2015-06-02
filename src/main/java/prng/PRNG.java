package prng;

import java.security.Provider;
import java.security.Security;

/**
 * Initialisation point.
 * 
 * @author Simon Greatrix
 *
 */
public class PRNG {
    /**
     * Install the Secure Random Provider.
     * 
     * @param isPrimary
     *            if true, the Secure Random Provider will be the primary source
     *            for secure random number generators.
     */
    public static void install(boolean isPrimary) {
        if( isPrimary ) {
            Security.insertProviderAt(SecureRandomProvider.PROVIDER, 1);
        } else {
            Provider[] provs = Security.getProviders();
            Security.insertProviderAt(SecureRandomProvider.PROVIDER,
                    provs.length);
        }
    }
}
