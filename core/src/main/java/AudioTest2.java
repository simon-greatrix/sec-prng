import javax.sound.sampled.*;

import prng.utility.BLOBPrint;

public class AudioTest2 {
    public static void main(String[] args) throws Exception {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        Mixer mixer = null;
        for(Mixer.Info i:mixerInfos) {
            if( i.getName().trim().equals("Microphone (Logitech USB Headse") ) {
                mixer = AudioSystem.getMixer(i);
                break;
            }
        }

        System.out.println(mixer.getMixerInfo());

        AudioFormat format = new AudioFormat(44100, 8, 1, true, true);
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class,
                format);
        byte[] seedData = new byte[500000];
        TargetDataLine target = (TargetDataLine) mixer.getLine(targetInfo);
        target.open();
        target.start();
        int off = 0;
        while( off < seedData.length ) {
            off += target.read(seedData, off, seedData.length - off);
        }
        target.stop();
        target.close();
        System.out.println(BLOBPrint.toString(seedData));
        SourceDataLine source = AudioSystem.getSourceDataLine(format);
        source.open();
        source.start();
        source.write(seedData, 0, 500000);
        source.stop();
        source.close();
    }
}
