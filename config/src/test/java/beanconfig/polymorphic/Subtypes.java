package beanconfig.polymorphic;

import com.typesafe.config.ConfigSubTypes;
import com.typesafe.config.ConfigSubTypes.Type;
import com.typesafe.config.ConfigTypeInfo;
import com.typesafe.config.ConfigTypeName;
import com.typesafe.config.Optional;

public class Subtypes {

    @ConfigTypeInfo
    @ConfigSubTypes({
        @Type(value = SubB.class),
        @Type(value = SubC.class),
        @Type(value = SubD.class)
    })
    public static abstract class SuperType {
    }

    @ConfigTypeName("TypeB")
    public static class SubB extends SuperType {

        private int b = 1;

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }
    }

    public static class SubC extends SuperType {

        private int c = 2;

        public int getC() {
            return c;
        }

        public void setC(int c) {
            this.c = c;
        }
    }

    public static class SubD extends SuperType {

        private int d;

        public int getD() {
            return d;
        }

        public void setD(int d) {
            this.d = d;
        }
    }

    @ConfigTypeInfo(defaultImpl = DefaultImpl.class)
    public static abstract class SuperTypeWithDefault {}

    public static class DefaultImpl extends SuperTypeWithDefault {

        @Optional
        private int a;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }
    }

    @ConfigTypeInfo
    @ConfigSubTypes({
        @Type(ImplX.class),
        @Type(ImplY.class)
    })
    public static abstract class BaseX {}

    @ConfigTypeName("x")
    public static class ImplX extends BaseX {

        private int x;

        public ImplX() {
        }

        public ImplX(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }
    }

    @ConfigTypeName("y")
    public static class ImplY extends BaseX {

        private int y;

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    public static class AtomicWrapper {

        private BaseX value;

        public AtomicWrapper() {
        }

        public AtomicWrapper(int x) {
            value = new ImplX(x);
        }

        public BaseX getDirectValue() {
            return value;
        }

        public int getValue() {
            return ((ImplX) value).getX();
        }

        public void setValue(int value) {
            this.value = new ImplX(value);
        }
    }

}
