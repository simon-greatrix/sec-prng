package prng.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import java.util.Random;

/** Create a fractal image file using a provided random number generator */
public class Fractal extends BasePainter {

  /**
   * Number of vertices in the TSP polygon
   */
  public static final int VERTICES = 64;


  /**
   * Create 2D linearly-interpolated Perlin noise
   *
   * @param rand source of randomness
   *
   * @return noise values
   */
  public static int[][] createEqualizedNoise(Random rand) {
    float[][] r = new float[128][128];
    int scale = 64;
    int len = 2;
    while (len <= r.length) {
      // create seed points for the current scale
      float[][] b = new float[len + 1][len + 1];
      for (int x = 0; x <= len; x++) {
        for (int y = 0; y <= len; y++) {
          b[x][y] = rand.nextFloat() * scale;
        }
      }

      // interpolate the seed points to the grid
      int v = 128 / len;
      for (int x = 0; x < len; x++) {
        for (int y = 0; y < len; y++) {
          // corners of the seed points in this square
          float b00 = b[x][y];
          float b01 = b[x][y + 1];
          float b10 = b[x + 1][y];
          float b11 = b[x + 1][y + 1];

          // for each point in the seed square
          for (int i = 0; i < v; i++) {
            float fi = (float) i / v;
            for (int j = 0; j < v; j++) {
              float fj = (float) j / v;
              // calculate local contribution
              r[(x * v) + i][(y * v)
                  + j] += (b00 * (1 - fi) * (1 - fj))
                  + (b10 * fi * (1 - fj))
                  + (b01 * (1 - fi) * fj)
                  + (b11 * fi * fj);
            }
          }
        }
      }
      scale = (int) (scale / 1.5);
      len *= 2;
    }

    // collect the values, so we can work out the equalization points
    float[] values = new float[16384];
    for (int x = 0; x < 128; x++) {
      System.arraycopy(r[x], 0, values, x * 128, 128);
    }
    Arrays.sort(values);
    float[] cutOff = new float[256];
    for (int i = 0; i < 256; i++) {
      float f = values[i * 64];
      cutOff[i] = Math.nextDown(f);
    }

    // rescale up to integer grid
    int[][] ri = new int[128][128];
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        int k = Arrays.binarySearch(cutOff, r[i][j]);
        if (k < 0) {
          k = -k - 1;
        }
        ri[i][j] = k - 1;
      }
    }

    return ri;
  }


  /**
   * Create 2D linearly-interpolated Perlin noise
   *
   * @param rand source of randomness
   *
   * @return noise values
   */
  public static int[][] createNoise(Random rand) {
    float[][] r = new float[128][128];
    int scale = 64;
    int len = 2;
    while (len <= r.length) {
      // create seed points for the current scale
      float[][] b = new float[len + 1][len + 1];
      for (int x = 0; x <= len; x++) {
        for (int y = 0; y <= len; y++) {
          b[x][y] = rand.nextFloat() * scale;
        }
      }

      // interpolate the seed points to the grid
      int v = 128 / len;
      for (int x = 0; x < len; x++) {
        for (int y = 0; y < len; y++) {
          // corners of the seed points in this square
          float b00 = b[x][y];
          float b01 = b[x][y + 1];
          float b10 = b[x + 1][y];
          float b11 = b[x + 1][y + 1];

          // for each point in the seed square
          for (int i = 0; i < v; i++) {
            float fi = (float) i / v;
            for (int j = 0; j < v; j++) {
              float fj = (float) j / v;
              // calculate local contribution
              r[(x * v) + i][(y * v)
                  + j] += (b00 * (1 - fi) * (1 - fj))
                  + (b10 * fi * (1 - fj))
                  + (b01 * (1 - fi) * fj)
                  + (b11 * fi * fj);
            }
          }
        }
      }
      //  scale /= 2;
      scale /= 1.5;
      len *= 2;
    }

    // find max and minimum for rescaling
    float max = r[0][0];
    float min = r[0][0];
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        float v = r[i][j];
        if (v > max) {
          max = v;
        }
        if (v < min) {
          min = v;
        }
      }
    }
    float size = max - min;

    // rescale up to integer grid
    int[][] ri = new int[128][128];
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        ri[i][j] = (int) ((255.0 * (r[i][j] - min)) / size);
      }
    }

    return ri;
  }


  /**
   * Create a route which is a TSP solution for a set of random points in one
   * quarter of the field. The route enters and leaves on different boundaries
   * of the quarter, so by reflections a complete symmetric polygon can be
   * created.
   *
   * @param rand random source
   *
   * @return points for one quarter of the polygon
   */
  public static Point2D[] createPoly(Random rand) {
    // create the vertices
    Point2D[] points = new Point2D[VERTICES + 1];
    for (int i = 0; i < VERTICES; i++) {
      Point2D p;
      boolean isUsed;
      do {
        p = new Point2D.Double(rand.nextInt(256), rand.nextInt(256));
        isUsed = false;
        for (int j = 0; j < i; j++) {
          if (p.equals(points[j])) {
            isUsed = true;
            break;
          }
        }
      } while (isUsed);
      points[i] = p;
    }

    // create the edge vertices at start and end
    points[0].setLocation(256, rand.nextInt(256));
    points[VERTICES] = new Point2D.Double(rand.nextInt(256), 256);

    Point2D s1, e1, s2, e2;

    // repeatedly apply the 2-opt rule to improve the TSP solution
    boolean isImproved = true;
    while (isImproved) {
      isImproved = false;
      outer:
      for (int i = 1; i < (VERTICES - 1); i++) {
        s1 = points[i - 1];
        e1 = points[i];
        double len1 = s1.distance(e1);
        for (int j = i + 1; j < VERTICES; j++) {
          s2 = points[j];
          e2 = points[j + 1];
          double origLen = len1 + s2.distance(e2);
          double newLen = s1.distance(s2) + e1.distance(e2);

          // if new route is shorter, reverse i to j
          if ((newLen < origLen)) {
            while (i < j) {
              Point2D t = points[i];
              points[i] = points[j];
              points[j] = t;
              i++;
              j--;
            }
            isImproved = true;
            break outer;
          }
        }
      }
    }

    return points;
  }


  /**
   * Create a fractal image instance. A random generator must be assigned.
   */
  public Fractal() {
    // do nothing
  }


  /**
   * Create a fractal image using the provided random number generator
   *
   * @param rand source of randomness
   */
  public Fractal(Random rand) {
    super(rand);
  }


  @Override
  public void create() {
    // create image
    BufferedImage image = new BufferedImage(
        512, 512,
        BufferedImage.TYPE_INT_RGB
    );
    Graphics2D graphics = (Graphics2D) image.getGraphics();

    // draw Perlin noise background
    int[][] red = createNoise(rand);
    int[][] green = createNoise(rand);
    int[][] blue = createNoise(rand);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        graphics.setColor(
            new Color(red[i][j], green[i][j], blue[i][j]));
        graphics.fillRect(i * 4, j * 4, 4, 4);
      }
    }

    // create the polygon
    Point2D[] points = createPoly(rand);
    Path2D path = new Path2D.Double(
        Path2D.WIND_NON_ZERO,
        (4 * VERTICES) + 2
    );

    // draw 1st quarter
    path.moveTo(points[0].getX(), points[0].getY());
    for (int i = 1; i < points.length; i++) {
      Point2D p = points[i];
      path.lineTo(p.getX(), p.getY());
    }

    // draw 2nd quarter
    for (int i = points.length - 2; i > 0; i--) {
      Point2D p = points[i];
      path.lineTo(p.getX(), 512 - p.getY());
    }

    // draw 3rd quarter
    for (Point2D p : points) {
      path.lineTo(512 - p.getX(), 512 - p.getY());
    }

    // draw final quarter
    for (int i = points.length - 2; i > 0; i--) {
      Point2D p = points[i];
      path.lineTo(512 - p.getX(), p.getY());
    }
    path.closePath();

    // fill the polygon with a translucent black
    graphics.setColor(new Color(0, 0, 0, 64));
    graphics.fill(path);

    // draw the edge of the polygon in solid black
    graphics.setStroke(new BasicStroke(
        4, BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    ));
    graphics.setColor(Color.BLACK);
    graphics.draw(path);

    // create a 5x5 Gaussian blur filter
    float[] blurMatrix = new float[]{
        1, 4, 7, 4, 1, 4, 16, 26, 16, 4, 7,
        26, 41, 26, 7, 4, 16, 26, 16, 4, 1, 4, 7, 4, 1
    };
    for (int i = 0; i < 25; i++) {
      blurMatrix[i] /= 273f;
    }
    BufferedImageOp op = new ConvolveOp(
        new Kernel(5, 5, blurMatrix),
        ConvolveOp.EDGE_NO_OP, null
    );

    // blur the image
    myImage = op.filter(image, null);
  }

}
