package beanconfig;

import com.typesafe.config.Optional;

public class ConstructorConfigNoAnnotation {
  private final String foo;
  private final String bar;
  private final ConstructorConfig.NestedConfig nested;
  private String baz;

  public ConstructorConfigNoAnnotation(String foo, @Optional String bar, ConstructorConfig.NestedConfig nested) {
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

  public ConstructorConfig.NestedConfig getNested() {
    return nested;
  }

  public String getBaz() {
    return baz;
  }

  public void setBaz(String baz) {
    this.baz = baz;
  }

}
