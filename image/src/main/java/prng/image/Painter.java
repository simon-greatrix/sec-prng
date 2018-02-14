package prng.image;

import java.awt.Graphics2D;
import java.util.Random;
import java.util.function.DoubleConsumer;

public interface Painter {

    void create(DoubleConsumer progress);


    void paint(Graphics2D graphics);


    void setRandom(Random rand);
}
