package prng;

import java.security.SecureRandomParameters;

/**
 * An interface that makes the protected methods of the SecureRandomSpi class public.
 *
 * @author Simon Greatrix on 19/10/2022.
 */
public interface OpenEngineSpi {

  byte[] engineGenerateSeed(int size);


  SecureRandomParameters engineGetParameters();


  void engineNextBytes(byte[] bytes, SecureRandomParameters params);


  void engineNextBytes(byte[] bytes);


  void engineReseed(SecureRandomParameters params);


  void engineSetSeed(byte[] seed);


  String getAlgorithm();


  byte[] newSeed();

}
