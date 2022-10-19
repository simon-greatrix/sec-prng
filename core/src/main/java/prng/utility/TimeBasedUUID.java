package prng.utility;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import prng.LoggersFactory;
import prng.SecureRandomProvider;
import prng.SystemRandom;

/**
 * Implementation of UUID generator that uses time/location based generation method (variant 1).
 *
 * <p> A time based UUID may be used as a nonce where 256-bit security is required. For such usage a nonce should not be expected to repeat more often than a
 * (0.5 * security-strength)-bit random number is expected to repeat. Due to the birthday problem a (0.5 * 256)-bit or 128-bit random number is expected to
 * repeat within 2^64 values.
 *
 * <p> The time-based UUID comprises a* 60 bit clock time, a 16 bit sequence number and a 96 bit network ID. The combination of clock time and sequence exceeds
 * the required values before repetition on a particular network address.
 *
 * <p> In order to create nonces that are unique across different processes on the same machine, it is necessary to combine the type 1 UUID with a process
 * identifier.
 */
public class TimeBasedUUID {

  /** Logger for this class */
  private static final Logger LOG = LoggersFactory.getLogger(TimeBasedUUID.class);

  /** Secure random number generator */
  private static final Random RANDOM = SystemRandom.getRandom();

  /** Default instance */
  private static TimeBasedUUID INSTANCE = null;


  /**
   * Create a Type-1 UUID using the default MAC address
   *
   * @return a newly generated UUID
   */
  public static UUID create() {
    if (INSTANCE == null) {
      INSTANCE = new TimeBasedUUID(null);
    }
    return INSTANCE.generate();
  }


  /**
   * Get the MAC address to use with this computer
   *
   * @return the MAC address
   */
  private static byte[] getAddress() {
    try {
      byte[] data = AccessController.doPrivileged(
          (PrivilegedAction<byte[]>) TimeBasedUUID::getAddressWithPrivilege);
      if (data != null) {
        return data;
      }
    } catch (SecurityException e) {
      SecureRandomProvider.LOG.warn(
          "Cannot get MAC for local host. Require permission \"NetPermission getNetworkInformation\" and \"SocketPermission localhost, resolve\".");
    }

    // Must create random multi-cast address. We will create one from an
    // unused block in the CF range. The CF range is currently closed but
    // was intended for when there is no appropriate regular organizational
    // unit identifier (OUI) which would normally constitute the first three
    // bytes of the MAC address.
    byte[] data = new byte[6];
    RANDOM.nextBytes(data);
    data[0] = (byte) 0xcf;
    data[1] = (byte) (data[1] | 0x80);
    return data;
  }


  /**
   * Attempt to get the local MAC address after privilege has been asserted
   *
   * @return the MAC address or null.
   *
   * @throws SecurityException if the MAC address remains inaccessible
   */
  static byte[] getAddressWithPrivilege() throws SecurityException {
    try {
      // first try local host
      LOG.info("Getting address for localhost");
      InetAddress localHost = InetAddress.getLocalHost();
      LOG.info("Localhost is {}", localHost);

      if (!localHost.isLoopbackAddress()) {
        LOG.info("Getting hardware address for localhost");
        NetworkInterface nint = NetworkInterface.getByInetAddress(
            localHost);
        if (nint != null) {
          byte[] data = nint.getHardwareAddress();
          if (data != null && data.length == 6) {
            if (LOG.isInfoEnabled()) {
              LOG.info("Hardware address for localhost is {}", toHexString(data));
            }
            return data;
          }
        }
      }
    } catch (IOException e) {
      // possibly the look-up of local host failed
      LOG.warn("Failed to get localhost hardware address.", e);
    }

    LOG.info("Failed to get hardware address for localhost. Enumerating network interfaces.");

    // now try all interfaces
    LinkedList<NetworkInterface> list = new LinkedList<>();
    try {
      Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
      while (en.hasMoreElements()) {
        list.addLast(en.nextElement());
      }
    } catch (SocketException se) {
      // cannot process interfaces
      LOG.warn("Failed to get network interfaces.", se);
    }

    while (!list.isEmpty()) {
      NetworkInterface nint = list.removeFirst();
      LOG.info("Examining network interface {}", nint.getDisplayName());
      try {
        if (!nint.isLoopback()) {
          byte[] data = nint.getHardwareAddress();
          if (data != null && data.length == 6) {
            if (LOG.isInfoEnabled()) {
              LOG.info("Hardware address for {} is {}", nint.getDisplayName(), toHexString(data));
            }
            return data;
          }
        }

        // queue up sub-interfaces in order
        LinkedList<NetworkInterface> tmp = new LinkedList<>();
        Enumeration<NetworkInterface> en = nint.getSubInterfaces();
        while (en.hasMoreElements()) {
          tmp.addLast(en.nextElement());
        }
        while (!tmp.isEmpty()) {
          list.addFirst(tmp.removeLast());
        }
      } catch (SocketException se) {
        // ignore this interface
        LOG.warn(
            "Failed to get localhost hardware address or sub-interfaces for "
                + nint.getDisplayName(),
            se
        );
      }
    }

    LOG.info("No hardware network address discovered. Will use special OUI MAC address.");
    return null;
  }


