package prng;

public interface SeedSource {
    public byte[] getSeed(int size);
}
