package prng.seeds;

/**
 * Store all seed information in a single file and load it all into memory when
 * needed.
 * 
 * @author Simon Greatrix
 *
 */
public class FileStorage extends SeedStorage {

    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        // TODO Auto-generated method stub

    }


    @Override
    protected byte[] getRaw(String name) throws StorageException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    protected void remove(String name) {
        // TODO Auto-generated method stub

    }

}
