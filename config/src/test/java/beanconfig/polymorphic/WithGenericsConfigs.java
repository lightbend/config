package beanconfig.polymorphic;

import beanconfig.polymorphic.WithGenerics.Animal;
import beanconfig.polymorphic.WithGenerics.ContainerWithAnimal;

public class WithGenericsConfigs {

    public static class WrapperWithGenericsConfig {

        private ContainerWithAnimal<Animal> wrapperWithDog;

        public ContainerWithAnimal<Animal> getWrapperWithDog() {
            return wrapperWithDog;
        }

        public void setWrapperWithDog(ContainerWithAnimal<Animal> wrapperWithDog) {
            this.wrapperWithDog = wrapperWithDog;
        }
    }

}
