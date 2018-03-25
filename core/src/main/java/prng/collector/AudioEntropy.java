package prng.collector;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import prng.config.Config;
import prng.generator.HashSpec;
import prng.generator.IsaacRandom;

/**
 * Collect entropy from audio sources.
 *
 * @author Simon Greatrix
 */
public class AudioEntropy extends EntropyCollector {

  /**
   * Possible audio sample rates. These rates correspond to CD quality, ferric cassette, AM radio, low quality, and phone.
   */
  private static final float[] SAMPLE_RATES = new float[]{44100, 32000,
      22050, 11025, 8000};



  /**
   * An audio source we can draw data from
   *
   * @author Simon Greatrix
   */
  static class AudioSource {

    /** The buffer size we will use with this format */
    final int bufferSize;

    /** The input format for this source */
    final DataLine.Info format;

    /** The audio mixed for this source */
    final Mixer mixer;


    /**
     * Create new source definition
     *
     * @param m the mixer
     * @param f the line format
     * @param b the buffer size
     */
    AudioSource(Mixer m, DataLine.Info f, int b) {
      mixer = m;
      format = f;
      bufferSize = b;
    }


    /**
     * Sample data from this source
     *
     * @return the sampled data, or null if no data was found.
     */
    byte[] sample() {
      AudioFormat af = format.getFormats()[0];
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sampling audio from {}({}) at {} Hz", mixer.getMixerInfo().getName(), mixer.getMixerInfo().getDescription(), af.getSampleRate());
      }

      // Record some data
      TargetDataLine target;
      try {
        target = (TargetDataLine) mixer.getLine(format);
        target.open(af, bufferSize);
      } catch (LineUnavailableException e) {
        // some other application could have grabbed it
        return null;
      }
      target.start();
      int frameSize = af.getFrameSize();
      byte[] seedData = new byte[128 * frameSize];
      int off = 0;
      while (off < seedData.length) {
        off += target.read(seedData, off, seedData.length - off);
      }
      target.stop();
      target.close();

      // Verify there is some variation across the frames.
      boolean doesVary = false;
      for (int i = seedData.length - 1; i >= frameSize; i--) {
        if (seedData[i] != seedData[i - frameSize]) {
          doesVary = true;
          break;
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Audio Sample gathered {} data.", doesVary ? (Integer.toString(seedData.length) + " bytes of") : "no");
      }
      return doesVary ? seedData : null;
    }
  }


  /**
   * Get all available sources from which we may collect entropy
   *
   * @return audio sources
   */
  protected static List<AudioSource> getSources() {
    List<AudioSource> list = new ArrayList<>();
    // A recordable line is a target data line
    Line.Info targetDataType = new Line.Info(TargetDataLine.class);

    // The source will be from some mixer, so loop over all of them.
    // Get available mixers
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
    for (Mixer.Info mi : mixerInfos) {
      // May throw SecurityException
      Mixer mixer = AudioSystem.getMixer(mi);

      // No use if cannot record from it
      if (!mixer.isLineSupported(targetDataType)) {
        continue;
      }

      getSources(list, mixer);
    }

    return list;
  }


  /**
   * Get all the source from the provided mixer and add them to the list
   *
   * @param list  the list we are building
   * @param mixer the mixer to get sources from
   */
  protected static void getSources(List<AudioSource> list, Mixer mixer) {
    // Get the recordable lines
    Line.Info[] sourceLines = mixer.getTargetLineInfo();
    if ((sourceLines == null) || (sourceLines.length == 0)) {
      // no sources from this mixer
      return;
    }

    // For each recordable line
    for (Line.Info info : sourceLines) {
      // If not a data line, ignore it
      if (!(info instanceof DataLine.Info)) {
        continue;
      }

      DataLine.Info di = (DataLine.Info) info;

      // Try to get 1024 bytes in the buffer. We are going to assume
      // just 1 bit of entropy per frame and the smallest frame is 1
      // byte.
      int buffSize = 1024;
      if ((buffSize < di.getMinBufferSize())
          && (di.getMinBufferSize() != AudioSystem.NOT_SPECIFIED)) {
        buffSize = di.getMinBufferSize();
      }
      if ((buffSize > di.getMaxBufferSize())
          && (di.getMaxBufferSize() != AudioSystem.NOT_SPECIFIED)) {
        buffSize = di.getMaxBufferSize();
      }

      // Try to create a valid format
      for (AudioFormat supported : di.getFormats()) {
        AudioFormat.Encoding encoding = supported.getEncoding();
        // Is it PCM Signed?
        boolean isPCMSigned = AudioFormat.Encoding.PCM_SIGNED.equals(
            encoding);

        // Is it PCM Unsigned?
        boolean isPCMUnsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(
            encoding);

        // If its not PCM, we don't know how to use it
        if (!(isPCMSigned || isPCMUnsigned)) {
          continue;
        }

        // Recording quality is based on sample rate and size
        float[] sampleRate = new float[]{supported.getSampleRate()};
        if (sampleRate[0] == AudioSystem.NOT_SPECIFIED) {
          sampleRate = SAMPLE_RATES;
        }
        int[] sampleSize = new int[]{
            supported.getSampleSizeInBits()};
        if (sampleSize[0] == AudioSystem.NOT_SPECIFIED) {
          sampleSize = new int[]{8, 16};
        }

        // Mono, stereo or something else?
        int[] channels = new int[]{supported.getChannels()};
        if (channels[0] == AudioSystem.NOT_SPECIFIED) {
          channels = new int[]{1, 2};
        }

        boolean isBig = supported.isBigEndian();

        // Check all possible formats
        for (float f : sampleRate) {
          for (int s : sampleSize) {
            for (int c : channels) {
              AudioFormat test = new AudioFormat(f, s, c,
                  isPCMSigned, isBig);
              DataLine.Info targetInfo = new DataLine.Info(
                  TargetDataLine.class, test, buffSize);
              if (mixer.isLineSupported(targetInfo)) {
                list.add(new AudioSource(mixer, targetInfo,
                    buffSize));
              }
            }
          }
        }
      }
    }

  }


  public static void main(String[] args) throws Exception {
    // A recordable line is a target data line
    Line.Info targetDataType = new Line.Info(TargetDataLine.class);

    // Get available mixers
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
    for (Mixer.Info mi : mixerInfos) {
      // May throw SecurityException
      Mixer mixer = AudioSystem.getMixer(mi);

      // No use if cannot record from it
      if (!mixer.isLineSupported(targetDataType)) {
        continue;
      }

      System.out.println("\nname= " + mi.getName());
      System.out.println("desc= " + mi.getDescription());
      System.out.println("vers= " + mi.getVersion());
      System.out.println("vend= " + mi.getVendor());
    }
  }


  protected List<AudioSource> availableSources;


  /**
   * Create new audio entropy collector
   *
   * @param config configuration for this collector
   */
  public AudioEntropy(Config config) {
    super(config, 1000);
  }


  @Override
  protected boolean initialise() {
    availableSources = getSources();
    LOG.debug("Identified {} sources of audio entropy.", availableSources.size());
    return !availableSources.isEmpty();
  }


  @Override
  protected void runImpl() {
    int index = availableSources.size();
    if (index > 1) {
      index = IsaacRandom.getSharedInstance().nextInt(index);
    } else {
      index = 0;
    }
    AudioSource source = availableSources.get(index);
    byte[] seedData = source.sample();
    if (seedData != null) {
      MessageDigest digest = HashSpec.SPEC_SHA256.getInstance();
      setEvent(digest.digest(seedData));
    }
  }
}
