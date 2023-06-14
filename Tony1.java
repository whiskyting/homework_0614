package test0613;

public class Tony1 {

	public static void main(String[] args) {
		
//		int [] primenumber = new int [0];
		int count = 0;
		int i;
		
		for(i=1 ;i<=100;i++) {
			count = 0; // 須重置
			for(int j=1; j<=i;j++) {
				if (i%j ==0) {
//					System.out.println("test"+i+" "+j);
					count++;
				}
			}
			if (count==2) {
//				System.out.println("count"+count);
				System.out.println("質數"+i);
//				primenumber[primenumber.length]=i;
				
			}

		}
//		System.out.println(primenumber.length);
//		for(int k=1; k<=primenumber.length;k++) {
//			System.out.println(primenumber.length);
//		}
		

	}

}
