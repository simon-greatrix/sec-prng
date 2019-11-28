package prng;

import java.security.SecureRandom;

/**
 * @author Simon Greatrix on 12/12/2017.
 */
public class WorksAsPrimary {
  public static void main(String[] args) throws Exception {
    System.setProperty("prng.logging","true");

    SecureRandomProvider.install(true);

    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];

    System.out.println("Invoking PRNG");

    random.nextBytes(bytes);
    System.out.println("PRNG complete");

    random = SecureRandom.getInstanceStrong();
    System.out.println(random.getAlgorithm());

    System.out.println("Sleeping 5 seconds");
    Thread.currentThread().sleep(5000);
  }
}
