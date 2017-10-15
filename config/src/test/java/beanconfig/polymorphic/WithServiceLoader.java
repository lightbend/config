package beanconfig.polymorphic;

import com.typesafe.config.ConfigTypeInfo;
import com.typesafe.config.ConfigTypeName;

public class WithServiceLoader {

    public interface ExampleTag {
    }

    @ConfigTypeInfo
    public interface ExampleSPI extends ExampleTag {
    }

    @ConfigTypeName("a")
    public static class ImplA implements ExampleSPI {

        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @ConfigTypeName("b")
    public static class ImplB implements ExampleSPI {

        private int value;

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

}
