package beanconfig.polymorphic;

import com.typesafe.config.ConfigSubTypes;
import com.typesafe.config.ConfigSubTypes.Type;
import com.typesafe.config.ConfigTypeInfo;

public class WithGenerics {

    @ConfigTypeInfo(property = "object-type")
    @ConfigSubTypes({
        @Type(value = Dog.class, name = "doggy")
    })
    public static abstract class Animal {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Dog extends Animal {

        private int boneCount;

        public Dog() {
            super();
        }

        public int getBoneCount() {
            return boneCount;
        }

        public void setBoneCount(int boneCount) {
            this.boneCount = boneCount;
        }
    }

    public static class ContainerWithAnimal<T extends Animal> {

        private T animal;

        public void setAnimal(T animal) {
            this.animal = animal;
        }

        public T getAnimal() {
            return animal;
        }
    }

}
