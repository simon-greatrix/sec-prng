package prng.collector;

import java.awt.AWTException;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

import prng.generator.HashSpec;
import prng.generator.IsaacRandom;
import prng.utility.Config;
import prng.utility.DigestDataOutput;

/**
 * An entropy collector that sample small random areas of the connected
 * graphical devices.
 * 
 * @author Simon Greatrix
 *
 */
public class AWTEntropy extends EntropyCollector {
    /**
     * Holder for a graphics device and the associated AWT robot
     */
    private static class Sampler {
        /** The graphics device */
        GraphicsDevice device_;

        /** The robot */
        Robot robot_;


        /**
         * New sampler
         * 
         * @param d
         *            the graphics device
         * @throws AWTException
         * @throws SecurityException
         */
        Sampler(GraphicsDevice d) throws AWTException, SecurityException {
            device_ = d;
            robot_ = new Robot(d);
        }


        /**
         * Sample from the graphics device
         * 
         * @param width
         *            desired sample width
         * @param height
         *            desired sample height
         * @return the sample image
         */
        BufferedImage sample(int width, int height) {
            DisplayMode mode = device_.getDisplayMode();
            int scrWidth = mode.getWidth();
            width = Math.min(scrWidth, width);
            int xOff = scrWidth - width;
            Random rand = IsaacRandom.getSharedInstance();
            if( xOff > 0 ) xOff = rand.nextInt(xOff);

            int scrHeight = mode.getHeight();
            height = Math.min(scrHeight, height);
            int yOff = scrHeight - height;
            if( yOff > 0 ) yOff = rand.nextInt(yOff);

            Rectangle rect = new Rectangle(xOff, yOff, width, height);
            try {
                return robot_.createScreenCapture(rect);
            } catch (SecurityException se) {
                return null;
            }
        }
    }

    /**
     * Samplers by graphics devices
     */
    private Sampler[] samplers_;

    /** Selected sample area width */
    private int sampleWidth_ = 50;

    /** Selected sample area height */
    private int sampleHeight_ = 50;


    /**
     * Create new collector
     * 
     * @param config
     *            associated configuration
     */
    public AWTEntropy(Config config) {
        super(config, 1000);
        sampleWidth_ = config.getInt("sampleWidth", 50);
        if( sampleWidth_ <= 0 ) sampleWidth_ = 50;

        sampleHeight_ = config.getInt("sampleHeight", 50);
        if( sampleHeight_ <= 0 ) sampleHeight_ = 50;
    }


    @Override
    protected boolean initialise() {
        // if environment is headless, cannot do any sampling
        if( GraphicsEnvironment.isHeadless() ) return false;

        // get the local environment and again check if it is headless
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if( env.isHeadlessInstance() ) return false;

        // for each screen device
        GraphicsDevice[] screens = env.getScreenDevices();
        ArrayList<Sampler> list = new ArrayList<Sampler>(screens.length);
        for(int i = 0;i < screens.length;i++) {
            try {
                // create and test a sampler
                Sampler samp = new Sampler(screens[i]);
                BufferedImage img = samp.sample(sampleWidth_, sampleHeight_);
                if( img==null ) continue;

                // all OK
                list.add(samp);
            } catch (AWTException e) {
                // expected
                LOG.debug("No entropy collection from "
                        + screens[i].getIDstring());
            } catch (SecurityException e) {
                // also expected
                LOG.debug("Security blocked entropy collection from "
                        + screens[i].getIDstring());
            }
        }

        if( list.isEmpty() ) return false;

        samplers_ = list.toArray(new Sampler[list.size()]);
        return true;
    }


    @Override
    protected void runImpl() {
        // select a screen
        int s = samplers_.length;
        if( s > 1 ) {
            s = IsaacRandom.getSharedInstance().nextInt(s);
        } else {
            s = 0;
        }

        // grab a sample image if security allows us to do so
        BufferedImage image = samplers_[s].sample(sampleWidth_, sampleHeight_);
        if( image == null ) return;

        // compute digest of screen image
        DigestDataOutput output = new DigestDataOutput(
                HashSpec.SPEC_SHA256.getInstance());
        output.writeLong(System.nanoTime());
        int w = image.getWidth();
        int h = image.getHeight();
        for(int x = 0;x < w;x++) {
            for(int y = 0;y < h;y++) {
                output.writeInt(image.getRGB(x, y));
            }
        }
        setEvent(output.digest());
    }
}
