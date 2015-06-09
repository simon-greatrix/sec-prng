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
    protected String name_;

    /** The seed entropy */
    protected byte[] data_;


    /**
     * Create a seed
     * 
     * @param name
     *            seed's name
     * @param data
     *            seed data
     */
    public Seed(String name, byte[] data) {
        name_ = name;
        data_ = data.clone();
    }


    /**
     * Create an empty seed. Must be initialized.
     */
    public Seed() {
        name_ = "unset";
        data_ = new byte[0];
    }


    /**
     * Initialize this seed.
     * 
     * @param input
     *            input data to initialize with
     * @throws Exception
     */
    public void initialize(SeedInput input) throws Exception {
        name_ = input.readUTF();
        data_ = input.readSeed();
    }


    /**
     * Is this seed empty?
     * 
     * @return true if this seed contains no data
     */
    public boolean isEmpty() {
        return data_ == null || data_.length == 0;
    }


    /**
     * Save this seed
     * 
     * @param output
     *            output stream
     */
    public void save(SeedOutput output) {
        output.writeUTF(name_);
        output.writeSeed(data_);
    }


    /**
     * Get this seeds name
     * 
     * @return the name
     */
    public String getName() {
        return name_;
    }


    /**
     * Get this seeds value
     * 
     * @return the value
     */
    public byte[] getSeed() {
        return data_.clone();
    }


    @Override
    public int hashCode() {
        return ((name_ == null) ? 0 : name_.hashCode());
    }


    @Override
    public boolean equals(Object obj) {
        if( this == obj ) return true;
        if( obj == null ) return false;
        if( getClass() != obj.getClass() ) return false;
        Seed other = (Seed) obj;
        if( name_ == null ) {
            return (other.name_ == null);
        }
        return name_.equals(other.name_);
    }


    @Override
    public String toString() {
        return "Seed( " + name_ + " ) [\n" + BLOBPrint.toString(data_) + "]";
    }
}