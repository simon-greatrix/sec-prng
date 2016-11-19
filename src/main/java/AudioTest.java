import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.sound.sampled.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.collector.EntropyCollector;
import prng.generator.IsaacRandom;
import prng.utility.BLOBPrint;

public class AudioTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(EntropyCollector.class);
    
    /**
     * Possible audio sample rates. These rates correspond to CD quality, ferric cassette, AM radio, low quality, and phone.
     */
    private static final float[] SAMPLE_RATES = new float[] { 44100, 32000, 22050, 11025, 8000 };
    
    private static void listControls() throws Exception {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        Line.Info isPort = new Line.Info(Port.class);
        for(Mixer.Info mi : mixerInfos) {
            System.out.println(mi);

            Mixer mixer = AudioSystem.getMixer(mi);
            mixer.open();
            if( mixer.isLineSupported(isPort) ) {
                Line line = mixer.getLine(isPort);
                line.open();
                if(line!=null) {
                    System.out.println(line.getControls().length);
                    for(Control c : line.getControls()) {
                        System.out.println("control = "+c);
                        if( c instanceof CompoundControl ) {
                            CompoundControl cc = (CompoundControl) c;
                            for(Control d : cc.getMemberControls()) {
                                System.out.println("\t\t = "+d);
                            }
                        }
                    }
                }
                line.close();
            }
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        Line.Info targetDataType = new Line.Info(TargetDataLine.class);
        for(Mixer.Info mi : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mi);
            if(! mixer.isLineSupported(targetDataType)) continue;
            Control[] cntrls = mixer.getControls();
            System.out.println(Arrays.toString(cntrls));
            
            Line.Info[] source = mixer.getTargetLineInfo();
            if( source==null || source.length==0 ) continue;
            
            System.out.println("\nMixer: "+mi);
            System.out.println("Target lines: "+(source.length));
            
            for(Line.Info info : source) {
                if( ! (info instanceof DataLine.Info) ) continue;
                System.out.println(info);
                DataLine.Info di = (DataLine.Info) info;
                System.out.println("\t"+Arrays.toString(di.getFormats()));
                System.out.println(di.getMinBufferSize()+" -> "+di.getMaxBufferSize());

          
                
                // try to get 1024 bits
                int buffSize = 128;
                if( buffSize < di.getMinBufferSize() && di.getMinBufferSize() != AudioSystem.NOT_SPECIFIED ) {
                    buffSize = di.getMinBufferSize();
                }
                if( buffSize > di.getMaxBufferSize() && di.getMaxBufferSize() != AudioSystem.NOT_SPECIFIED ) {
                    buffSize = di.getMaxBufferSize();                    
                }
                
                // Try to create a valid format
                for(AudioFormat supported : di.getFormats()) {
                    AudioFormat.Encoding encoding = supported.getEncoding();
                    if( !(AudioFormat.Encoding.PCM_SIGNED.equals(encoding) || AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding))) continue;
                    boolean isPCMSigned = AudioFormat.Encoding.PCM_SIGNED.equals(encoding);
                    
                    float sampleRate[] = new float[] { supported.getSampleRate() };
                    if( sampleRate[0]==AudioSystem.NOT_SPECIFIED ) {
                        sampleRate = SAMPLE_RATES;
                    }
                    int sampleSize[] = new int[] { supported.getSampleSizeInBits() };
                    if( sampleSize[0]==AudioSystem.NOT_SPECIFIED ) {
                        sampleSize = new int[] { 8, 16 };
                    }
                    int channels[] = new int[] { supported.getChannels() };
                    if( channels[0]==AudioSystem.NOT_SPECIFIED ) {
                        channels = new int[] { 1, 2 };
                    }
                    
                    boolean isBig = supported.isBigEndian();

                    AudioFormat tests[] = new AudioFormat[ sampleRate.length * sampleSize.length * channels.length ];
                    int index=0;
                    for(float f : sampleRate) {
                        for(int s : sampleSize) {
                            for(int c : channels) {
                               tests[index] = new AudioFormat(f,s,c,isPCMSigned,isBig);
                               index++;
                            }
                        }
                    }

                    Random rand = IsaacRandom.getSharedInstance();
                    for(int i=0;i<tests.length;i++) {
                        int r = rand.nextInt(tests.length);
                        r=0;
                        while( tests[r]==null ) {
                            r++;
                            if( r==tests.length ) r=0;
                        }
                        AudioFormat test = tests[r];
                        tests[r] = null;
                        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, test, buffSize);
                        if( mixer.isLineSupported(targetInfo) ) {
                            if( test.getFrameSize() != 1 ) continue;
                            System.out.println(mi.getName()+"  "+mi.getDescription()+"  "+test);
                            TargetDataLine target = (TargetDataLine) mixer.getLine(targetInfo);
                            target.open(test,buffSize);
                            target.start();
                            int frameSize = test.getFrameSize();
                            int frames = 1280 / frameSize;
                            if( frames*frameSize < 1280 ) frames++;
                            byte[] seedData = new byte[frames*frameSize];
                            int off = 0;
                            while( off<seedData.length ) {
                                off += target.read(seedData, off, seedData.length-off);                                
                            }
                            target.stop();
                            target.close();
                            
                            float entropy = 0;
                            int count[] = new int[256];
                            for(byte b : seedData) {
                                int x = (b+128)/4;
//                                for(int y=0;y<x;y++) { System.out.print(" "); }
//                                System.out.println("X");
                                count[0xff & b]++;
                            }
                            int cn = 0;
                            for(int c : count) {
                                if( c==0 ) continue;
                                cn++;
                                float f = (float) c / seedData.length;  
                                entropy -= f * Math.log(f);
                            }
                            entropy /= Math.log(2);
                            System.out.println(entropy+" , "+cn);
                        }
                    }
                }
            }
            
        }
    }
}
