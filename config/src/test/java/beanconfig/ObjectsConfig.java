package beanconfig;


import com.typesafe.config.Optional;

public class ObjectsConfig {
    public static class ValueObject {
        @Optional
        private String nullableValue;
        private String nonNullableValue;

        public String getNonNullableValue() {
          return nonNullableValue;
        }

        public void setNonNullableValue(String nonNullableValue) {
          this.nonNullableValue = nonNullableValue;
        }

        public String getNullableValue() {
          return nullableValue;
        }

        public void setNullableValue(String nullableValue) {
          this.nullableValue = nullableValue;
        }
    }

    private ValueObject valueObject;

    public ValueObject getValueObject() {

        return valueObject;
    }

    public void setValueObject(ValueObject valueObject) {
        this.valueObject = valueObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObjectsConfig)) {
            return false;
        }

        ObjectsConfig that = (ObjectsConfig) o;

        return !(getValueObject() != null ? !getValueObject().equals(that.getValueObject()) : that.getValueObject() != null);

    }

    @Override
    public int hashCode() {
        return getValueObject() != null ? getValueObject().hashCode() : 0;
    }


    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ObjectsConfig{");
        sb.append("innerType=").append(valueObject);
        sb.append('}');
        return sb.toString();
    }
}
