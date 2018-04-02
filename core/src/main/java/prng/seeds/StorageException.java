package prng.seeds;

/**
 * A problem occurred with seed storage
 *
 * @author Simon Greatrix
 */
public class StorageException extends Exception {

  /** serial version UID */
  private static final long serialVersionUID = 1L;


  /**
   * New exception
   *
   * @param message explanatory message
   * @param cause causative exception
   */
  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
