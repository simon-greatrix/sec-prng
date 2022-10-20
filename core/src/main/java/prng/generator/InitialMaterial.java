package prng.generator;

import java.util.Arrays;

import prng.Fortuna;
import prng.utility.NonceFactory;

/**
 * The material to initialise a secure random generator with. <p>
 * <p>
 * This material cannot be used instantly as all the cryptographic services must finish initialising before generation can occur, but many services need random
 * number generators. To avoid this circular dependency, the material is kept until the generator is first used. <p>
 *
 * @author Simon Greatrix
 */
class InitialMaterial {

  /** The desired initial entropy. */
  private final int desiredEntropy;

  /** The minimum initial entropy required. */
  private final int minEntropy;

  /** Entropy source, if additional data is required. */
  private final SeedSource source;

  /** The supplied entropy. Will be augmented from Fortuna if necessary. */
  private byte[] entropy;

  /**
   * The supplied nonce. Data from the nonce factory will be used if none is supplied.
   */
  private byte[] nonce;

  /**
   * Personalization data. The nonce factory will be used if none is supplied.
   */
  private final byte[] personalization;


  /**
   * New instance.
   *
   * @param source          entropy source (Fortuna will be used if unspecified)
   * @param entropy         initial entropy (Fortuna will be used if insufficient to requirements)
   * @param nonce           A nonce. Will be drawn from the nonce factory if null.
   * @param personalization Personalization data. Will be drawn from the factory if null
   * @param minEntropy      the minimum required entropy
   * @param desiredEntropy  the desired entropy
   */
  public InitialMaterial(SeedSource source, byte[] entropy, byte[] nonce, byte[] personalization, int minEntropy, int desiredEntropy) {
    // Note it is the job of this class to clear entropy and nonce after use, so we use the references.
    this.entropy = entropy;
    this.nonce = nonce;
    this.personalization = personalization != null ? personalization.clone() : NonceFactory.personalization();
    this.minEntropy = minEntropy;
    this.desiredEntropy = desiredEntropy;
    this.source = source;
  }


  /**
   * Combine the initial materials.
   *
   * @return the initial entropy
   */
  byte[] combineMaterials() {
    SeedSource seedSource = source;
    if (seedSource == null) {
      seedSource = Fortuna.SOURCE;
    }
    if (entropy == null) {
      entropy = seedSource.getSeed(desiredEntropy);
    }
    if (entropy.length < minEntropy) {
      byte[] newEntropy = seedSource.getSeed(minEntropy);
      System.arraycopy(entropy, 0, newEntropy, 0, entropy.length);
      entropy = newEntropy;
    }
    if (nonce == null) {
      nonce = NonceFactory.create();
    }
    // merge material into one block
    int inputLength = entropy.length + nonce.length + personalization.length;
    byte[] seedMaterial = new byte[inputLength];
    System.arraycopy(entropy, 0, seedMaterial, 0, entropy.length);
    System.arraycopy(nonce, 0, seedMaterial, entropy.length, nonce.length);
    System.arraycopy(personalization, 0, seedMaterial, entropy.length + nonce.length, personalization.length);

    // blank the data
    Arrays.fill(entropy, (byte) 0);
    entropy = null;
    Arrays.fill(nonce, (byte) 0);
    nonce = null;

    return seedMaterial;
  }


  public byte[] getPersonalization() {
    return personalization;
  }

}
