package beanconfig.polymorphic;

import beanconfig.polymorphic.Subtypes.AtomicWrapper;
import beanconfig.polymorphic.Subtypes.SuperType;
import beanconfig.polymorphic.Subtypes.SuperTypeWithDefault;

public class SubtypesConfigs {

    public static class SuperTypeConfig {

        private SuperType bean1;
        private SuperType bean2;

        public SuperType getBean1() {
            return bean1;
        }

        public void setBean1(SuperType bean1) {
            this.bean1 = bean1;
        }

        public SuperType getBean2() {
            return bean2;
        }

        public void setBean2(SuperType bean2) {
            this.bean2 = bean2;
        }
    }

    public static class DefaultImplConfig {

        private SuperTypeWithDefault defaultImpl1;
        private SuperTypeWithDefault defaultImpl2;
        private SuperTypeWithDefault defaultImpl3;

        public SuperTypeWithDefault getDefaultImpl1() {
            return defaultImpl1;
        }

        public void setDefaultImpl1(SuperTypeWithDefault defaultImpl1) {
            this.defaultImpl1 = defaultImpl1;
        }

        public SuperTypeWithDefault getDefaultImpl2() {
            return defaultImpl2;
        }

        public void setDefaultImpl2(SuperTypeWithDefault defaultImpl2) {
            this.defaultImpl2 = defaultImpl2;
        }

        public SuperTypeWithDefault getDefaultImpl3() {
            return defaultImpl3;
        }

        public void setDefaultImpl3(SuperTypeWithDefault defaultImpl3) {
            this.defaultImpl3 = defaultImpl3;
        }
    }

    public static class AtomicWrapperConfig {

        private AtomicWrapper atomicWrapper;

        public AtomicWrapper getAtomicWrapper() {
            return atomicWrapper;
        }

        public void setAtomicWrapper(AtomicWrapper atomicWrapper) {
            this.atomicWrapper = atomicWrapper;
        }
    }

}
