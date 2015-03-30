package prng;

import java.security.SecureRandom;
import java.security.SecureRandomSpi;

/**
 * Implementation of a SecureRandom using a given SPI.
 * 
 * @author Simon Greatrix
 *
 */
class SecureRandomImpl extends SecureRandom {
    /** serial version UID */
    private static final long serialVersionUID = 2l;


    /**
     * Create secure random instance
     * 
     * @param spi
     *            SPI to use
     */
    SecureRandomImpl(SecureRandomSpi spi) {
        super(spi, SecureRandomProvider.PROVIDER);
    }
}