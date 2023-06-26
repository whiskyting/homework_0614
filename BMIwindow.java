package test0613;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class BMIwindow extends JFrame {
	private JTextField Hinput;
	private JTextField Winput;
	
	public BMIwindow() {
		Hinput = new JTextField(10);
		Winput = new JTextField();
		Hinput.setBounds(0,0,10,10);
		setSize(640,480);
		setVisible(true);
		setTitle("BMI");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		setLayout(new BorderLayout());
		JPanel top = new JPanel(new BorderLayout());
		add(top,BorderLayout.NORTH); //把盤子放在桌子的北方
		
		top.add(Hinput);
		
		
		
	}
	

	
	
	public static void main(String[] args) {
		BMIwindow wi = new BMIwindow();

	}

}
