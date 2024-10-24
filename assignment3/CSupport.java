public class CSupport {
    int g;
    ASupport a;

    public CSupport(int g, ASupport a) {
        this.g = g;
        this.a = a;
    }

    public static int m(CSupport obj) {
        return obj.g + obj.a.f;
    }
}