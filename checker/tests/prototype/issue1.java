import org.checkerframework.checker.prototype.qual.*;

/* The following test case should not pass the test(but it passes) as I have made the checker only relevant to
Byte.class, Integer.class, Short.class, Long.class using RelevantJavaTypes  */
class issue1 {

    @Unsigned float f1;
    @Unsigned double d1;

    void m1() {
        @Unsigned float f2 = f1;
        @Unsigned double d2 = d1;
    }
}
