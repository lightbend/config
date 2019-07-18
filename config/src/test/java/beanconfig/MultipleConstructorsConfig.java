package beanconfig;

import java.beans.ConstructorProperties;

public class MultipleConstructorsConfig {
  private String foo;
  private String bar;
  private String baz;

  @ConstructorProperties({"foo", "bar"})
  public MultipleConstructorsConfig(String foo, String bar) {
    this.foo = foo;
    this.bar = bar;
  }

  @ConstructorProperties({"foo", "bar", "baz"})
  public MultipleConstructorsConfig(String foo, String bar, String baz) {
    this.foo = foo;
    this.bar = bar;
    this.baz = baz;
  }

  public String getFoo() {
    return foo;
  }

  public String getBar() {
    return bar;
  }

  public String getBaz() {
    return baz;
  }
}
