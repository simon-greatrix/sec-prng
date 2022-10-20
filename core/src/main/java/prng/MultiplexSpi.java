package prng;

import java.security.SecureRandomParameters;
import java.security.SecureRandomSpi;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import prng.generator.BaseRandom;

/**
 * An SPI that multiplexes other SPIs to provide thread safety.
 *
 * @author Simon Greatrix on 18/10/2022.
 */
class MultiplexSpi extends SecureRandomSpi implements OpenEngineSpi {

  private static final Logger LOG = LoggersFactory.getLogger(MultiplexSpi.class);

  private final AtomicInteger size = new AtomicInteger(0);

  /**
   * Pool of SPIs.
   */
  private final LinkedBlockingDeque<BaseRandom> spiPool = new LinkedBlockingDeque<>();

  /**
   * Template for generating additional SPIs.
   */
  private final Supplier<BaseRandom> template;


  /**
   * New instance.
   *
   * @param base     the first SPI. May be null.
   * @param template the template to generate additional SPIs.
   */
  public MultiplexSpi(BaseRandom base, Supplier<BaseRandom> template) {
    if (base != null) {
      spiPool.add(base);
      size.incrementAndGet();
    }
    this.template = template;
  }


  @Override
  public byte[] engineGenerateSeed(int numBytes) {
    BaseRandom spi = reserve();
    try {
      return spi.engineGenerateSeed(numBytes);
    } finally {
      release(spi);
    }
  }


  @Override
  public SecureRandomParameters engineGetParameters() {
    BaseRandom spi = reserve();
    try {
      return spi.engineGetParameters();
    } finally {
      release(spi);
    }
  }


  @Override
  public void engineNextBytes(byte[] bytes, SecureRandomParameters params) {
    BaseRandom spi = reserve();
    try {
      spi.engineNextBytes(bytes, params);
    } finally {
      release(spi);
    }
  }


  @Override
  public void engineNextBytes(byte[] bytes) {
    BaseRandom spi = reserve();
    try {
      spi.engineNextBytes(bytes);
    } finally {
      release(spi);
    }
  }


  @Override
  public void engineReseed(SecureRandomParameters params) {
    BaseRandom spi = reserve();
    try {
      spi.engineReseed(params);
    } finally {
      release(spi);
    }
  }


  @Override
  public void engineSetSeed(byte[] seed) {
    BaseRandom spi = reserve();
    try {
      spi.engineSetSeed(seed);
    } finally {
      release(spi);
    }
  }


  @Override
  public String getAlgorithm() {
    BaseRandom spi = reserve();
    try {
      return spi.getAlgorithm();
    } finally {
      release(spi);
    }
  }


  @Override
  public byte[] newSeed() {
    BaseRandom spi = reserve();
    try {
      return spi.newSeed();
    } finally {
      release(spi);
    }
  }


  private void release(BaseRandom spi) {
    if (!spiPool.offerLast(spi)) {
      LOG.warn("Unable to recycle SPI instance");
      size.decrementAndGet();
    }
  }


  private BaseRandom reserve() {
    BaseRandom spi = spiPool.pollFirst();
    if (spi == null) {
      spi = template.get();
      size.incrementAndGet();
    }
    return spi;
  }


  @Override
  public String toString() {
    String className;
    BaseRandom spi = reserve();
    try {
      className = spi.getClass().getSimpleName();
    } finally {
      release(spi);
    }
    return String.format("MultiplexSpi(%s, %d)", className, size.get());
  }

}
