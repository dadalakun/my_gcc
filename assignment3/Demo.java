public class Demo {
    public static void main(String[] args) {
        // Example 1: Primitive parameters, should be memoized
        ExampleSupport.sum(10, 20);
        ExampleSupport.sum(10, 20);

        // Example 2: Object parameter with nested objects, should be memoized
        ASupport aObj = new ASupport(10);
        CSupport cObj = new CSupport(5, aObj);
        CSupport.m(cObj);
        CSupport.m(cObj);

        // Example 3: Object parameter with primitive fields, should be memoized
        DSupport dObj = new DSupport(10, 20);
        DSupport.m(dObj);
        DSupport.m(dObj);

        // Example 4: Should NOT be memoized (class does not end with "Support")
        NonSupportClass.m(5, 6);
        NonSupportClass.m(5, 6);

        // Example 5: Should NOT be memoized (return type is Object)
        ESupport.m(5, 6);
        ESupport.m(5, 6);
    }
}