package prng.internet;

import java.io.EOFException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Very simple JSON parser.
 *
 * @author Simon
 */
public class SimpleJSONParser {

  /**
   * Letters for the "false" literal
   */
  private static final char[] LITERAL_FALSE = {'A', 'a', 'L', 'l',
      'S', 's', 'E', 'e'};

  /** Letters for the "null" literal */
  private static final char[] LITERAL_NULL = {'U', 'u', 'L', 'l',
      'L', 'l'};

  /** Letters for the "true" literal */
  private static final char[] LITERAL_TRUE = {'R', 'r', 'U', 'u',
      'E', 'e'};

  /** Hexadecimal digits */
  private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6',
      '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};



  /**
   * Enumeration of JSON types
   */
  public enum Type {
    /** A JSON [...] construct */
    ARRAY(JSONArray.class),

    /** A true or a false */
    BOOLEAN(Boolean.class),

    /** A null */
    NULL(Void.class),

    /** Any number */
    NUMBER(Number.class),

    /** A JSON { ... } construct */
    OBJECT(JSONObject.class),

    /** A string */
    STRING(String.class);

    /** Class of associated encapsulated values */
    private final Class<?> type;


    /**
     * Create type
     *
     * @param type encapsulated value class
     */
    Type(Class<?> type) {
      this.type = type;
    }


    /**
     * Get encapsulate value class
     *
     * @return the type of the encapsulated value
     */
    public Class<?> getType() {
      return type;
    }
  }



  /**
   * Representation of an array in JSON
   */
  public static class JSONArray extends ArrayList<Primitive> {

    /** serial version UID */
    private static final long serialVersionUID = 2L;


