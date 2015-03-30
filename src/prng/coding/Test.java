package prng.coding;

import java.io.IOException;
import java.util.Random;

public class Test {

    public static void main(String[] args) throws IOException {
        // TODO Auto-generated method stub
        Encoder encE = new Encoder(new BitOutputStream(null));
        Encoder encM = new Encoder(new BitOutputStream(null));
        Random rand = new Random();
        for(int i=0;i<1000;i++) {
            float r = 96 + (float) rand.nextGaussian();
            int ri = Float.floatToRawIntBits(r);
            int e = (ri>>23) & 0xff;
            int m = (ri>>17)&0xff;
            encE.update(e);
            encM.update(m);            
        }
        
        System.out.println(encE.freq_);
        System.out.println(encM.freq_);
    }

}
