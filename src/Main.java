import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Swing is single-threaded: build the window on its event thread
        MarketPriceStrategy market = MarketPriceFactory.create(args.length > 0 ? args[0] : null);
        SwingUtilities.invokeLater(() -> {
            GamePanel panel = new GamePanel(market);
            JFrame frame = new JFrame("Little Farm - CN201");
            frame.add(panel);
            frame.pack();
            frame.setResizable(true);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
            panel.requestFocusInWindow();
            panel.start();
        });
    }
}
