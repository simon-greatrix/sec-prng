package prng.image;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
        public int getHue(double x, double y) {
            double dist = Math.abs(((x - 256) * unitX) + ((y - 256) * unitY));
            dist = (spacing / 2) - Math.abs((dist % spacing) - (spacing / 2));
            double scale = (shift * sharpness) / (dist + sharpness);
            double nx = x + (scale * unitY);
            double ny = y - (scale * unitX);
            return next.getHue(nx, ny);
        }
    }


    public static class Ink implements Op {
        double area;

        double centX;

        double centY;

        int hue;

        Op next;


        Ink(Op ink, double scale, Random rand) {
            next = ink;
            centX = rand.nextInt(512);
            centY = rand.nextInt(512);

            double theta = Math.atan2(centX - 256, centY - 256);
            hue = rand.nextInt(128);

            if( ink != null ) {
                while( hue == ink.getHue(centX, centY) ) {
                    hue = rand.nextInt(128);
                }
            }

            area = 0;
            while( area < scale ) {
                area += 4 * scale * (1 + (2 * rand.nextGaussian()));
            }
        }


        @Override
        public int getHue(double x, double y) {
            double dx = x - centX;
            double dy = y - centY;
            double dist2 = (dx * dx) + (dy * dy);

            if( dist2 <= area ) {
                return hue;
            }
            if( next == null ) {
                return 128;
            }

            double fact = Math.sqrt(1 - (area / dist2));
            double ox = centX + (dx * fact);
            double oy = centY + (dy * fact);
            return next.getHue(ox, oy);
        }
    }

    public interface Op {
        int getHue(double x, double y);
    }

    public class Ripple {
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


        public float getModifier(double x, double y) {
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
        public int getHue(double x, double y) {
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
        double area = ((Ink) ink).area;
        for(int i = 0;i < 100;i++) {
            ink = new Ink(ink, scale, rand);
            area += ((Ink) ink).area;
            scale *= 0.975;
        }
        
        /*
        switch (rand.nextInt(3)) {
        case 0:
            ink = new Wave(ink, rand);
            break;
        case 1:
            ink = new Comb(ink, rand);
            break;
        default:
        }
        */

        double size = Math.sqrt(area);
        Rectangle2D bound = new Rectangle2D.Double(256-size,256-size,size*2,size*2);
        System.out.println(bound);
        
        int[] pallette = new int[129];
        float base = rand.nextFloat();
        for(int i0=0;i0<4;i0++) {
            float s = 1.0f - 0.1f * i0;
            for(int i1=0;i1<4;i1++) {
                float b = 1.0f - 0.1f * i1;
                int o = 8*(i0*4+i1);
                pallette[o] = Color.HSBtoRGB(base,s,b);
                pallette[o+1] = Color.HSBtoRGB(base+0.08f,s,b);
                pallette[o+2] = Color.HSBtoRGB(base+0.16f,s,b);
                pallette[o+3] = Color.HSBtoRGB(base+0.24f,s,b);
                pallette[o+4] = Color.HSBtoRGB(base+0.5f,s,b);
                pallette[o+5] = Color.HSBtoRGB(base+0.58f,s,b);
                pallette[o+6] = Color.HSBtoRGB(base+0.66f,s,b);
                pallette[o+7] = Color.HSBtoRGB(base+0.74f,s,b);
            }
        }
        pallette[128]=0;
        
        Ripple sat = new Ripple(rand, 0.2f);
        Ripple bright = new Ripple(rand, 0.5f);
        for(int x = 0;x < 512;x++) {
            for(int y = 0;y < 512;y++) {
                double x0 = bound.getMinX() + x * bound.getWidth() / 512;
                double y0 = bound.getMinY() + y * bound.getHeight() / 512;
                int hue = ink.getHue(x0, y0);
                int rgb = pallette[hue];
//                int rgb = Color.HSBtoRGB(hue + hueOffset, sat.getHue(x, y),
//                        bright.getHue(x, y));
                image.setRGB(x, y, rgb);
            }
        }

        myImage = image;
    }
}