  private static String toHexString(byte[] a) {
    if (a == null || a.length == 0) {
      return "";
    }
    StringBuilder buf = new StringBuilder();
    for (byte b : a) {
      buf.append(Integer.toHexString((b & 0xff) | 0x100).substring(1));
      buf.append(':');
    }
    buf.setLength(buf.length() - 1);
    return buf.toString();
  }


  /** The Ethernet address this generator is associated with */
  protected final long ethernetAddress;


  /**
   * Create a Type 1 UUID generator for the specified MAC address
   *
   * @param address the MAC address (6 bytes)
   */
  public TimeBasedUUID(byte[] address) {
    if (address != null && address.length != 6) {
      throw new IllegalArgumentException(
          "MAC Address must contain 48 bits");
    }
    UUIDTime.init(RANDOM);
    if (address == null) {
      address = getAddress();
    }

    long v = 0;
    for (int i = 0; i < 6; i++) {
      v = (v << 8) | (address[i] & 0xff);
    }
    ethernetAddress = v;
  }


  /**
   * Generate a Type 1 UUID for this address
   *
   * @return a new UUID
   */
  public UUID generate() {
    UUIDTime time = UUIDTime.newTimeStamp();
    final long rawTimestamp = time.getTime();
    final int sequence = time.getSequence();

    // first 32 bits are the lowest 32 bits of the time
    long l1 = (rawTimestamp & 0xffffffffL) << 32;

    // next 16 bits are the middle of the time
    l1 |= (rawTimestamp & 0xffff00000000L) >> 16;

    // next 4 bits are the version code
    l1 |= 0x1000;

    // last 12 bits are the next 12 bits of the time
    l1 |= (rawTimestamp & 0xfff000000000000L) >> 48;

    // the top 4 bits of the time are lost

    long l2 = ((long) sequence) << 48;
    l2 |= ethernetAddress;

    return new UUID(l1, l2);
  }

}



/**
 * Representation of the time and sequence used to generate Type-1 UUIDs.
 */
final class UUIDTime {

  /**
   * UUIDs need time from the beginning of Gregorian calendar (15-OCT-1582), need to apply this offset from the System current time.
   */
  private static final long CLOCK_OFFSET = 0x01b21dd213814000L;

  /**
   * Initial time which may be negative. Used to ensure we get a positive time difference.
   */
  private static final long INIT_NANO = System.nanoTime();

  /**
   * Sequence number for a given clock value. The RFC requires it be initialised from a secure random source.
   */
  private static short CLOCK_SEQUENCE = 0;

  /** Has the timer been initialised? */
  private static boolean IS_INIT_DONE = false;

  /**
   * Timestamp value last used for generating a UUID (along with {@link #CLOCK_SEQUENCE}. Usually the same as System.currentTimeMillis , but not always (system
   * clock moved backwards). Note that this value is guaranteed to be monotonically increasing; that is, at given absolute time points t1 and t2 (where t2 is
   * after t1), t1 <= t2 will always hold true.
   */
  private static long LAST_USED_TIMESTAMP = 0L;


  /**
   * Initialize this timer. Ideally the random number generator will provide a secure random value to initialise the sequence with.
   *
   * @param random RNG
   */
  static synchronized void init(Random random) {
    if (IS_INIT_DONE) {
      return;
    }

    if (random == null) {
      random = new SecureRandom();
    }
    CLOCK_SEQUENCE = (short) random.nextInt(0x10000);
    IS_INIT_DONE = true;
  }


  /**
   * Method that constructs a unique timestamp.
   *
   * @return 64-bit timestamp to use for constructing UUID
   */
  public static synchronized UUIDTime newTimeStamp() {
    long sysTime = System.currentTimeMillis();
    long nanoTime = ((System.nanoTime() - INIT_NANO) / 100) % 10000;
    sysTime = sysTime * 10000 + nanoTime + CLOCK_OFFSET;

    // If time is in past, move up
    if (sysTime < LAST_USED_TIMESTAMP) {
      sysTime = LAST_USED_TIMESTAMP;
    }

    // Get the sequence
    short seq = CLOCK_SEQUENCE;
    if (sysTime == LAST_USED_TIMESTAMP) {
      seq++;
      CLOCK_SEQUENCE = seq;
      if (seq == 0) {
        sysTime++;
      }
    }

    LAST_USED_TIMESTAMP = sysTime;

    return new UUIDTime(sysTime, seq);
  }


  /**
   * A 16-bit sequence number unique with the current 100 nanosecond interval.
   */
  private final int sequence;

  /**
   * The number of 100 nanosecond intervals that have passed since the start of the Gregorian calendar.
   */
  private final long timeStamp;


  /**
   * New time and sequence
   *
   * @param timeStamp the time stamp
   * @param sequence  the sequence
   */
  private UUIDTime(long timeStamp, short sequence) {
    this.timeStamp = timeStamp;
    this.sequence = sequence & 0xffff;
  }


  /**
   * Get the 16-bit sequence value
   *
   * @return the sequence value
   */
  public int getSequence() {
    return sequence;
  }


  /**
   * Get the 64-bit 100 nanosecond time value
   *
   * @return the time value
   */
  public long getTime() {
    return timeStamp;
  }

}
