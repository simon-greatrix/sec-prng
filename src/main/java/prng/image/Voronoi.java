package prng.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
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

public class Voronoi extends BasePainter {

    static class Cell {
        Point center_;

        List<Point> vert_ = new ArrayList<Point>();


        Cell(Point center, List<Point> box) {
            center_ = center;
            vert_.addAll(box);
            vert_.add(vert_.get(0));
        }


        void cut(Point o, double len) {
            if( o.equals(center_) ) {
                return;
            }

            // find which points are closer to o than to the center
            int s = vert_.size();

            // test point zero
            Point v = vert_.get(0);
            double myDist = v.dist(center_);
            double oDist = v.dist(o);
            boolean ok = myDist < oDist;

            // find the entry and exit lines
            boolean previous = ok;
            int entry = -1;
            int exit = -1;
            for(int i = 1;i < s;i++) {
                v = vert_.get(i);
                myDist = v.dist(center_);
                oDist = v.dist(o);
                boolean c = myDist < oDist;
                ok = ok && c;
                if( c && !previous ) {
                    exit = i;
                }
                if( previous && !c ) {
                    entry = i;
                }
                previous = c;
            }

            // if all points closer, we are OK
            if( ok ) {
                return;
            }

            // create cutting edge
            Edge cut = new Edge(center_, o, len);
            Point entryPoint = cut.intersect(vert_.get(entry - 1),
                    vert_.get(entry));
            Point exitPoint = cut.intersect(vert_.get(exit - 1),
                    vert_.get(exit));
            List<Point> newVerts = new ArrayList<Point>();
            newVerts.add(entryPoint);
            newVerts.add(exitPoint);
            int p = exit;
            while( p != entry ) {
                newVerts.add(vert_.get(p));
                p++;
                if( p == s ) {
                    p = 1;
                }
            }
            newVerts.add(entryPoint);
            vert_ = newVerts;
        }


        Shape getPoly() {
            int s = vert_.size() - 1;
            Path2D.Double ret = new Path2D.Double(Path2D.WIND_NON_ZERO, s);
            Point p = vert_.get(0);
            ret.moveTo(p.x, p.y);
            for(int i = 1;i < s;i++) {
                p = vert_.get(i);
                ret.lineTo(p.x, p.y);
            }
            ret.closePath();
            return ret;
        }
    }

    static class Edge {
        Point e_;

        Point s_;


        Edge(Point a, Point b) {
            s_ = a;
            e_ = b;
        }


        Edge(Point a, Point b, double len) {
            s_ = new Point();
            e_ = new Point();
            double mx = 0.5 * (a.x + b.x);
            double my = 0.5 * (a.y + b.y);

            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double r = Math.sqrt((dx * dx) + (dy * dy));
            len /= r;
            dx *= len;
            dy *= len;

            s_.x = mx - dy;
            s_.y = my + dx;

            e_.x = mx + dy;
            e_.y = my - dx;
        }


        Point intersect(Edge o) {
            return Voronoi.intersect(s_, e_, o.s_, o.e_);
        }


        Point intersect(Point s, Point e) {
            return Voronoi.intersect(s_, e_, s, e);
        }


        void next(Point c) {
            s_ = e_;
            e_ = c;
        }
    }

    static class Point {
        double x, y;


        Point() {}


        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }


        double dist(Point p) {
            double dx = p.x - x;
            double dy = p.y - y;
            return Math.sqrt((dx * dx) + (dy * dy));
        }


        @Override
        public boolean equals(Object obj) {
            if( this == obj ) {
                return true;
            }
            if( obj == null ) {
                return false;
            }
            if( getClass() != obj.getClass() ) {
                return false;
            }
            Point other = (Point) obj;
            if( Double.doubleToLongBits(x) != Double.doubleToLongBits(
                    other.x) ) {
                return false;
            }
            if( Double.doubleToLongBits(y) != Double.doubleToLongBits(
                    other.y) ) {
                return false;
            }
            return true;
        }


        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = (prime * result) + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = (prime * result) + (int) (temp ^ (temp >>> 32));
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
     * @param p0
     *            start of first line
     * @param p1
     *            end of first line
     * @param p2
     *            start of second line
     * @param p3
     *            end of second line
     * @return point of intersection or null if no such point
     */
    static Point intersect(Point p0, Point p1, Point p2, Point p3) {
        double s1x = p1.x - p0.x;
        double s1y = p1.y - p0.y;
        double s2x = p3.x - p2.x;
        double s2y = p3.y - p2.y;

        double z = (-s2x * s1y) + (s1x * s2y);
        if( z == 0 ) {
            return null;
        }

        double s = ((-s1y * (p0.x - p2.x)) + (s1x * (p0.y - p2.y))) / z;
        double t = ((s2x * (p0.y - p2.y)) - (s2y * (p0.x - p2.x))) / z;

        if( (s >= 0) && (s <= 1) && (t >= 0) && (t <= 1) ) {
            Point p = new Point();
            p.x = p0.x + (t * s1x);
            p.y = p0.y + (t * s1y);
            return p;
        }

        return null;
    }

