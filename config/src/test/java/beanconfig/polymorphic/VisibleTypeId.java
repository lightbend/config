package beanconfig.polymorphic;

import com.typesafe.config.ConfigTypeInfo;
import com.typesafe.config.ConfigTypeName;

public class VisibleTypeId {

    @ConfigTypeInfo(visible = true)
    @ConfigTypeName("BaseType")
    public static class PropertyBean {

        private int a = 3;

        private String type;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

}
