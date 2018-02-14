package prng.seeds;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Store seed data in the JVM's system or user preferences storage. Note that using this kind of storage may require the 'preferences' privilege.
 *
 * @author Simon Greatrix
 */
public abstract class PreferenceStorage extends SeedStorage {

  /**
   * New preference based storage for seeds/
   *
   * @throws StorageException if preferences cannot be accessed
   */
  protected PreferenceStorage() throws StorageException {
    // test we have access privilege to preferences.
    getPreferences();
  }


  /**
   * Get the appropriate preferences
   *
   * @return the preferences.
   * @throws StorageException if the preferences cannot be retrieved
   */
  abstract protected Preferences getPreferences() throws StorageException;


  @Override
  public byte[] getRaw(String name) throws StorageException {
    byte[] data;
    try {
      Preferences prefs = getPreferences();
      prefs.sync();
      data = prefs.getByteArray(name, null);
    } catch (BackingStoreException e) {
      throw new StorageException("User preference storage failed", e);
    }
    return data;
  }


  @Override
  protected void putRaw(String name, byte[] data) throws StorageException {
    try {
      Preferences prefs = getPreferences();
      prefs.putByteArray(name, data);
      prefs.flush();
    } catch (BackingStoreException e) {
      throw new StorageException("User preference storage failed", e);
    }
  }


  @Override
  protected void remove(String name) {
    try {
      Preferences prefs = getPreferences();
      prefs.remove(name);
      prefs.flush();
    } catch (StorageException e) {
      LOG.error("Failed to remove {} from storage", name, e);
    } catch (BackingStoreException e) {
      LOG.error("Failed to remove {} from storage", name, e);
    }
  }

}
