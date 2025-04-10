package prng.seeds;

import java.util.prefs.Preferences;

/**
 * Store seed data in the JVM's system preferences storage. Note that accessing system preferences often requires administrator privileges.
 *
 * @author Simon Greatrix
 */
public class SystemPrefsStorage extends PreferenceStorage {

  /**
   * Create storage that used the system preferences.
   *
   * @throws StorageException if the system preferences cannot be accessed
   */
  public SystemPrefsStorage() throws StorageException {
    // no-op
  }


  @Override
  protected Preferences getPreferences() {
    return Preferences.systemNodeForPackage(SeedStorage.class);
  }

}
