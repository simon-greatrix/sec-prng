package prng.collector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;
import prng.EntropySource;

/**
 * A utility output stream where the contents of the stream contribute to system entropy. An application may choose to wrap another output stream with this
 * class so that entropy is contributed to the pool when the stream is closed.
 *
 * @author Simon Greatrix
 */
public class EntropyOutputStream extends CheckedOutputStream {

  /** Entropy source */
  private static final EntropySource ENTROPY = new EntropySource();


  /**
   * Create an EntropyOutputStream that wraps the provided stream
   *
   * @param in the input stream
   */
  public EntropyOutputStream(OutputStream in) {
    super(in, new Adler32());
  }


  @Override
  public void close() throws IOException {
    Checksum cs = getChecksum();
    ENTROPY.setEvent((int) cs.getValue());
    super.close();
  }
}
