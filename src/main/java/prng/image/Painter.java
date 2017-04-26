package prng.image;

import java.awt.Graphics2D;
import java.util.Random;
import java.util.function.DoubleConsumer;

public interface Painter {

    public void create(DoubleConsumer progress);


    public void paint(Graphics2D graphics);


    public void setRandom(Random rand);
}
