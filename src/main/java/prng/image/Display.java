package prng.image;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.*;
import java.util.Random;
import java.util.function.DoubleConsumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import prng.SecureRandomProvider;

public class Display extends JFrame {
    
    class Updater extends Thread implements DoubleConsumer {
        public void run() {
            painter.create(this);
            doRepaint();
        }

        @Override
        public void accept(double value) {
            doRepaint();
        }
        
        private void doRepaint() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    Display.this.repaint();
                }
            });
        }
    }
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    class Comp extends JComponent {

        /**
         *
         */
        private static final long serialVersionUID = 1L;


        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setClip(0, 0, getWidth(), getHeight());
            painter.paint(g2);
        }

    }


    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandomProvider.install(false);
        Random rand = SecureRandom.getInstance("Nist-SHA256",SecureRandomProvider.NAME);
        Painter p = new Combined(rand);
        new Display(p);
    }

    final Painter painter;


    public Display(Painter painter) {
        super("Security Image Display");
        this.painter = painter;
        painter.create((d) -> {});

        Container pane = getContentPane();
        pane.setSize(new Dimension(512, 512));
        BorderLayout layout = new BorderLayout();
        pane.setLayout(layout);
        pane.add(new Comp(), BorderLayout.CENTER);
        JButton button = new JButton("Regenerate");
        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Updater u = new Updater();
                u.start();
            }

        });
        pane.add(button, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(800, 800));
        setMinimumSize(new Dimension(200, 200));
        setSize(new Dimension(800, 800));
        validate();
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
