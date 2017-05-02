import java.util.Random;

import javax.sound.sampled.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.collector.EntropyCollector;
import prng.generator.IsaacRandom;
import prng.utility.BLOBPrint;

public class AudioTest {

    static class AudioSource {
        Mixer mixer;


        AudioSource(Mixer mixer) {
            this.mixer = mixer;

        }
    }

    protected static final Logger LOG = LoggerFactory.getLogger(
            EntropyCollector.class);

    /**
     * Possible audio sample rates. These rates correspond to CD quality, ferric
     * cassette, AM radio, low quality, and phone.
     */
    private static final float[] SAMPLE_RATES = new float[] { 44100, 32000,
            22050, 11025, 8000 };


    public static void main(String[] args) throws Exception {
        // A recordable line is a target data line
        Line.Info targetDataType = new Line.Info(TargetDataLine.class);

        // Get available mixers
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for(Mixer.Info mi:mixerInfos) {
            // May throw SecurityException
            Mixer mixer = AudioSystem.getMixer(mi);

            // No use if cannot record from it
            if( !mixer.isLineSupported(targetDataType) ) continue;

            // Get the recordable lines
            Line.Info[] source = mixer.getTargetLineInfo();
            if( source == null || source.length == 0 ) continue;

            // For each recordable line
            for(Line.Info info:source) {
                // If not a data line, ignore it
                if( !(info instanceof DataLine.Info) ) continue;

                DataLine.Info di = (DataLine.Info) info;

                // Try to get 1024 bytes in the buffer. We are going to assume
                // just 1 bit of entropy per frame and the smallest frame is 1
                // byte,
                int buffSize = 1024;
                if( buffSize < di.getMinBufferSize()
                        && di.getMinBufferSize() != AudioSystem.NOT_SPECIFIED ) {
                    buffSize = di.getMinBufferSize();
                }
                if( buffSize > di.getMaxBufferSize()
                        && di.getMaxBufferSize() != AudioSystem.NOT_SPECIFIED ) {
                    buffSize = di.getMaxBufferSize();
                }

                // Try to create a valid format
                for(AudioFormat supported:di.getFormats()) {
                    // If its not PCM, we don't know how to use it
                    AudioFormat.Encoding encoding = supported.getEncoding();
                    if( !(AudioFormat.Encoding.PCM_SIGNED.equals(encoding)
                            || AudioFormat.Encoding.PCM_UNSIGNED.equals(
                                    encoding)) )
                        continue;
                    boolean isPCMSigned = AudioFormat.Encoding.PCM_SIGNED.equals(
                            encoding);

                    // Recording quality is based on sample rate and size
                    float sampleRate[] = new float[] {
                            supported.getSampleRate() };
                    if( sampleRate[0] == AudioSystem.NOT_SPECIFIED ) {
                        sampleRate = SAMPLE_RATES;
                    }
                    int sampleSize[] = new int[] {
                            supported.getSampleSizeInBits() };
                    if( sampleSize[0] == AudioSystem.NOT_SPECIFIED ) {
                        sampleSize = new int[] { 8, 16 };
                    }

                    // Mono, stereo or something else?
                    int channels[] = new int[] { supported.getChannels() };
                    if( channels[0] == AudioSystem.NOT_SPECIFIED ) {
                        channels = new int[] { 1, 2 };
                    }

                    boolean isBig = supported.isBigEndian();

                    // Create an array of all possible formats
                    AudioFormat tests[] = new AudioFormat[sampleRate.length
                            * sampleSize.length * channels.length];
                    int index = 0;
                    for(float f:sampleRate) {
                        for(int s:sampleSize) {
                            for(int c:channels) {
                                tests[index] = new AudioFormat(f, s, c,
                                        isPCMSigned, isBig);
                                index++;
                            }
                        }
                    }

                    Random rand = IsaacRandom.getSharedInstance();
                    for(int i = 0;i < tests.length;i++) {
                        int r = rand.nextInt(tests.length);
                        r = 0;
                        while( tests[r] == null ) {
                            r++;
                            if( r == tests.length ) r = 0;
                        }
                        AudioFormat test = tests[r];
                        tests[r] = null;
                        DataLine.Info targetInfo = new DataLine.Info(
                                TargetDataLine.class, test, buffSize);
                        if( mixer.isLineSupported(targetInfo) ) {
                            System.out.println(mi.getName() + "  "
                                    + mi.getDescription() + "  " + test);
                            TargetDataLine target = (TargetDataLine) mixer.getLine(
                                    targetInfo);
                            target.open(test, buffSize);
                            target.start();
                            int frameSize = test.getFrameSize();
                            int frames = 128;
                            byte[] seedData = new byte[frames * frameSize];
                            int off = 0;
                            while( off < seedData.length ) {
                                off += target.read(seedData, off,
                                        seedData.length - off);
                            }
                            target.stop();
                            target.close();

                            System.out.println(BLOBPrint.toString(seedData));

                            float entropy = 0;
                            int count[] = new int[256];
                            for(byte b:seedData) {
                                count[0xff & b]++;
                            }
                            int cn = 0;
                            for(int c:count) {
                                if( c == 0 ) continue;
                                cn++;
                                float f = (float) c / seedData.length;
                                System.out.println(c + " -> " + (100 * f)
                                        + " \t " + (Math.log(f) / Math.log(2)));
                                entropy -= c * Math.log(f);
                            }
                            entropy /= Math.log(2);
                            System.out.println(entropy + " , " + cn);
                        }
                    }
                }
            }

        }
    }
}
