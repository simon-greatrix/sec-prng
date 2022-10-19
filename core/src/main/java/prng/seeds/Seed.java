package prng.seeds;

import java.io.EOFException;
import java.io.UTFDataFormatException;

import prng.utility.BLOBPrint;

/**
 * Storable seed data. Every seed must have a unique name by which it is referenced.
 *
 * @author Simon Greatrix
 */
public class Seed {

  /** The seed entropy */
  protected byte[] data;

  /** Name of this seed datum */
  protected String name;


  /**
   * Create a seed
   *
   * @param name seed's name
   * @param data seed data
   */
  public Seed(String name, byte[] data) {
    this.name = name;
    this.data = (data != null) ? data.clone() : null;
  }


  /**
   * Create an empty seed. Must be initialized.
   */
  public Seed() {
    name = "unset";
    data = new byte[0];
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Seed other = (Seed) obj;
    if (name == null) {
      return (other.name == null);
    }
    return name.equals(other.name);
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


  /**
   * Initialize this seed.
   *
   * @param input input data to initialize with
   *
   * @throws UTFDataFormatException if the seed name is incomplete
   * @throws EOFException           if the seed data is missing
   */
  public void initialize(SeedInput input) throws UTFDataFormatException, EOFException {
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
   * @param output output stream
   */
  public void save(SeedOutput output) {
    output.writeUTF(name);
    output.writeSeed(data);
  }


  @Override
  public String toString() {
    return "Seed( " + name + " ) [\n" + BLOBPrint.toString(data) + "]";
  }

}