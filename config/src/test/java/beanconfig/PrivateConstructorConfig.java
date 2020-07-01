package beanconfig;

public class PrivateConstructorConfig {
  private final String foo;
  private final String bar;

  private PrivateConstructorConfig(String foo, String bar) {
    this.foo = foo;
    this.bar = bar;
  }

  public String getFoo() {
    return foo;
  }

  public String getBar() {
    return bar;
  }
}
