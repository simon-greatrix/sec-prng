package prng.collector;

import java.awt.AWTException;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Random;

import prng.SecureRandomProvider;
import prng.config.Config;
import prng.generator.HashSpec;
import prng.generator.IsaacRandom;
import prng.utility.DigestDataOutput;

/**
 * An entropy collector that sample small random areas of the connected graphical devices. If a security manager is in use this collector may require the
 * "createRobot" and "readDisplayPixels" privileges.
 *
 * @author Simon Greatrix
 */
public class AWTEntropy extends EntropyCollector {

  /**
   * Holder for a graphics device and the associated AWT robot
   */
  private static class Sampler {

    /** The graphics device */
    final GraphicsDevice device;

    /** The robot */
    final Robot robot;


    /**
     * New sampler
     *
     * @param d the graphics device
     */
    Sampler(GraphicsDevice d) throws AWTException, SecurityException {
      device = d;
      try {
        robot = AccessController.doPrivileged((PrivilegedExceptionAction<Robot>) () -> new Robot(device));
      } catch (PrivilegedActionException e) {
        Exception cause = e.getException();
        if (cause instanceof AWTException) {
          throw (AWTException) cause;
        }

        // SecurityException and RuntimeException are not checked exceptions so whilst they may happen, they should not come here.
        EntropyCollector.LOG.error("Undeclared throwable in AWTEntropy", e.getCause());
        throw new UndeclaredThrowableException(e.getCause());
      }
    }


    /**
     * Sample from the graphics device
     *
     * @param width  desired sample width
     * @param height desired sample height
     *
     * @return the sample image
     */
    BufferedImage sample(int width, int height) {
      DisplayMode mode = device.getDisplayMode();
      int scrWidth = mode.getWidth();
      width = Math.min(scrWidth, width);
      int xOff = scrWidth - width;
      Random rand = IsaacRandom.getSharedInstance();
      if (xOff > 0) {
        xOff = rand.nextInt(xOff);
      }

      int scrHeight = mode.getHeight();
      height = Math.min(scrHeight, height);
      int yOff = scrHeight - height;
      if (yOff > 0) {
        yOff = rand.nextInt(yOff);
      }

      Rectangle rect = new Rectangle(xOff, yOff, width, height);
      try {
        // use privileges to read the screen
        return AccessController.doPrivileged((PrivilegedAction<BufferedImage>) () -> robot.createScreenCapture(rect));
      } catch (SecurityException e) {
        // expected
        return null;
      }
    }

  }

  private final MessageDigest digest = HashSpec.SPEC_SHA256.getInstance();

  /** Selected sample area height */
  private int sampleHeight;

  /** Selected sample area width */
  private int sampleWidth;

  /**
   * Samplers by graphics devices
   */
  private Sampler[] samplers;


  /**
   * Create new collector
   *
   * @param config associated configuration
   */
  public AWTEntropy(Config config) {
    super(config, 1000);
    sampleWidth = config.getInt("sampleWidth", 50);
    if (sampleWidth <= 0) {
      sampleWidth = 50;
    }

    sampleHeight = config.getInt("sampleHeight", 50);
    if (sampleHeight <= 0) {
      sampleHeight = 50;
    }
  }


  @Override
  protected boolean initialise() {
    // if environment is headless, cannot do any sampling
    if (GraphicsEnvironment.isHeadless()) {
      return false;
    }

    // get the local environment and again check if it is headless
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    if (env.isHeadlessInstance()) {
      return false;
    }

    // for each screen device
    GraphicsDevice[] screens = env.getScreenDevices();
    ArrayList<Sampler> list = new ArrayList<>(screens.length);
    for (GraphicsDevice screen : screens) {
      try {
        // create and test a sampler
        Sampler samp = new Sampler(screen);
        BufferedImage img = samp.sample(sampleWidth, sampleHeight);
        if (img == null) {
          continue;
        }

        // all OK
        list.add(samp);
      } catch (AWTException e) {
        // expected
        LOG.debug("No entropy collection from {}", screen.getIDstring());
      } catch (SecurityException e) {
        // also expected
        LOG.debug("Security blocked entropy collection from {}", screen.getIDstring());
        SecureRandomProvider.LOG.warn("Lacking permission \"AWTPermission createRobot\" or \"AWTPermission readDisplayPixels\" - cannot access display entropy");
      }
    }

    if (list.isEmpty()) {
      return false;
    }

    samplers = list.toArray(new Sampler[0]);
    return true;
  }


  @Override
  protected void runImpl() {
    // select a screen
    int s = samplers.length;
    if (s > 1) {
      s = IsaacRandom.getSharedInstance().nextInt(s);
    } else {
      s = 0;
    }

    // grab a sample image if security allows us to do so
    BufferedImage image = samplers[s].sample(sampleWidth, sampleHeight);
    if (image == null) {
      return;
    }

    // compute digest of screen image
    byte[] hash;
    synchronized (digest) {
      DigestDataOutput output = new DigestDataOutput(digest);
      output.writeLong(System.nanoTime());
      int w = image.getWidth();
      int h = image.getHeight();
      for (int x = 0; x < w; x++) {
        for (int y = 0; y < h; y++) {
          output.writeInt(image.getRGB(x, y));
        }
      }
      hash = output.digest();
    }
    setEvent(hash);
  }

}
