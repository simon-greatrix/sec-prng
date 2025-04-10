package prng.image;

import java.awt.Graphics2D;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Interface for all painters.
 */
public interface Painter {

  /**
   * Create the image.
   *
   * @param progress a consumer to report progress. The values will range from 0 to 1.
   */
  void create(DoubleConsumer progress);


  /**
   * Paint the image.
   *
   * @param graphics where to paint the image
   */
  void paint(Graphics2D graphics);


  /**
   * Set the source of randomness used in creating the image.
   *
   * @param rand the source of randomness
   */
  void setRandom(Random rand);

}
