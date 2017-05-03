package prng.seeds;

import prng.utility.BLOBPrint;

/**
 * Storable seed data. Every seed must have a unique name by which it is
 * referenced.
 * 
 * @author Simon Greatrix
 *
 */
public class Seed {

    /** Name of this seed datum */
    protected String name;

    /** The seed entropy */
    protected byte[] data;


    /**
     * Create a seed
     * 
     * @param name
     *            seed's name
     * @param data
     *            seed data
     */
    public Seed(String name, byte[] data) {
        this.name = name;
        this.data = (data!=null) ? data.clone() : null;
    }


    /**
     * Create an empty seed. Must be initialized.
     */
    public Seed() {
        name = "unset";
        data = new byte[0];
    }


    /**
     * Initialize this seed.
     * 
     * @param input
     *            input data to initialize with
     * @throws Exception
     *             if something goes wrong initializing the seed
     */
    public void initialize(SeedInput input) throws Exception {
        name = input.readUTF();
        data = input.readSeed();
    }


    /**
     * Is this seed empty?
     * 
     * @return true if this seed contains no data
     */
    public boolean isEmpty() {
        return data == null || data.length == 0;
    }


    /**
     * Save this seed
     * 
     * @param output
     *            output stream
     */
    public void save(SeedOutput output) {
        output.writeUTF(name);
        output.writeSeed(data);
    }


    /**
     * Get this seeds name
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }


    /**
     * Get this seeds value
     * 
     * @return the value
     */
    public byte[] getSeed() {
        return data.clone();
    }


    @Override
    public int hashCode() {
        return ((name == null) ? 0 : name.hashCode());
    }


    @Override
    public boolean equals(Object obj) {
        if( this == obj ) return true;
        if( obj == null ) return false;
        if( getClass() != obj.getClass() ) return false;
        Seed other = (Seed) obj;
        if( name == null ) {
            return (other.name == null);
        }
        return name.equals(other.name);
    }


    @Override
    public String toString() {
        return "Seed( " + name + " ) [\n" + BLOBPrint.toString(data) + "]";
    }
}