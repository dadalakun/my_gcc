public class DSupport {
    int f;
    int g;

    public DSupport(int f, int g) {
        this.f = f;
        this.g = g;
    }

    public static int m(DSupport obj) {
        return obj.f + obj.g;
    }
}