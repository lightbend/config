package beanconfig.polymorphic;

import beanconfig.polymorphic.TypeNames.Animal;

import java.util.List;

public class TypeNamesConfigs {

    public static class SingleTypeConfig {

        private Animal dog;

        public Animal getDog() {
            return dog;
        }

        public void setDog(Animal dog) {
            this.dog = dog;
        }
    }

    public static class TypeNamesListConfig {

        List<Animal> dogs;
        List<Animal> cats;
        List<Animal> animals;

        public List<Animal> getDogs() {
            return dogs;
        }

        public void setDogs(List<Animal> dogs) {
            this.dogs = dogs;
        }

        public List<Animal> getCats() {
            return cats;
        }

        public void setCats(List<Animal> cats) {
            this.cats = cats;
        }

        public List<Animal> getAnimals() {
            return animals;
        }

        public void setAnimals(List<Animal> animals) {
            this.animals = animals;
        }
    }

}
