package beanconfig;

public class NoFoundPropBeanConfig extends TestBeanConfig{

    private String propNotListedInConfig;

    public String getPropNotListedInConfig() {
        return propNotListedInConfig;
    }

    public void setPropNotListedInConfig(String propNotListedInConfig) {
        this.propNotListedInConfig = propNotListedInConfig;
    }
}
