package beanconfig;

import com.typesafe.config.Optional;

import java.beans.ConstructorProperties;

public class ConstructorConfig {
  private final String foo;
  private final String bar;
  private final NestedConfig nested;
  private String baz;

  @ConstructorProperties({"foo", "bar", "nested"})
  public ConstructorConfig(String foo, @Optional String bar, NestedConfig nested) {
    this.foo = foo;
    this.bar = bar;
    this.nested = nested;
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
}
