package beanconfig.polymorphic;

import beanconfig.polymorphic.WithServiceLoader.ExampleSPI;

import java.util.List;

public class WithServiceLoaderConfigs {

    public static class SimpleServiceLoader {

        private ExampleSPI singleTag;
        private List<ExampleSPI> otherTags;

        public ExampleSPI getSingleTag() {
            return singleTag;
        }

        public void setSingleTag(ExampleSPI singleTag) {
            this.singleTag = singleTag;
        }

        public List<ExampleSPI> getOtherTags() {
            return otherTags;
        }

        public void setOtherTags(List<ExampleSPI> otherTags) {
            this.otherTags = otherTags;
        }
    }

}
