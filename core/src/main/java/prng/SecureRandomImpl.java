package prng;

import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import java.security.SecureRandomSpi;
import java.util.Objects;

/**
 * Implementation of a SecureRandom using a given SPI.
 *
 * @author Simon Greatrix
 */
class SecureRandomImpl extends SecureRandom {

  /** serial version UID */
  private static final long serialVersionUID = 2L;

  /** The actual PRNG */
  private final OpenEngineSpi engineSpi;

  /** Synchronize at this level? If true, don't synchronize. */
  private final boolean threadSafe;


  /**
   * Create secure random instance
   *
   * @param spi SPI to use
   */
  SecureRandomImpl(OpenEngineSpi spi) {
    super((SecureRandomSpi) spi, SecureRandomProvider.PROVIDER);
    engineSpi = spi;
    threadSafe = spi instanceof MultiplexSpi;
  }


  /** {@inheritDoc} */
  @Override
  public byte[] generateSeed(int numBytes) {
    if (numBytes < 0) {
      throw new IllegalArgumentException("numBytes cannot be negative");
    }
    if (threadSafe) {
      return engineSpi.engineGenerateSeed(numBytes);
    } else {
      synchronized (engineSpi) {
        return engineSpi.engineGenerateSeed(numBytes);
      }
    }
  }


  @Override
  public String getAlgorithm() {
    return engineSpi.getAlgorithm();
  }


  @Override
  public SecureRandomParameters getParameters() {
    return engineSpi.engineGetParameters();
  }


  /**
   * Get some material that can be used to re-seed this PRNG.
   *
   * @return some seed material
   */
  public byte[] newSeed() {
    return engineSpi.newSeed();
  }


  /** {@inheritDoc} */
  @Override
  public void nextBytes(byte[] bytes, SecureRandomParameters params) {
    if (params == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (threadSafe) {
      engineSpi.engineNextBytes(Objects.requireNonNull(bytes), params);
    } else {
      synchronized (engineSpi) {
        engineSpi.engineNextBytes(Objects.requireNonNull(bytes), params);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void nextBytes(byte[] bytes) {
    if (threadSafe) {
      engineSpi.engineNextBytes(bytes);
    } else {
      synchronized (engineSpi) {
        engineSpi.engineNextBytes(bytes);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void reseed(SecureRandomParameters params) {
    if (params == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (threadSafe) {
      engineSpi.engineReseed(params);
    } else {
      synchronized (engineSpi) {
        engineSpi.engineReseed(params);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void reseed() {
    if (threadSafe) {
      engineSpi.engineReseed(null);
    } else {
      synchronized (engineSpi) {
        engineSpi.engineReseed(null);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void setSeed(byte[] seed) {
    if (threadSafe) {
      engineSpi.engineSetSeed(seed);
    } else {
      synchronized (engineSpi) {
        engineSpi.engineSetSeed(seed);
      }
    }
  }


  @Override
  public String toString() {
    return engineSpi.toString();
  }

}