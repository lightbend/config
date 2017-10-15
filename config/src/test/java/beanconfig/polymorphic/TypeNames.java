package beanconfig.polymorphic;

import com.typesafe.config.ConfigSubTypes;
import com.typesafe.config.ConfigSubTypes.Type;
import com.typesafe.config.ConfigTypeInfo;
import com.typesafe.config.ConfigTypeName;

public class TypeNames {

    @ConfigTypeInfo
    @ConfigSubTypes({
        @Type(value = Dog.class, name = "doggy"),
        @Type(Cat.class) /* defaults to "Cat" then */
    })
    public static class Animal {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @ConfigSubTypes({
        @Type(MaineCoon.class),
        @Type(Persian.class)
    })
    public static abstract class Cat extends Animal {

        private boolean purrs;

        public boolean isPurrs() {
            return purrs;
        }

        public void setPurrs(boolean purrs) {
            this.purrs = purrs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Cat cat = (Cat) o;
            return purrs == cat.purrs;
        }

        @Override
        public int hashCode() {
            return (purrs ? 1 : 0);
        }
    }

    public static class Dog extends Animal {

        private int ageInYears;

        public int getAgeInYears() {
            return ageInYears;
        }

        public void setAgeInYears(int ageInYears) {
            this.ageInYears = ageInYears;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Dog dog = (Dog) o;
            return ageInYears == dog.ageInYears;
        }

        @Override
        public int hashCode() {
            return ageInYears;
        }

        @Override
        public String toString() {
            return "Dog{" +
                "ageInYears=" + ageInYears +
                '}';
        }
    }

    /*
     * Uses default name ("MaineCoon") since there's no @ConfigTypeName,
     * nor did supertype specify name.
     */
    public static class MaineCoon extends Cat {

        public MaineCoon() {
            super();
        }

        @Override
        public String toString() {
            return "Cat{" +
                "purrs=" + isPurrs() +
                '}';
        }
    }

    @ConfigTypeName("persialaisKissa")
    public static class Persian extends Cat {

        public Persian() {
            super();
        }

        @Override
        public String toString() {
            return "Cat{" +
                "purrs=" + isPurrs() +
                '}';
        }
    }

}
