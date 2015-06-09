package prng;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import prng.collector.InstantEntropy;
import prng.generator.IsaacRandom;
import prng.utility.BLOBPrint;

public class Test {

    public static void main(String[] args) throws Exception {
        long now = System.currentTimeMillis();
        byte[] data = Fortuna.getSeed(128);
        System.out.println(System.currentTimeMillis()-now);
        System.out.println(BLOBPrint.toString(data));
    }

}
