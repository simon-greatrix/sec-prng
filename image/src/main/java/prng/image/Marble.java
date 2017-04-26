package prng.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

public class Marble extends BasePainter {

    public static class Comb implements Op {
        Op next;

        double sharpness;

        double shift;

        double spacing;

        double unitX;

        double unitY;


        Comb(Op op, Random rand) {
            double angle = Math.PI * 2 * rand.nextDouble();
            unitX = Math.cos(angle);
            unitY = Math.sin(angle);
            shift = 32 + (96 * rand.nextDouble());
            sharpness = 1 + (24 * rand.nextDouble());
            next = op;
            spacing = 256.0 / (10 * rand.nextDouble());
        }


        @Override
        public float getHue(double x, double y) {
            double dist = Math.abs(((x - 256) * unitX) + ((y - 256) * unitY));
            dist = (spacing / 2) - Math.abs((dist % spacing) - (spacing / 2));
            double scale = (shift * sharpness) / (dist + sharpness);
            double nx = x + (scale * unitY);
            double ny = y - (scale * unitX);
            return next.getHue(nx, ny);
        }
    }

    public static class Cross implements Op {

        Op next;


        public Cross(Op op) {
            next = op;
        }


        @Override
        public float getHue(double x, double y) {
            double test = Math.min(Math.abs(x - 256), Math.abs(y - 256));
            return test < 4 ? 0f : next.getHue(x, y);
        }

    }

    public static class Ink implements Op {
        double area;

        double centX;

        double centY;

        float hue;

        Op next;


        Ink(Op ink, double scale, Random rand) {
            next = ink;
            centX = rand.nextInt(512);
            centY = rand.nextInt(512);

            double theta = Math.atan2(centX - 256, centY - 256);
            hue = (float) ((((theta / Math.PI) + 1) / 2))
                    + (0.1f * rand.nextFloat());

            if( ink != null ) {
                float oldHue = ink.getHue(centX, centY);
                if( Math.abs(hue - oldHue) < 0.1 ) {
                    hue = oldHue + ((rand.nextBoolean() ? -1 : 1)
                            * (0.1f + (0.1f * rand.nextFloat())));
                }
            }

            area = 0;
            while( area < scale ) {
                area += 4 * scale * (1 + (2 * rand.nextGaussian()));
            }
        }


        @Override
        public float getHue(double x, double y) {
            double dx = x - centX;
            double dy = y - centY;
            double dist2 = (dx * dx) + (dy * dy);

            if( dist2 <= area ) {
                return hue;
            }
            if( next == null ) {
                return 0;
            }

            double fact = Math.sqrt(1 - (area / dist2));
            double ox = centX + (dx * fact);
            double oy = centY + (dy * fact);
            return 0.1f + next.getHue(ox, oy);
        }
    }

    public interface Op {
        float getHue(double x, double y);
    }

    public class Ripple implements Op {
        double centX;

        double centY;

        double min;

        double wavelength;


        Ripple(Random rand, float min) {
            centX = rand.nextInt(512);
            centY = rand.nextInt(512);
            wavelength = 8 + (24 * rand.nextDouble());
            this.min = min;
        }


        @Override
        public float getHue(double x, double y) {
            double dist = Math.hypot(x - centX, y - centY);
            return (float) (min
                    + ((1 - min) * 0.5 * (1 + Math.cos(dist / wavelength))));
        }
    }

    public static class Wave implements Op {
        double amplitude;

        Op next;

        double unitX;

        double unitY;

        double wavelength;


        public Wave(Op op, Random rand) {
            next = op;
            double angle = Math.PI * 2 * rand.nextDouble();
            unitX = Math.cos(angle);
            unitY = Math.sin(angle);
            amplitude = 8 + (64 * rand.nextDouble());
            wavelength = 8 + (48 * rand.nextDouble());
        }


        @Override
        public float getHue(double x, double y) {
            double dist = ((x - 256) * unitX) + ((y - 256) * unitY);
            double eff = amplitude * Math.sin(dist / wavelength);
            double nx = x - (eff * unitY);
            double ny = y + (eff * unitX);
            return next.getHue(nx, ny);
        }

    }


    public Marble() {
        // do nothing
    }


    public Marble(Random rand) {
        super(rand);
    }


    @Override
    public void create() {
        BufferedImage image = new BufferedImage(512, 512,
                BufferedImage.TYPE_INT_RGB);

        float hueOffset = rand.nextFloat();
        double scale = 1000;
        Op ink = new Ink(null, scale, rand);
        for(int i = 0;i < 100;i++) {
            ink = new Ink(ink, scale, rand);
            scale *= 0.975;
        }
        switch (rand.nextInt(3)) {
        case 0:
            ink = new Wave(ink, rand);
            break;
        case 1:
            ink = new Comb(ink, rand);
            break;
        default:
        }

        Ripple sat = new Ripple(rand, 0.2f);
        Ripple bright = new Ripple(rand, 0.5f);
        for(int x = 0;x < 512;x++) {
            for(int y = 0;y < 512;y++) {
                float hue = ink.getHue(x, y);
                int rgb = Color.HSBtoRGB(hue + hueOffset, sat.getHue(x, y),
                        bright.getHue(x, y));
                image.setRGB(x, y, rgb);
            }
        }

        myImage = image;
    }
}
