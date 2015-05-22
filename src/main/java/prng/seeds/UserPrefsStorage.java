package prng.seeds;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Store seed data in the JVM's user preferences storage
 * 
 * @author Simon Greatrix
 *
 */
public class UserPrefsStorage extends SeedStorage {

    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        try {
            Preferences prefs = Preferences.userNodeForPackage(SeedStorage.class);
            prefs.putByteArray(name, data);
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new StorageException("User preference storage failed", e);
        }
    }


    @Override
    public byte[] getRaw(String name) throws StorageException {
        byte[] data;
        try {
            Preferences prefs = Preferences.userNodeForPackage(SeedStorage.class);
            prefs.sync();
            data = prefs.getByteArray(name, null);
        } catch (BackingStoreException e) {
            throw new StorageException("User preference storage failed", e);
        }
        return data;
    }
    @Override
    protected void remove(String name) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(SeedStorage.class);
            prefs.remove(name);
            prefs.flush();
        } catch (BackingStoreException e) {
            LOG.error("Failed to remove {} from storage",name,e);
        }
    }

}
