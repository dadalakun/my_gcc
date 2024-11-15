public class TestClass3 {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        if (a < b) {
            if (a + b > 25) {
                a = a + 5;
            } else {
                b = b + 5;
            }
        } else {
            a = a - 5;
        }
        System.out.println("a: " + a + ", b: " + b);
    }
}
