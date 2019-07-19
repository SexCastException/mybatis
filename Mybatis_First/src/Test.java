import java.io.File;

public class Test {

	public static void main(String[] args) {
		try {
			File file = new File("D://qq","aa.mp4");
			file.mkdirs();
			File file1 = new File("D://qq","aa.mp4");
			file1.createNewFile();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
