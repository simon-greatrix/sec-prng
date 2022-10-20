package prng;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


public class Stacker implements AutoCloseable {

  private static final AtomicInteger ID_SRC = new AtomicInteger();

  private static final ConcurrentHashMap<Thread, Sampler> SAMPLERS = new ConcurrentHashMap<>();

  // final static Logger LOGGER = LoggerFactory.getLogger(Stacker.class);



  static class Sampler implements Runnable {

    private final long id_;

    private final ThreadMXBean mx_ = ManagementFactory.getThreadMXBean();

    private final Thread thread_;

    private final Writer writer_;

    private volatile boolean running_ = true;


    Sampler(Writer writer) {
      thread_ = Thread.currentThread();
      id_ = thread_.getId();
      writer_ = writer;
    }


    public void message(String msg) {
      writer_.enque(msg);
    }


    @Override
    public void run() {
      while (running_ && thread_.isAlive()) {
        ThreadInfo info = mx_.getThreadInfo(id_, Integer.MAX_VALUE);
        StackTraceElement[] stack = info.getStackTrace();
        if (stack.length != 0) {
          writer_.enque(stack);
        }
        try {
          Thread.sleep(50);
        } catch (InterruptedException ie) {
          // LOGGER.error("Data collection interrupted.", ie);
          break;
        }
      }
      writer_.enque(new StackTraceElement[0]);
    }


    public void stop() {
      running_ = false;
    }

  }



  static class Writer implements Runnable {

    private final LinkedBlockingQueue<Object> stacks_ = new LinkedBlockingQueue<>();


    public void enque(Object stack) {
      stacks_.add(stack);
    }


    @Override
    public void run() {
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmssSSS").withZone(ZoneId.systemDefault());
      Instant now = Instant.now();
      File file = new File("/tmp/stack" + dtf.format(now) + ".txt");
      System.out.println("Stacker at " + file.getAbsolutePath());
      try (BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
        while (true) {
          Object obj;
          try {
            obj = stacks_.take();
          } catch (InterruptedException ie) {
            // LOGGER.error("Stack logging interrupted.", ie);
            continue;
          }
          if (obj == null) {
            continue;
          }
          if (obj instanceof String) {
            fw.write(String.valueOf(obj));
            fw.write('\n');
            fw.write('\n');
            continue;
          }
          StackTraceElement[] stack = (StackTraceElement[]) obj;
          if (stack.length == 0) {
            break;
          }

          for (StackTraceElement st : stack) {
            fw.write(st.getClassName());
            fw.write('.');
            fw.write(st.getMethodName());
            fw.write(':');
            fw.write(Integer.toString(st.getLineNumber()));
            fw.write('\n');
          }
          fw.write('\n');
        }
        fw.flush();
        fw.close();
      } catch (IOException ioe) {
        // LOGGER.error("Stack logging file writer failed.", ioe);
      }
    }

  }


  public static void start() {
    Thread me = Thread.currentThread();
    Sampler sampler = SAMPLERS.get(me);
    if (sampler != null) {
      return;
    }

    Writer writer = new Writer();
    sampler = new Sampler(writer);
    int id = ID_SRC.incrementAndGet();
    Thread t = new Thread(writer, "Stacker-Writer-" + id);
    t.start();
    t = new Thread(sampler, "Stacker-Sampler-" + id);
    t.start();
    SAMPLERS.put(me, sampler);
  }


  public static void stop() {
    Thread me = Thread.currentThread();
    Sampler sampler = SAMPLERS.remove(me);
    if (sampler != null) {
      sampler.stop();
    }
  }


  public Stacker() {
    start();
  }


  @Override
  public void close() {
    stop();
  }


  public void message(String msg) {
    Thread me = Thread.currentThread();
    Sampler sampler = SAMPLERS.get(me);
    sampler.message(msg);
  }

}
