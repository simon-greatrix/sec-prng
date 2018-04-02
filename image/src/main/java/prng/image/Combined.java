package prng.image;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Random;
import java.util.function.DoubleConsumer;

public class Combined implements Painter {
    static class Progress {
        final double[] progress = new double[4];

        double total = 0;

        final DoubleConsumer cons;


        Progress(DoubleConsumer cons) {
            this.cons = cons;
        }


        void update(int i, double p) {
            total -= progress[i];
            progress[i] = p;
            total += progress[i];
            cons.accept(total / 4);
        }
    }

    Painter[] paintings = new Painter[4];


    public Combined(Random rand) {
        paintings[0] = new Fractal(rand);
        paintings[1] = new Voronoi(rand);
        paintings[2] = new Letters(rand);
        paintings[3] = new Marble(rand);
    }


    @Override
    public void create(DoubleConsumer cons) {
        Progress prog = new Progress(cons);

        for(int i = 0;i < 4;i++) {
            Painter p = paintings[i];
            if( p != null ) {
                final int j = i;
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        p.create(d -> prog.update(j, d));
                        prog.update(j, 1);
                    }
                };
                t.start();
            } else {
                prog.update(i, 1);
            }
        }
    }


    @Override
    public void paint(Graphics2D graphics) {
        Rectangle outer = graphics.getClipBounds();
        int x = (int) outer.getX();
        int y = (int) outer.getY();
        int width = (int) outer.getWidth() / 2;
        int height = (int) outer.getHeight() / 2;

        for(int i = 0;i < 2;i++) {
            for(int j = 0;j < 2;j++) {
                int k = (i * 2) + j;
                Painter p = paintings[k];
                if( p == null ) {
                    continue;
                }

                graphics.setClip(x + (width * i), y + (height * j), width,
                        height);
                p.paint(graphics);
            }
        }
    }


    @Override
    public void setRandom(Random newRand) {
        for(Painter p:paintings) {
            p.setRandom(newRand);
        }
    }
}