    Cell[] cells;

    Point[] pnts;

    int points;


    public Voronoi(int points, Random rand) {
        this.rand = rand;
        this.points = points;
    }


    @Override
    public void create() {
        make();
        int size = points;

        // create palette
        Color[] pal = new Color[size];
        for(int i = 0;i < size;i++) {
            pal[i] = new Color(rand.nextInt(0x1000000));
        }

        // create color difference matrix
        double[][] colorDiff = new double[size][size];
        for(int i = 1;i < size;i++) {
            for(int j = 0;j < i;j++) {
                double diff = dist(pal[i], pal[j]);
                colorDiff[i][j] = diff;
                colorDiff[j][i] = diff;
            }
        }

        // create xy difference matrix
        double[][] xyDiff = new double[size][size];
        for(int i = 1;i < size;i++) {
            for(int j = 0;j < i;j++) {
                double diff = pnts[i].dist(pnts[j]);
                diff *= diff;
                xyDiff[i][j] = diff;
                xyDiff[j][i] = diff;
            }
        }

        // color permutation
        int[] perm = new int[size];
        for(int i = 0;i < size;i++) {
            perm[i] = i;
        }

        // do some sorting
        boolean didChange = false;
        do {
            for(int a = 1;a < size;a++) {
                for(int b = 0;b < a;b++) {
                    int ca = perm[a];
                    int cb = perm[b];

                    // does swapping a and b improve things?
                    double change = 0;
                    for(int i = 0;i < size;i++) {
                        int ci = perm[i];
                        if( (a == i) || (b == i) ) {
                            continue;
                        }
                        change -= colorDiff[ca][ci] / xyDiff[a][i];
                        change -= colorDiff[cb][ci] / xyDiff[b][i];
                        change += colorDiff[cb][ci] / xyDiff[a][i];
                        change += colorDiff[ca][ci] / xyDiff[b][i];
                    }

                    // if change is negative, it is better
                    if( change < 0 ) {
                        int t = perm[a];
                        perm[a] = perm[b];
                        perm[b] = t;
                    }

                }
            }
        } while( didChange );

        // create image
        BufferedImage image = new BufferedImage(512, 512,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        Shape[] shapes = getPolys();
        for(int i = 0;i < size;i++) {
            Shape s = shapes[i];
            Color col = pal[perm[i]];
            graphics.setColor(col);
            graphics.fill(s);
            graphics.setColor(Color.WHITE);
            graphics.draw(s);
        }

        for(int j = 0;j < (100 * size);j++) {
            Point2D p = new Point2D.Double();
            p.setLocation(512 * rand.nextDouble(), 512 * rand.nextDouble());
            double radius = Math.abs((3 + rand.nextGaussian()));
            Color col = new Color(rand.nextBoolean() ? 1f : 0f,
                    rand.nextBoolean() ? 1f : 0f, rand.nextBoolean() ? 1f : 0f,
                    0.1f);
            Ellipse2D dot = new Ellipse2D.Double(p.getX() - radius,
                    p.getY() - radius, 2 * radius, 2 * radius);
            graphics.setColor(col);
            graphics.fill(dot);
        }

        // create a 3x3 Gaussian blur filter
        float[] blurMatrix = new float[] { 1f / 16, 1f / 8, 1f / 16, 1f / 8,
                1f / 4, 1f / 8, 1f / 16, 1f / 8, 1f / 16 };
        BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, blurMatrix),
                ConvolveOp.EDGE_NO_OP, null);

        // blur the image
        myImage = op.filter(image, null);
    }


    public Shape[] getPolys() {
        Shape[] ret = new Shape[cells.length];
        for(int i = 0;i < ret.length;i++) {
            ret[i] = cells[i].getPoly();
        }
        return ret;
    }


    public void make() {
        List<Point> bbox2 = new ArrayList<Point>(4);
        double x = 0;
        double y = 0;
        double w = 512;
        double h = 512;
        bbox2.add(new Point(x, y));
        bbox2.add(new Point(x + w, y));
        bbox2.add(new Point(x + w, y + h));
        bbox2.add(new Point(x, y + h));
        double scale = w + h;

        // set the points
        pnts = new Point[points];
        for(int i = 0;i < points;i++) {
            pnts[i] = new Point(x + (w * rand.nextDouble()),
                    y + (h * rand.nextDouble()));
        }

        // make the cells
        cells = new Cell[points];
        for(int i = 0;i < points;i++) {
            Cell c = new Cell(pnts[i], bbox2);
            for(int j = 0;j < points;j++) {
                c.cut(pnts[j], scale);
            }
            cells[i] = c;
        }
    }
}