    /**
     * Get a type-safe value
     *
     * @param type the type required
     * @param index the value's index
     * @param dflt default value if missing or wrong type
     * @param <T> the value's class to return
     *
     * @return the value
     */
    public <T> T get(Class<T> type, int index, T dflt) {
      Primitive prim = get(index);
      if (prim == null) {
        return dflt;
      }
      Object val = prim.getValue();
      if (val == null) {
        return null;
      }
      if (!type.isInstance(val)) {
        return dflt;
      }
      return type.cast(val);
    }


    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append('[');
      for (Primitive e : this) {
        buf.append(e);
        buf.append(", ");
      }
      if (buf.length() > 1) {
        buf.setLength(buf.length() - 2);
      }
      buf.append(']');
      return buf.toString();
    }
  }



  /**
   * Representation of an object in JSON
   */
  public static class JSONObject extends LinkedHashMap<String, Primitive> {

    /** serial version UID */
    private static final long serialVersionUID = 1L;


    /**
     * Get a type-safe value
     *
     * @param type the type required
     * @param name the name of the field
     * @param dflt default value if missing or wrong type
     * @param <T> the value's class to return
     *
     * @return the value
     */
    public <T> T get(Class<T> type, String name, T dflt) {
      Primitive prim = get(name);
      if (prim == null) {
        return dflt;
      }
      Object val = prim.getValue();
      if (val == null) {
        return null;
      }
      if (!type.isInstance(val)) {
        return dflt;
      }
      return type.cast(val);
    }


    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append('{');
      for (Map.Entry<String, Primitive> e : entrySet()) {
        buf.append(escapeString(e.getKey()));
        buf.append(':');
        buf.append(e.getValue());
        buf.append(", ");
      }
      if (buf.length() > 1) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }
  }



  /**
   * Container for a JSON primitive
   */
  public static class Primitive {

    /** The type represented by this primitive */
    final Type type;

    /** The encapsulated value */
    final Object value;


    /**
     * Create new primitive container.
     *
     * @param type contained type
     * @param value contained value
     */
    public Primitive(Type type, Object value) {
      type.getType().cast(value);
      if ((type == Type.NULL) != (value == null)) {
        throw new IllegalArgumentException("Null if and only if NULL");
      }
      this.type = type;
      this.value = value;
    }


    /**
     * Get the type encapsulated by this primitive
     *
     * @return the type
     */
    public Type getType() {
      return type;
    }


    /**
     * Get the value encapsulated by this primitive
     *
     * @return the value
     */
    public Object getValue() {
      return value;
    }


    /**
     * Get the value encapsulated by this primitive
     *
     * @param <T> required type
     * @param reqType the required type
     * @param dflt default value if type is not correct
     *
     * @return the value
     */
    public <T> T getValue(Class<T> reqType, T dflt) {
      if (reqType.isInstance(value)) {
        return reqType.cast(value);
      }
      return dflt;
    }


    /**
     * Get the value encapsulated by this primitive
     *
     * @param <T> required type
     * @param reqType the required type
     *
     * @return the value
     */
    public <T> T getValueSafe(Class<T> reqType) {
      return reqType.cast(value);
    }


    @Override
    public String toString() {
      if (type == Type.STRING) {
        return escapeString((String) value);
      }
      return String.valueOf(value);
    }
  }


  /**
   * Escape a String using JSON escaping rules
   *
   * @param val the string
   *
   * @return the escaped and quotes string
   */
  static String escapeString(String val) {
    StringBuilder buf = new StringBuilder();
    buf.append('\"');
    for (int i = 0; i < val.length(); i++) {
      char ch = val.charAt(i);
      switch (ch) {
        case '\"':
          buf.append("\\\"");
          break;
        case '\\':
          buf.append("\\\\");
          break;
        case '/':
          buf.append("\\/");
          break;
        case '\b':
          buf.append("\\b");
          break;
        case '\f':
          buf.append("\\f");
          break;
        case '\n':
          buf.append("\\n");
          break;
        case '\r':
          buf.append("\\r");
          break;
        case '\t':
          buf.append("\\t");
          break;
        default:
          if (32 <= ch && ch < 127) {
            buf.append(ch);
            break;
          }
          buf.append("\\u");
          buf.append(HEX[(ch >>> 12) & 0xf]);
          buf.append(HEX[(ch >>> 8) & 0xf]);
          buf.append(HEX[(ch >>> 4) & 0xf]);
          buf.append(HEX[ch & 0xf]);
      }
    }
    buf.append('\"');
    return buf.toString();
  }


  /**
   * Check if input represents whitespace
   *
   * @param r the input
   *
   * @return true if whitespace
   */
  private static boolean isWhite(int r) {
    return (r == ' ' || r == '\n' || r == '\r' || r == '\t' || r == '\f');
  }


  /**
   * Test reading a file.
   *
   * @param args the file name
   *
   * @throws IOException if the file cannot be read
   */
  public static void main(String[] args) throws IOException {
    Primitive prim;
    try (FileReader reader = new FileReader(args[0])) {
      prim = parse(reader);
    }
    System.out.println("Type = " + prim.getType());
    System.out.println("Value = " + prim.getValue());
  }


  /**
   * Match a literal.
   *
   * @param literal the literal to match (excluding first character)
   * @param input input
   *
   * @throws IOException if literal is not matched
   */
  private static void matchLiteral(char[] literal, Reader input)
      throws IOException {
    for (int i = 0; i < literal.length; i += 2) {
      int r = input.read();
      if (r == -1) {
        throw new EOFException();
      }
      if (r != literal[i] && r != literal[i + 1]) {
        throw new IOException(
            "Invalid character in literal " + Integer.toHexString(r)
                + " when expecting '" + literal[i] + "'");
      }
    }
  }


  /**
   * Read the next primitive from the input
   *
   * @param input the input
   *
   * @return the next primitive
   * @throws IOException if the input cannot be read, or there is invalid JSON data
   */
  public static Primitive parse(Reader input) throws IOException {
    if (!(input instanceof PushbackReader)) {
      input = new PushbackReader(input);
    }
    return parseAny((PushbackReader) input);
  }


  /**
   * Read the next primitive from the input
   *
   * @param input the input
   *
   * @return the next primitive
   * @throws IOException if the input cannot be read, or there is invalid JSON data
   */
  private static Primitive parseAny(PushbackReader input) throws IOException {
    int r = skipWhite(input);
    if (r == '{') {
      return parseObject(input);
    }
    if (r == '[') {
      return parseArray(input);
    }
    if (r == '\"') {
      return parseString(input);
    }
    if (r == 't' || r == 'T' || r == 'f' || r == 'F') {
      return parseBoolean(input, r);
    }
    if (r == 'n' || r == 'N') {
      return parseNull(input);
    }
    if (r == '-' || r == '.' || ('0' <= r && r <= '9')) {
      return parseNumber(input, r);
    }
    throw new IOException("Invalid input byte 0x" + Integer.toHexString(r));
  }


  /**
   * Parse an array from the input
   *
   * @param input the input
   *
   * @return the array
   */
  private static Primitive parseArray(PushbackReader input)
      throws IOException {
    JSONArray arr = new JSONArray();
    Primitive prim = new Primitive(Type.ARRAY, arr);
    while (true) {
      Primitive val = parseAny(input);
      arr.add(val);
      int r = skipWhite(input);
      if (r == ']') {
        return prim;
      }
      if (r != ',') {
        throw new IOException("Array continuation was not ',' but 0x"
            + Integer.toHexString(r));
      }
    }

  }


  /**
   * Parse a boolean from the input
   *
   * @param input the input
   * @param r the initial character of the literal
   *
   * @return the boolean
   */
  private static Primitive parseBoolean(Reader input, int r)
      throws IOException {
    Boolean val = r == 'T' || r == 't';
    matchLiteral(val ? LITERAL_TRUE : LITERAL_FALSE, input);
    return new Primitive(Type.BOOLEAN, val);
  }


  /**
   * Parse a null from the input
   *
   * @param input the input
   *
   * @return the null
   */
  private static Primitive parseNull(Reader input) throws IOException {
    matchLiteral(LITERAL_NULL, input);
    return new Primitive(Type.NULL, null);
  }


  /**
   * Parse a number from the input
   *
   * @param input the input
   * @param r the initial character of the number
   *
   * @return the number
   */
  private static Primitive parseNumber(PushbackReader input, int r)
      throws IOException {
    StringBuilder buf = new StringBuilder();
    // process first character
    int s = 1;
    switch (r) {
      case '-':
        buf.append('-');
        s = 0;
        break;
      case '.':
        buf.append("0.");
        s = 2;
        break;
      default:
        buf.append((char) r);
        break;
    }

    // read rest of number
    boolean notDone = true;
    while (notDone) {
      r = input.read();
      if (r == -1) {
        notDone = false;
      }

      switch (s) {
        case 0:
          // expecting first digit or period
          if (r == '.') {
            buf.append("0.");
            s = 2;
          } else if ('0' <= r && r <= '9') {
            buf.append((char) r);
            s = 1;
          } else {
            buf.append((char) r);
            throw new IOException("Invalid numeric input: \""
                + buf + "\"");
          }
          break;
        case 1:
          // seen at least one digit, expecting digit, period or 'e'
          if (r == '.') {
            buf.append('.');
            s = 2;
          } else if ('0' <= r && r <= '9') {
            buf.append((char) r);
          } else if (r == 'e' || r == 'E') {
            buf.append('e');
            s = 3;
          } else {
            notDone = false;
          }
          break;
        case 2:
          // seen a period, expecting digit or 'e'
          if ('0' <= r && r <= '9') {
            buf.append((char) r);
          } else if (r == 'e' || r == 'E') {
            buf.append('e');
            s = 3;
          } else {
            notDone = false;
          }
          break;
        case 3:
          // seen an 'e', must see '+' or '-'
          if (r == '+' || r == '-') {
            buf.append((char) r);
            s = 4;
          } else {
            buf.append((char) r);
            throw new IOException("Invalid numeric input: \""
                + buf + "\"");
          }
          break;
        case 4:
          // seen "e+" or "e-", must see digit
          if ('0' <= r && r <= '9') {
            buf.append((char) r);
            s = 5;
          } else {
            buf.append((char) r);
            throw new IOException("Invalid numeric input: \""
                + buf + "\"");
          }
          break;
        case 5:
          // seen "e+" or "e-" and at least one digit. Expect more digits
          // or finish
          if ('0' <= r && r <= '9') {
            buf.append((char) r);
          } else {
            notDone = false;
          }
          break;
      }
    }
    input.unread(r);
    Number val;

    // convert to a good number type
    if (s == 1) {
      Long lval = Long.valueOf(buf.toString());
      if (lval == lval.intValue()) {
        val = lval.intValue();
      } else {
        val = lval;
      }
    } else {
      val = Double.valueOf(buf.toString());
    }
    return new Primitive(Type.NUMBER, val);
  }


  /**
   * Parse an object from the input
   *
   * @param input the input
   *
   * @return the object
   */

  private static Primitive parseObject(PushbackReader input)
      throws IOException {
    JSONObject obj = new JSONObject();
    Primitive prim = new Primitive(Type.OBJECT, obj);
    while (true) {
      int r = skipWhite(input);
      // name must start with a quote
      if (r != '\"') {
        throw new IOException(
            "Object pair's name did not start with '\"' but with 0x"
                + Integer.toHexString(r));
      }
      Primitive name = parseString(input);

      // then comes the ':'
      r = skipWhite(input);
      if (r != ':') {
        throw new IOException(
            "Object pair-value separator was not start with ':' but 0x"
                + Integer.toHexString(r));
      }

      // and then the value
      Primitive value = parseAny(input);
      obj.put(name.getValueSafe(String.class), value);
      r = skipWhite(input);
      if (r == '}') {
        return prim;
      }
      if (r != ',') {
        throw new IOException("Object continuation was not ',' but 0x"
            + Integer.toHexString(r));
      }
    }
  }


  /**
   * Parse an array from the input
   *
   * @param input the input
   *
   * @return the array
   */
  private static Primitive parseString(Reader input) throws IOException {
    int s = 0;
    int u = 0;
    StringBuilder buf = new StringBuilder();
    boolean notDone = true;
    while (notDone) {
      int r = input.read();
      switch (s) {
        case 0:
          // regular character probable
          if (r == '"') {
            notDone = false;
            break;
          }
          if (r == '\\') {
            s = 1;
            break;
          }
          if (r == -1) {
            throw new EOFException("Unterminated string");
          }
          buf.append((char) r);
          break;

        case 1:
          // expecting an escape sequence
          switch (r) {
            case '"':
              buf.append('\"');
              break;
            case '\\':
              buf.append('\\');
              break;
            case '/':
              buf.append('/');
              break;
            case 'b':
              buf.append('\b');
              break;
            case 'f':
              buf.append('\f');
              break;
            case 'n':
              buf.append('\n');
              break;
            case 'r':
              buf.append('\r');
              break;
            case 't':
              buf.append('\t');
              break;
            case 'u':
              u = 0;
              s = 2;
              break;
            default:
              throw new IOException(
                  "Invalid escape sequence \"\\" + ((char) r) + "\"");
          }
          if (s == 1) {
            s = 0;
          }
          break;

        case 2: // fall thru
        case 3: // fall thru
        case 4: // fall thru
        case 5: // fall thru
          // Unicode escape
          u = u * 16;
          if ('0' <= r && r <= '9') {
            u += r - '0';
          } else if ('a' <= r && r <= 'f') {
            u += r - 'a' + 10;
          } else if ('A' <= r && r <= 'F') {
            u += r - 'A' + 10;
          } else {
            throw new IOException(
                "Invalid hex character in \\u escape");
          }
          s++;
          if (s == 6) {
            s = 0;
            buf.append((char) u);
          }
          break;
      }
    }

    // finally create the string
    return new Primitive(Type.STRING, buf.toString());
  }


  /**
   * Skip whitespace and return the first non-white character
   *
   * @param input the input
   *
   * @return the first non-white character
   */
  private static int skipWhite(Reader input) throws IOException {
    int r;
    do {
      r = input.read();
    }
    while (isWhite(r));
    if (r == -1) {
      throw new EOFException();
    }
    return r;
  }
}
