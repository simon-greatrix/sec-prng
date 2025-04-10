package prng.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;


/**
 * <p>Create a coloured Voronoi diagram for 256 cells on a 512x512 grid.</p>
 *
 * <p>Each cell has as center with a unique x co-ordinate. There are more than 2^508 ways of choosing these x co-ordinates.</p>
 *
 * <p>Each cell has a non-unique y co-ordinate. Assigning these uses 256*9 = 2304 bits.</p>
 *
 * <p>Each cell has a color from the 16777216 colors in the sRGB color space. The lower bound for the combinations is (n^k)/(k^k), which is 65536^256,
 * 256*16=40496 bits.</p>
 *
 * <p>The total image entropy is hence at least: 508+2304+4096 = 6908.</p>
 */
public class Voronoi extends BasePainter {

  static class Cell {

    final Point center;

    List<Point> vert = new ArrayList<>();


    Cell(Point center, List<Point> box) {
      this.center = center;
      vert.addAll(box);
      vert.add(vert.get(0));
    }


    void cut(Point o, double len) {
      if (o.equals(center)) {
        return;
      }

      // find which points are closer to o than to the center
      int s = vert.size();

      // test point zero
      Point v = vert.get(0);
      double myDist = v.dist(center);
      double oDist = v.dist(o);
      boolean ok = myDist < oDist;

      // find the entry and exit lines
      boolean previous = ok;
      int entry = -1;
      int exit = -1;
      for (int i = 1; i < s; i++) {
        v = vert.get(i);
        myDist = v.dist(center);
        oDist = v.dist(o);
        boolean c = myDist < oDist;
        ok = ok && c;
        if (c && !previous) {
          exit = i;
        }
        if (previous && !c) {
          entry = i;
        }
        previous = c;
      }

      // if all points closer, we are OK
      if (ok) {
        return;
      }

      // create cutting edge
      Edge cut = new Edge(center, o, len);
      Point entryPoint = cut.intersect(
          vert.get(entry - 1),
          vert.get(entry)
      );
      Point exitPoint = cut.intersect(
          vert.get(exit - 1),
          vert.get(exit)
      );
      List<Point> newVerts = new ArrayList<>();
      newVerts.add(entryPoint);
      newVerts.add(exitPoint);
      int p = exit;
      while (p != entry) {
        newVerts.add(vert.get(p));
        p++;
        if (p == s) {
          p = 1;
        }
      }
      newVerts.add(entryPoint);
      vert = newVerts;
    }


    Point[] getMajorAxis() {
      Point[] best = new Point[2];
      double bestDistance = -1;
      int s = vert.size();
      for (int i = 1; i < s; i++) {
        Point p0 = vert.get(i);
        for (int j = 0; j < i; j++) {
          Point p1 = vert.get(j);
          double d = p0.dist(p1);
          if (d > bestDistance) {
            best[0] = p0;
            best[1] = p1;
            bestDistance = d;
          }
        }
      }
      return best;
    }


    Shape getPoly() {
      int s = vert.size() - 1;
      Path2D.Double ret = new Path2D.Double(Path2D.WIND_NON_ZERO, s);
      Point p = vert.get(0);
      ret.moveTo(p.x, p.y);
      for (int i = 1; i < s; i++) {
        p = vert.get(i);
        ret.lineTo(p.x, p.y);
      }
      ret.closePath();
      return ret;
    }

  }



  private static class Data {

    Cell[] cells;

    Color[] pallette;

    Point[] pnts;

    Shape[] shapes;

  }



  static class Edge {

    Point end;

    Point start;


    Edge(Point a, Point b) {
      start = a;
      end = b;
    }


    Edge(Point a, Point b, double len) {
      start = new Point();
      end = new Point();
      double mx = 0.5 * (a.x + b.x);
      double my = 0.5 * (a.y + b.y);

      double dx = b.x - a.x;
      double dy = b.y - a.y;
      double r = Math.sqrt((dx * dx) + (dy * dy));
      len /= r;
      dx *= len;
      dy *= len;

      start.x = mx - dy;
      start.y = my + dx;

      end.x = mx + dy;
      end.y = my - dx;
    }


    Point intersect(Edge o) {
      return Voronoi.intersect(start, end, o.start, o.end);
    }


    Point intersect(Point s, Point e) {
      return Voronoi.intersect(start, end, s, e);
    }


    void next(Point c) {
      start = end;
      end = c;
    }

  }



  static class Point {

    double x, y;


    Point() {
    }


    Point(double x, double y) {
      this.x = x;
      this.y = y;
    }


    Point2D as2D() {
      return new Point2D.Double(x, y);
    }


