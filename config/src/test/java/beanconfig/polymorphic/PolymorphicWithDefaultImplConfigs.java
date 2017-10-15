package beanconfig.polymorphic;

import beanconfig.polymorphic.PolymorphicWithDefaultImpl.DefaultWithVoidAsDefault;
import beanconfig.polymorphic.PolymorphicWithDefaultImpl.Inter;
import beanconfig.polymorphic.PolymorphicWithDefaultImpl.MysteryPolymorphic;

public class PolymorphicWithDefaultImplConfigs {

    public static class InnerConfig {

        private Inter object;
        private Inter array;

        public Inter getObject() {
            return object;
        }

        public void setObject(Inter object) {
            this.object = object;
        }

        public Inter getArray() {
            return array;
        }

        public void setArray(Inter array) {
            this.array = array;
        }
    }

    public static class DefaultWithVoidAsDefaultConfig {

        private DefaultWithVoidAsDefault defaultAsVoid1;
        private DefaultWithVoidAsDefault defaultAsVoid2;

        public DefaultWithVoidAsDefault getDefaultAsVoid1() {
            return defaultAsVoid1;
        }

        public void setDefaultAsVoid1(DefaultWithVoidAsDefault defaultAsVoid1) {
            this.defaultAsVoid1 = defaultAsVoid1;
        }

        public DefaultWithVoidAsDefault getDefaultAsVoid2() {
            return defaultAsVoid2;
        }

        public void setDefaultAsVoid2(DefaultWithVoidAsDefault defaultAsVoid2) {
            this.defaultAsVoid2 = defaultAsVoid2;
        }
    }

    public static class MysteryPolymorphicConfig {

        private MysteryPolymorphic badType;

        public MysteryPolymorphic getBadType() {
            return badType;
        }

        public void setBadType(MysteryPolymorphic badType) {
            this.badType = badType;
        }
    }
}
