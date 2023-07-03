import javax.swing.*;

public class ClientWinFrame  extends JFrame {

    public ClientWinFrame(int[] bingo,int width,int height) {
        this.setBounds(100, 100, 566, 395);// 內部 700x500
        this.setResizable(false);// 禁止視窗大小調整
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setTitle("Bingo");
        this.getContentPane().setLayout(null);

        if (width > 0 && height > 0) {
            int w = 450 / width;
            int h = 250 / height;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    JButton but = new JButton(String.valueOf(bingo[y*width+x]));
                    but.setBounds(30 + x * (w + 10), 30 + y * (h + 10), w, h);
                    but.setEnabled(false);
                    this.add(but);
                }
            }
            this.repaint();
        }
    }

}
