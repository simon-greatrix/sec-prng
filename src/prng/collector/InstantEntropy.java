package prng.collector;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import prng.DigestDataOutput;
import prng.NonceFactory;
import prng.nist.HashSpec;
import prng.nist.IsaacRandom;
import prng.nist.SeedSource;

/**
 * Attempts to create useful entropy from nothing.
 * 
 * @author Simon Greatrix
 */
public class InstantEntropy implements Runnable, SeedSource {
    
    private static final Random RAND;
   
    static {
        // Create a 1024 byte entropy source
        ByteBuffer buf = ByteBuffer.allocate(1024);
        
        // start off with our 32 byte personalization value
        byte[] p = NonceFactory.personalization();
        BigInteger mask = BigInteger.ZERO.flipBit(256);
        
        // Use a 256-bit (32 byte) FNV-1a Hash
        BigInteger prime = new BigInteger("374144419156711147060143317175368453031918731002211");
        BigInteger offset = new BigInteger("100029257958052580907070968620625704837092796014241193945225284501741471925557");
        for(int i=0;i<32;i++) {
            // hash the previous value
            BigInteger hash = offset;
            for(int j=0;j<32;j++) {
                hash = hash.xor(BigInteger.valueOf(0xff & p[j])).multiply(prime).mod(mask);
            }
            
            // add the nano time to the hash
            long now = System.nanoTime();
            for(int j=0;j<8;j++) {
                hash = hash.xor(BigInteger.valueOf(0xff & now)).multiply(prime).mod(mask);
                now >>>= 8;
            }
            
            // convert hash to byte array
            p = hash.toByteArray();
            int d = p.length - 32;
            if( d>0 ) {
                // leading sign byte
                byte[] p2 = new byte[32];
                System.arraycopy(p,d,p2,0,32);
                p=p2;
            } else if( d<0 ) {
                // leading zero byte
                byte[] p2 = new byte[32];
                System.arraycopy(p,0,p2,0,p.length);
                p=p2;
            }
            buf.put(p);
        }
        
        // Now get a "random" bit. It's the bit above the least significant non-zero bit in the nano time
        long now = System.nanoTime();
        int bit = Long.numberOfTrailingZeros(now);
        now >>>= (bit+1);
        buf.order( (now&1)==0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        // convert to ints
        buf.flip();
        IntBuffer ibuf = buf.asIntBuffer();        
        int[] seed = new int[256];
        ibuf.get(seed);

        // seed an ISAAC random generator with this entropy
        RAND = new IsaacRandom(seed);
    }
       

    static final int[] ADD_CONST = new int[] { 1, 7, 11, 13, 17, 19, 23, 29 };
    
    
    public static byte[] get() {
        DigestDataOutput dig = new DigestDataOutput(HashSpec.SPEC_SHA512.getInstance());
        Thread[] threads = new Thread[16];
        AtomicInteger id = new AtomicInteger(0);
        for(int i=0;i<threads.length;i++) {
            threads[i] = new Thread(new InstantEntropy(id,dig));
        }
        for(int i=0;i<threads.length;i++) {
            threads[i].start();
        }
        for(int i=0;i<threads.length;i++) {
            try {
                threads[i].join();
            } catch ( InterruptedException ie ) {
                
            }
        }
        return dig.digest();
    }
    
    AtomicInteger id_;
    
    DigestDataOutput output_;


    InstantEntropy(AtomicInteger id, DigestDataOutput output) {
        id_ = id;
        output_ = output;
    }


    /**
     * Find a prime number. This is simply an operation that takes a hard to predict amount of time with a hard to predict output.
     * @return the prime number
     */
    int findPrime() {
        int p;
        findPrime:
        while( true ) {
            // yield to allow threads to schedule over each other
            Thread.yield();
            
            // create a candidate that is not divisible by 2,3 or 5
            int v = RAND.nextInt(1048576);
            p = 30 * (v>>>3) + ADD_CONST[v&0x7];
            
            // check it does not divide by any other prime <30            
            for(int i=1;i<8;i++) {
                if( ( p % ADD_CONST[i]) == 0 ) {
                    continue findPrime;
                }
            }
            
            // check up to square root
            int m = (int) (Math.sqrt(p) / 30);
            for(int j=1;j<m;j++) {
                for(int i=0;i<8;i++) {
                    int d = 30*j+ADD_CONST[i];
                    if( (p % d) == 0 ) {
                        continue findPrime;
                    }
                }
            }
            
            // found a prime
            break;
        }
        
        return p;
    }
    
    
    public void run() {
        while( true ) {
            int id = id_.getAndIncrement();
            if( id > 255 ) return;
            
            long s = System.nanoTime();
            int p = findPrime();
            long e = System.nanoTime() - s;
            int value = p * ((int) e);
            
            synchronized( output_ ) {
                System.out.println(id+" : "+p+"  "+e+"    "+value);
                output_.write(id);
                output_.writeLong(value);
            }
            
        }
    }


    @Override
    public byte[] getSeed(int size) {
        // TODO Auto-generated method stub
        return null;
    }
}