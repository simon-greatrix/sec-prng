import prng.internet.Quantum;
import prng.utility.BLOBPrint;

public class Test {

    public static void main(String[] args) throws Exception {
        // TODO Auto-generated method stub
        // new ConfigTable();
        Quantum q = new Quantum();
        System.out.println(BLOBPrint.toString(q.load()));
    }

}
