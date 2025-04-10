package prng.seeds;

import java.util.prefs.Preferences;

/**
 * Store seed data in the JVM's user preferences storage
 *
 * @author Simon Greatrix
 */
public class UserPrefsStorage extends PreferenceStorage {

  /**
   * New storage using user preferences
   *
   * @throws StorageException if the user preferences cannot be accessed
   */
  public UserPrefsStorage() throws StorageException {
    // no-op
  }


  @Override
  protected Preferences getPreferences() {
    return Preferences.userNodeForPackage(SeedStorage.class);
  }

}
