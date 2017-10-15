package beanconfig.polymorphic;

import com.typesafe.config.ConfigSubTypes;
import com.typesafe.config.ConfigSubTypes.Type;
import com.typesafe.config.ConfigTypeInfo;

import java.util.List;

public class PolymorphicWithDefaultImpl {

    @ConfigTypeInfo(defaultImpl = LegacyInter.class)
    @ConfigSubTypes(value = {
        @Type(value = MyInter.class, name = "mine")
    })
    public interface Inter {}

    public static class MyInter implements Inter {

        private List<String> blah;

        public List<String> getBlah() {
            return blah;
        }

        public void setBlah(List<String> blah) {
            this.blah = blah;
        }
    }

    public static class LegacyInter extends MyInter {
    }

    /*
     * can use non-deprecated value for the same
     */
    @ConfigTypeInfo(defaultImpl = Void.class)
    public static class DefaultWithVoidAsDefault {}

    /*
     * one with no defaultImpl nor listed subtypes
     */
    @ConfigTypeInfo
    public abstract static class MysteryPolymorphic {}

}
