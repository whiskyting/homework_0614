package tw.org.iii.twtor;

import java.util.Scanner;

public class BMI {

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		
		//輸入基本資料		
		System.out.print("請輸入身高(cm): ");
		double height = scanner.nextInt();
		
		
		System.out.print("請輸入體重(kg): ");
		double weight = scanner.nextInt();
		
		//計算BMI
		double BMI = weight/((height*height)/10000);
		System.out.printf("BMI的值為:%.2f",BMI);
		System.out.println();

		
		
		//判斷胖子
		if (BMI >=35) {
			System.out.println("吸ㄉㄨㄚˇ摳");
		}else {
			if (BMI>=30) {
				System.out.println("小 ㄉㄨㄚˇ摳");
			}else {
				if(BMI>=27) {
					System.out.println("身材勉強(輕度肥胖");
				}else {
					if(BMI>=24) {
						System.out.println("身材還可以(過重)");
					}else {
						if (BMI>=18.5) {
							System.out.println("是個正常人");
						}else {
							System.out.println("死紙片人");
						}
					}
				}
			}
		}
		
			
	}

}
