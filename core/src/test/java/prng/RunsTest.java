package prng;

import java.util.Arrays;
import java.util.TreeSet;

/** http://www.itl.nist.gov/div898/handbook/eda/section3/eda35d.htm */
public class RunsTest {

    static byte[] create(int order) {
        byte[] data = new byte[256];
        final int a = ((order >> 7) & 63) * 4 + 1;
        final int c = (order & 127) * 2 + 1;
        System.out.println(" a="+a+" , c="+c);
        int j = 0;
        for(int i = 0;i < 256;i++) {
            data[i] = (byte) j;
            j = ((j * a) + c) & 0xff;
        }
        return data;
    }


    public static void main(String[] args) {
        boolean[] ht = new boolean[256];

        double[][] score = new double[8192][8];        
        for(int i = 0;i < 8192;i++) {
            byte[] data = create(i);
            for(int k = 0;k < 8;k++) {

                int mask = 1 << k;
                int cp = 0, cn = 0;
                for(int j = 0;j < 256;j++) {
                    ht[j] = (data[j] & mask) == 0;
                    if( ht[j] ) {
                        cp++;
                    } else {
                        cn++;
                    }
                }
                int runs = 1;
                for(int j = 1;j < 256;j++) {
                    if( ht[j] != ht[j - 1] ) {
                        runs++;
                    }
                }

                double expect = 1 + (2.0 * cp * cn) / (cp + cn);
                double sigma = 2.0 * cp * cn * (2.0 * cp * cn - cp - cn)
                        / ((cp + cn) * (cp + cn) * (cp + cn + 1));
                double z = (runs - expect) / Math.sqrt(sigma);
                score[i][k] = Math.abs(z);
            }
        }

        int[] points = new int[8192];
        
        for(int j=0;j<8;j++) {
            TreeSet<Double> values = new TreeSet<>();
            for(int i=0;i<8192;i++) {
                values.add(Double.valueOf(score[i][j]));
            }
            for(int i=0;i<8192;i++) {
                points[i] += values.headSet(Double.valueOf(score[i][j])).size();
            }
        }
        
        int b = 8192;
        int bi;
        for(int i=0;i<8192;i++) {
            if( points[i] <= b ) {
                b = points[i];
                bi = i;
                System.out.println(bi+" "+b);
            }
        }
        
        byte[] data = create(3223);
        System.out.println(Arrays.toString(data));
        System.out.println(Arrays.toString(score[3223]));
    }

}