    double dist(Point p) {
      double dx = p.x - x;
      double dy = p.y - y;
      return Math.sqrt((dx * dx) + (dy * dy));
    }


    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Point other = (Point) obj;
      return Double.doubleToLongBits(x) == Double.doubleToLongBits(
          other.x) && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
    }


    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = (prime * result) + Double.hashCode(x);
      result = (prime * result) + Double.hashCode(y);
      return result;
    }

  }


  private static double dist(Color a, Color b) {
    int r = a.getRed() - b.getRed();
    int g = a.getGreen() - b.getGreen();
    int l = a.getBlue() - b.getBlue();
    return Math.sqrt((r * r) + (g * g) + (l * l));
  }


  /**
   * Find the point where a line from p0 to p1 meets a line from p2 to p3.
   *
   * @param p0 start of first line
   * @param p1 end of first line
   * @param p2 start of second line
   * @param p3 end of second line
   *
   * @return point of intersection or null if no such point
   */
  static Point intersect(Point p0, Point p1, Point p2, Point p3) {
    double s1x = p1.x - p0.x;
    double s1y = p1.y - p0.y;
    double s2x = p3.x - p2.x;
    double s2y = p3.y - p2.y;

    double z = (-s2x * s1y) + (s1x * s2y);
    if (z == 0) {
      return null;
    }

    double s = ((-s1y * (p0.x - p2.x)) + (s1x * (p0.y - p2.y))) / z;
    double t = ((s2x * (p0.y - p2.y)) - (s2y * (p0.x - p2.x))) / z;

    if ((s >= 0) && (s <= 1) && (t >= 0) && (t <= 1)) {
      Point p = new Point();
      p.x = p0.x + (t * s1x);
      p.y = p0.y + (t * s1y);
      return p;
    }

    return null;
  }


  private final int points = 256;

  private boolean doingDots = false;


  /**
   * New instance.
   *
   * @param rand source of randomness
   */
  public Voronoi(Random rand) {
    this.rand = rand;
  }


  @Override
  public void create(DoubleConsumer progress) {
    Data data = new Data();
    make(data);
    int size = points;

    // create palette
    createPallette(data);

    // create image
    BufferedImage image = new BufferedImage(
        512, 512,
        BufferedImage.TYPE_INT_RGB
    );
    myImage = image;
    Graphics2D graphics = (Graphics2D) image.getGraphics();
    graphics.setStroke(new BasicStroke(
        1, BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    ));

    Shape[] shapes = getPolys(data);
    for (int i = 0; i < size; i++) {
      Shape s = shapes[i];
      graphics.setColor(data.pallette[i]);
      graphics.fill(s);
    }
    progress.accept(0.4);

    // draw Perlin noise background
    int[][] white = Fractal.createEqualizedNoise(rand);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        graphics.setColor(
            new Color(white[i][j], white[i][j], white[i][j], 96));
        graphics.fillRect(i * 4, j * 4, 4, 4);
      }
    }

    if (doingDots) {
      doDots(data, graphics);
    }
    progress.accept(0.9);

    for (int i = 0; i < size; i++) {
      Shape s = shapes[i];
      graphics.setColor(Color.WHITE);
      graphics.draw(s);
    }

    // create a 3x3 Gaussian blur filter
    float[] blurMatrix = new float[]{
        1f / 16, 1f / 8, 1f / 16, 1f / 8,
        1f / 4, 1f / 8, 1f / 16, 1f / 8, 1f / 16
    };
    BufferedImageOp op = new ConvolveOp(
        new Kernel(3, 3, blurMatrix),
        ConvolveOp.EDGE_NO_OP, null
    );

    // blur the image
    myImage = op.filter(image, null);

    progress.accept(1.0);
  }


  void createPallette(Data data) {
    int size = points;
    Color[] pal = new Color[size];
    for (int i = 0; i < size; i++) {
      pal[i] = new Color(rand.nextInt(0x1000000));
    }

    // create color difference matrix
    double[][] colorDiff = new double[size][size];
    for (int i = 1; i < size; i++) {
      for (int j = 0; j < i; j++) {
        double diff = dist(pal[i], pal[j]);
        colorDiff[i][j] = diff;
        colorDiff[j][i] = diff;
      }
    }

    // create xy difference matrix
    double[][] xyDiff = new double[size][size];
    for (int i = 1; i < size; i++) {
      for (int j = 0; j < i; j++) {
        double diff = data.pnts[i].dist(data.pnts[j]);
        diff *= diff;
        xyDiff[i][j] = diff;
        xyDiff[j][i] = diff;
      }
    }

    // color permutation
    int[] perm = new int[size];
    for (int i = 0; i < size; i++) {
      perm[i] = i;
    }

    // Do some sorting, but not enough to completely order it.
    for (int a = 1; a < size; a++) {
      for (int b = 0; b < a; b++) {
        int ca = perm[a];
        int cb = perm[b];

        // does swapping a and b improve things?
        double change = 0;
        for (int i = 0; i < size; i++) {
          int ci = perm[i];
          if ((a == i) || (b == i)) {
            continue;
          }
          change -= colorDiff[ca][ci] / xyDiff[a][i];
          change -= colorDiff[cb][ci] / xyDiff[b][i];
          change += colorDiff[cb][ci] / xyDiff[a][i];
          change += colorDiff[ca][ci] / xyDiff[b][i];
        }

        // if change is negative, it is better
        if (change < 0) {
          int t = perm[a];
          perm[a] = perm[b];
          perm[b] = t;
        }
      }
    }

    data.pallette = new Color[size];
    for (int i = 0; i < size; i++) {
      data.pallette[i] = pal[perm[i]];
    }
  }


  private void doDots(Data data, Graphics2D graphics) {
    for (int x = 0; x < 23; x++) {
      for (int y = 0; y < 23; y++) {
        // 529 bits of entropy just here!
        if (rand.nextBoolean()) {
          continue;
        }

        Shape shape = null;
        Color col = null;
        Point2D p = new Point2D.Double();
        do {
          p.setLocation(512 * (x + rand.nextDouble()) / 23, 512 * (y + rand.nextDouble()) / 23);
          for (int i = 0; i < points; i++) {
            Shape s = data.shapes[i];
            if (s.contains(p)) {
              shape = s;
              col = data.pallette[i];
              break;
            }
          }
        } while (shape == null);

        float[] rgba = col.getRGBComponents(null);
        int r = rand.nextInt(3);
        float c = rgba[r];

        modify(rgba, 0);
        modify(rgba, 1);
        modify(rgba, 2);

        float min = 0.18f;
        if (c < min) {
          c += min;
        } else if (c + min > 1.0f) {
          c -= min;
        } else if (rand.nextBoolean()) {
          c += min;
        } else {
          c -= min;
        }
        rgba[r] = c;

        graphics.setClip(shape);

        int radius = 8 + rand.nextInt(16);
        float[] fractions = new float[]{0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
        float[] alpha = new float[]{1, 0.976f, 0.905f, 0.794f, 0.655f, 0.5f, 0.345f, 0.206f, 0.095f, 0.024f, 0};

        Color[] colors = new Color[11];
        for (int i = 0; i < 11; i++) {
          colors[i] = new Color(rgba[0], rgba[1], rgba[2], alpha[i]);
        }
        RadialGradientPaint gradient = new RadialGradientPaint(p, radius, fractions, colors);
        graphics.setPaint(gradient);
        Shape oval = new Ellipse2D.Double(p.getX() - radius, p.getY() - radius, 2 * radius, 2 * radius);
        graphics.fill(oval);
        graphics.setClip(null);
      }
    }
  }


  private Shape[] getPolys(Data data) {
    Cell[] cells = data.cells;
    Shape[] ret = new Shape[cells.length];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = cells[i].getPoly();
    }
    data.shapes = ret;
    return ret;
  }


  private void make(Data data) {
    List<Point> bbox2 = new ArrayList<>(4);
    double x = 0;
    double y = 0;
    double w = 512;
    double h = 512;
    bbox2.add(new Point(x, y));
    bbox2.add(new Point(x + w, y));
    bbox2.add(new Point(x + w, y + h));
    bbox2.add(new Point(x, y + h));
    double scale = w + h;

    // Set the points. Ensure the x co-ords are unique to prevent to cells being on top of one another.
    boolean[] xcoords = new boolean[512];
    Point[] pnts = new Point[points];
    data.pnts = pnts;
    for (int i = 0; i < points; i++) {
      int xc;
      do {
        pnts[i] = new Point(x + (w * rand.nextDouble()), y + (h * rand.nextDouble()));
        xc = (int) pnts[i].x;
      } while (xcoords[xc]);
      xcoords[xc] = true;
    }

    // make the cells
    Cell[] cells = new Cell[points];
    data.cells = cells;
    for (int i = 0; i < points; i++) {
      Cell c = new Cell(pnts[i], bbox2);
      for (int j = 0; j < points; j++) {
        c.cut(pnts[j], scale);
      }
      cells[i] = c;
    }
  }


  private void modify(float[] rgba, int index) {
    float f = rgba[index];
    f *= 0.75f + 0.5f * rand.nextFloat();
    if (f < 0) {
      f = 0;
    }
    if (f > 1.0f) {
      f = 1.0f;
    }
    rgba[index] = f;
  }


  private Color nudge(Color col) {
    float[] rgba = col.getRGBComponents(null);
    int r = rand.nextInt(3);
    if (rgba[r] < 0.5f) {
      return col.brighter();
    }
    return col.darker();
  }


  private Color nudge2(Color col) {
    float[] rgba = col.getRGBComponents(null);
    int r = rand.nextInt(3);
    float c = rgba[r];

    modify(rgba, 0);
    modify(rgba, 1);
    modify(rgba, 2);

    float min = 0.18f;
    if (c < min) {
      c += min;
    } else if (c + min > 1.0f) {
      c -= min;
    } else if (rand.nextBoolean()) {
      c += min;
    } else {
      c -= min;
    }
    rgba[r] = c;
    return new Color(rgba[0], rgba[1], rgba[2]);
  }


  /**
   * Set whether dots are drawn on top of the Voronoi diagram.
   *
   * @param is true if dots should be drawn
   */
  public void setDoingDots(boolean is) {
    doingDots = is;
  }

}
