package prng;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import prng.collector.InstantEntropy;
import prng.nist.IsaacRandom;

public class Test {

    public static void main(String[] args) {
        for(int i=0;i<120;i++) {
            long start = System.currentTimeMillis();
        
            InstantEntropy.get();
            System.out.println(System.currentTimeMillis()-start);
        }
    }

}
