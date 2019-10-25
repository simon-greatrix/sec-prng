package prng;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * A logger with everything permanently turned off.
 *
 * @author Simon Greatrix on 25/10/2019.
 */
public class NoOpLogger implements Logger {

  private final String name;


  public NoOpLogger(Class<?> type) {
    name = type.getName();
  }


  @Override
  public void debug(String msg) {
    // do nothing
  }


  @Override
  public void debug(String format, Object arg) {
    // do nothing
  }


  @Override
  public void debug(String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void debug(String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void debug(String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void debug(Marker marker, String msg) {
    // do nothing
  }


  @Override
  public void debug(Marker marker, String format, Object arg) {
    // do nothing
  }


  @Override
  public void debug(Marker marker, String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void debug(Marker marker, String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void debug(Marker marker, String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void error(String msg) {
    // do nothing
  }


  @Override
  public void error(String format, Object arg) {
    // do nothing
  }


  @Override
  public void error(String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void error(String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void error(String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void error(Marker marker, String msg) {
    // do nothing
  }


  @Override
  public void error(Marker marker, String format, Object arg) {
    // do nothing
  }


  @Override
  public void error(Marker marker, String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void error(Marker marker, String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void error(Marker marker, String msg, Throwable t) {
    // do nothing
  }


  @Override
  public String getName() {
    return name;
  }


  @Override
  public void info(String msg) {
    // do nothing
  }


  @Override
  public void info(String format, Object arg) {
    // do nothing
  }


  @Override
  public void info(String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void info(String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void info(String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void info(Marker marker, String msg) {
    // do nothing
  }


  @Override
  public void info(Marker marker, String format, Object arg) {
    // do nothing
  }


  @Override
  public void info(Marker marker, String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void info(Marker marker, String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void info(Marker marker, String msg, Throwable t) {
    // do nothing
  }


  @Override
  public boolean isDebugEnabled() {
    return false;
  }


  @Override
  public boolean isDebugEnabled(Marker marker) {
    return false;
  }


  @Override
  public boolean isErrorEnabled() {
    return false;
  }


  @Override
  public boolean isErrorEnabled(Marker marker) {
    return false;
  }


  @Override
  public boolean isInfoEnabled() {
    return false;
  }


  @Override
  public boolean isInfoEnabled(Marker marker) {
    return false;
  }


  @Override
  public boolean isTraceEnabled() {
    return false;
  }


  @Override
  public boolean isTraceEnabled(Marker marker) {
    return false;
  }


  @Override
  public boolean isWarnEnabled() {
    return false;
  }


  @Override
  public boolean isWarnEnabled(Marker marker) {
    return false;
  }


  @Override
  public void trace(String msg) {
    // do nothing
  }


  @Override
  public void trace(String format, Object arg) {
    // do nothing
  }


  @Override
  public void trace(String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void trace(String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void trace(String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void trace(Marker marker, String msg) {
    // do nothing
  }


  @Override
  public void trace(Marker marker, String format, Object arg) {
    // do nothing
  }


  @Override
  public void trace(Marker marker, String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void trace(Marker marker, String format, Object... argArray) {
    // do nothing
  }


  @Override
  public void trace(Marker marker, String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void warn(String msg) {
    // do nothing
  }


  @Override
  public void warn(String format, Object arg) {
    // do nothing
  }


  @Override
  public void warn(String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void warn(String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void warn(String msg, Throwable t) {
    // do nothing
  }


  @Override
  public void warn(Marker marker, String msg) {
    // do nothing
  }


  @Override
  public void warn(Marker marker, String format, Object arg) {
    // do nothing
  }


  @Override
  public void warn(Marker marker, String format, Object arg1, Object arg2) {
    // do nothing
  }


  @Override
  public void warn(Marker marker, String format, Object... arguments) {
    // do nothing
  }


  @Override
  public void warn(Marker marker, String msg, Throwable t) {
    // do nothing
  }
}
