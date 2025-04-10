package prng.image;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Base class for all painters.
 */
public abstract class BasePainter implements Painter {

  BufferedImage myImage;

  Random rand;


  BasePainter() {
    // do nothing
  }


  BasePainter(Random rand) {
    this.rand = rand;
  }


  @Override
  public void create(DoubleConsumer progress) {
    create();
    progress.accept(1d);
  }


  /** Create the image. */
  public void create() {
    throw new UnsupportedOperationException("Not implemented");
  }


  /**
   * Get the image.
   *
   * @return the image
   */
  public BufferedImage getImage() {
    return myImage;
  }


  @Override
  public void paint(Graphics2D graphics) {
    Rectangle bounds = graphics.getClipBounds();
    AffineTransform trans = AffineTransform.getScaleInstance(
        bounds.getWidth() / 512, bounds.getHeight() / 512);
    AffineTransformOp op = new AffineTransformOp(
        trans,
        AffineTransformOp.TYPE_BICUBIC
    );
    graphics.drawImage(
        myImage, op, (int) bounds.getX(),
        (int) bounds.getY()
    );
  }


  @Override
  public void setRandom(Random newRand) {
    rand = newRand;
  }

}
