package prng.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.DoubleConsumer;


/**
 * <p>Creates a display of at least 87 letters selected from a set of 59 characters. 59^87 exceeds 2^512.</p>
 *
 * <p>Each character has a color selected from 1536 possibles, </p>
 */
public class Letters extends BasePainter {

  /** Selected visually distinct characters */
  private static final String CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabdefghijnpqrt$%&*+@#?{";


  private static Color col(float r) {
    r *= 6;
    int s = (int) r;
    r -= s;
    switch (s) {
      case 0:
        return new Color(1, r, 0);
      case 1:
        return new Color(1 - r, 1f, 0);
      case 2:
        return new Color(0, 1, r);
      case 3:
        return new Color(0, 1 - r, 1);
      case 4:
        return new Color(r, 0, 1);
      case 5:
        return new Color(1, 0, 1 - r);
      default:
        return Color.WHITE;
    }
  }


  /**
   * Create a new instance.
   *
   * @param rand source of randomness
   */
  public Letters(Random rand) {
    super(rand);
  }


  @Override
  public void create(DoubleConsumer progress) {
    // Select a font
    GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
    String[] families = e.getAvailableFontFamilyNames();
    ArrayList<Font> fonts = new ArrayList<>();
    for (String f : families) {
      Font font = new Font(f, Font.PLAIN, 12);
      if (font.canDisplayUpTo(CHARS) != -1) {
        continue;
      }
      fonts.add(font);
    }

    ArrayList<Area> drawn = new ArrayList<>();
    ArrayList<Rectangle2D> bounds = new ArrayList<>();
    BufferedImage image = new BufferedImage(
        512, 512,
        BufferedImage.TYPE_INT_RGB
    );

    myImage = image;

    Graphics2D graphics = (Graphics2D) image.getGraphics();
    graphics.setStroke(new BasicStroke(1));
    Stroke fatStroke = new BasicStroke(3);
    FontRenderContext frc = graphics.getFontRenderContext();

    int placed;
    do {
      placed = 0;
      graphics.clearRect(0, 0, 512, 512);

      Font font = fonts.get(rand.nextInt(fonts.size()));

      int count = 0;
      double scale = 512;
      while (scale >= 16) {
        double xo = 0;
        while (xo < 512) {
          double yo = 0;
          while (yo < 512) {
            int chr = rand.nextInt(CHARS.length());
            GlyphVector vec = font.createGlyphVector(frc, CHARS.substring(chr, chr + 1));
            Shape outline = vec.getGlyphOutline(0);
            outline = AffineTransform.getRotateInstance(Math.PI * 2 * rand.nextDouble()).createTransformedShape(outline);
            Rectangle2D box = outline.getBounds2D();
            double xs = scale / box.getWidth();
            double ys = scale / box.getHeight();
            double as = (Math.min(xs, ys) * 7) / 8;
            outline = AffineTransform.getScaleInstance(as, as).createTransformedShape(outline);
            box = outline.getBounds2D();
            outline = AffineTransform.getTranslateInstance(
                (xo + (0.5 * (scale - box.getWidth()))) - box.getX(),
                (yo + (0.5 * (scale - box.getHeight()))) - box.getY()
            ).createTransformedShape(outline);
            Area letter = new Area(fatStroke.createStrokedShape(outline));
            letter.add(new Area(outline));
            box = letter.getBounds2D();
            boolean noHit = true;
            for (int i = 0; i < drawn.size(); i++) {
              // quick check on bounding rectangles
              Rectangle2D b = bounds.get(i);
              if (b.intersects(box)) {
                // possible area overlap
                Area a = drawn.get(i);
                Area l2 = (Area) letter.clone();
                l2.intersect(a);
                if (!l2.isEmpty()) {
                  noHit = false;
                  break;
                }
              }
            }
            if (noHit) {
              float r = rand.nextFloat();
              graphics.setColor(col(r));
              graphics.fill(outline);
              graphics.draw(outline);
              drawn.add(letter);
              bounds.add(box);
              placed++;
            }

            count++;
            progress.accept(count / 1365.0);

            yo += scale;
          }
          xo += scale;
        }
        scale /= 2;
      }

      // We have 59 characters to choose from and 59^86 < 2^512 < 59^87. Hence, we require at least 87 characters be placed to guarantee we have
      // enough bits on the image.
    } while (placed < 87);
  }

}
