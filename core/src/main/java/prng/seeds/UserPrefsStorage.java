package prng.seeds;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.prefs.Preferences;

import prng.SecureRandomProvider;

/**
 * Store seed data in the JVM's user preferences storage
 * 
 * @author Simon Greatrix
 *
 */
public class UserPrefsStorage extends PreferenceStorage {

    /**
     * New storage using user preferences
     * @throws StorageException
     */
    public UserPrefsStorage() throws StorageException {
        // no-op
    }


    @Override
    protected Preferences getPreferences() throws StorageException {
        try {
            return AccessController.doPrivileged(
                    new PrivilegedAction<Preferences>() {
                        @Override
                        public Preferences run() {
                            return Preferences.userNodeForPackage(
                                    SeedStorage.class);
                        }
                    });
        } catch (SecurityException e) {
            SecureRandomProvider.LOG.warn(
                    "Lacking permission \"RuntimePermission preferences\" or access to user preferences - cannot access seed data in user preferences");
            throw new StorageException(
                    "Privilege 'preferences' is required to use preferences",
                    e);
        }
    }

}
