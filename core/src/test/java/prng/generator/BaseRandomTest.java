package prng.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import prng.SecureRandomProvider;

/**
 * @author Simon Greatrix on 20/10/2022.
 */
class BaseRandomTest {

  @Test
  void test() throws NoSuchAlgorithmException, NoSuchProviderException, InterruptedException {
    SecureRandomProvider.install(true);
    List<String> algos = List.of(
        "Nist/SHA-256",
        "Nist/HmacSHA-256",
        "Nist/AES"
    );

    for (String algo : algos) {
      SecureRandom random = SecureRandom.getInstance(algo, SecureRandomProvider.NAME);

      HashSet<Long> values = new HashSet<>();
      Thread[] threads = new Thread[4];
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(() -> {
          HashSet<Long> myValues = new HashSet<>();
          for (int j = 0; j < 10_000; j++) {
            myValues.add(random.nextLong());
          }
          synchronized (values) {
            values.addAll(myValues);
          }
        });
        threads[i].start();
      }

      for (Thread t : threads) {
        t.join();
      }

      assertEquals(40_000, values.size());
    }
  }

}