package prng.image;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

public class Display extends JFrame {

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

    class Updater extends Thread implements DoubleConsumer {
        @Override
        public void accept(double value) {
            doRepaint();
        }


        private void doRepaint() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Display.this.repaint();
                }
            });
        }


        @Override
        public void run() {
            painter.create(this);
            doRepaint();
        }
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;


    public static void main(String[] args)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandomProvider.install(false);
        Random rand = SecureRandom.getInstance("Nist/SHA256",
                SecureRandomProvider.NAME);
        Painter p = new Combined(rand);
        new Display(p);
    }

    final Painter painter;


    public Display(Painter painter) {
        super("Security Image Display");
        this.painter = painter;

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

        setPreferredSize(new Dimension(1024, 1024));
        setMinimumSize(new Dimension(200, 200));
        setSize(new Dimension(1024, 1024));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        validate();
        setVisible(true);
        new Updater().start();
    }
}
