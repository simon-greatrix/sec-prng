package prng.seeds;

import prng.BLOBPrint;


/**
 * Storable seed data. Every seed must have a unique name by which it is
 * referenced.
 * 
 * @author Simon Greatrix
 *
 */
public class Seed {
    
    /** Name of this seed datum */
    private String name_;

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
    
    
    /**
     * Save this seed to storage
     */
    public void save() {
        SeedStorage store = SeedStorage.getInstance();
        store.put(this);
    }


    @Override
    public String toString() {
        return "Seed( "+name_+" ) [\n"+BLOBPrint.toString(data_)+"]";
    }
}