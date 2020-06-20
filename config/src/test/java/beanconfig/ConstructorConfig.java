package beanconfig;

import com.typesafe.config.Optional;

import java.beans.ConstructorProperties;

public class ConstructorConfig {
  private final String foo;
  private final String bar;
  private final NestedConfig nested;
  private final NestedWithoutAnnotation nestedWithoutAnnotation;
  private String baz;

  @ConstructorProperties({"foo", "bar", "nested", "nestedWithoutAnnotation"})
  public ConstructorConfig(String foo, @Optional String bar, NestedConfig nested, NestedWithoutAnnotation nestedWithoutAnnotation) {
    this.foo = foo;
    this.bar = bar;
    this.nested = nested;
    this.nestedWithoutAnnotation = nestedWithoutAnnotation;
  }

  public String getFoo() {
    return foo;
  }

  public String getBar() {
    return bar;
  }

  public NestedConfig getNested() {
    return nested;
  }

  public NestedWithoutAnnotation getNestedWithoutAnnotation() {
    return nestedWithoutAnnotation;
  }

  public String getBaz() {
    return baz;
  }

  public void setBaz(String baz) {
    this.baz = baz;
  }

  public static class NestedConfig {
    private final String foo;

    @ConstructorProperties({"foo"})
    public NestedConfig(String foo) {
      this.foo = foo;
    }

    public String getFoo() {
      return foo;
    }
  }

  public static class NestedWithoutAnnotation {
    private final String foo;

    public NestedWithoutAnnotation(String foo) {
      this.foo = foo;
    }

    public String getFoo() {
      return foo;
    }
  }

  public static class ConstructorConfigSkipWithoutAnnotation extends ConstructorConfig {

    @ConstructorProperties({"foo", "bar", "nested", "nestedWithoutAnnotation"})
    public ConstructorConfigSkipWithoutAnnotation(String foo, @Optional String bar, NestedConfig nested, NestedWithAnnotation nestedWithAnnotation) {
        super(foo, bar, nested, nestedWithAnnotation);
    }

  }

  public static class NestedWithAnnotation extends NestedWithoutAnnotation {

      @ConstructorProperties({"foo"})
      public NestedWithAnnotation(String foo) {
        super(foo);
      }

    }

}
