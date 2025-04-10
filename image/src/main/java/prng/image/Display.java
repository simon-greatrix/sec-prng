package prng.image;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.DoubleConsumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import prng.SecureRandomProvider;

/** An application to display the image. */
public class Display extends JFrame {

  private static final long serialVersionUID = 1L;


  /**
   * Application entry point.
   *
   * @param args the command line arguments
   *
   * @throws NoSuchAlgorithmException if the random algorithm is not available
   * @throws NoSuchProviderException  if the provider is not available
   */
  public static void main(String[] args)
      throws NoSuchAlgorithmException, NoSuchProviderException {
    SecureRandomProvider.install(false);
    Random rand = SecureRandom.getInstance(
        "Nist/SHA256",
        SecureRandomProvider.NAME
    );
    Painter p = new Combined(rand);
    new Display(p);
  }


  /* Holder for an image. */
  class Comp extends JComponent {

    private static final long serialVersionUID = 1L;


    @Override
    public void paint(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;
      g2.setClip(0, 0, getWidth(), getHeight());
      painter.paint(g2);
    }

  }



  class Updater extends Thread implements DoubleConsumer {

    @Override
    public void accept(double value) {
      doRepaint();
    }


    private void doRepaint() {
      SwingUtilities.invokeLater(Display.this::repaint);
    }


    @Override
    public void run() {
      painter.create(this);
      doRepaint();
    }

  }



  /** The painter whose image is displayed. */
  private final Painter painter;


  /**
   * Create a new instance.
   *
   * @param painter the painter to display
   */
  public Display(Painter painter) {
    super("Security Image Display");
    this.painter = painter;

    Container pane = getContentPane();
    pane.setSize(new Dimension(512, 512));
    BorderLayout layout = new BorderLayout();
    pane.setLayout(layout);
    pane.add(new Comp(), BorderLayout.CENTER);
    JButton button = new JButton("Regenerate");
    button.addActionListener(e -> {
      Updater u = new Updater();
      u.start();
    });
    pane.add(button, BorderLayout.SOUTH);

    setPreferredSize(new Dimension(1024, 1024));
    setMinimumSize(new Dimension(200, 200));
    setSize(new Dimension(1024, 1024));
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    validate();
    setVisible(true);
    new Updater().start();
  }

}
