package prng.collector;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;
import prng.EntropySource;

/**
 * A utility input stream where the contents of the stream contribute to system entropy. An application may choose to wrap another input stream with this class
 * so that entropy is contributed to the pool when the stream is closed.
 *
 * @author Simon Greatrix
 */
public class EntropyInputStream extends CheckedInputStream {

  /** Entropy source */
  private static final EntropySource ENTROPY = new EntropySource();


  /**
   * Create an EntropyInputStream that wraps the provided stream
   *
   * @param in the input stream
   */
  public EntropyInputStream(InputStream in) {
    super(in, new Adler32());
  }


  @Override
  public void close() throws IOException {
    Checksum cs = getChecksum();
    ENTROPY.setEvent((int) cs.getValue());
    super.close();
  }
}
