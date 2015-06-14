package prng.seeds;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.prefs.Preferences;

/**
 * Store seed data in the JVM's user preferences storage
 * 
 * @author Simon Greatrix
 *
 */
public class UserPrefsStorage extends PreferenceStorage {

    @Override
    protected Preferences getPreferences() throws StorageException {
        try {
            return AccessController.doPrivileged(new PrivilegedAction<Preferences>() {
                public Preferences run() {
                    return Preferences.userNodeForPackage(SeedStorage.class);
                }
            });
        } catch (SecurityException e) {
            throw new StorageException(
                    "Privilege 'preferences' is required to use preferences", e);
        }
    }

}
