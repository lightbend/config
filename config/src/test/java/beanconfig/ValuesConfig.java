package beanconfig;

import java.util.Map;

import com.twitter_typesafe.config.Config;
import com.twitter_typesafe.config.ConfigList;
import com.twitter_typesafe.config.ConfigObject;
import com.twitter_typesafe.config.ConfigValue;

// test bean for various "uncooked" values
public class ValuesConfig {

    Object obj;
    Config config;
    ConfigObject configObj;
    ConfigValue configValue;
    ConfigList list;
    Map<String,Object> unwrappedMap;

    public Object getObj() {
        return obj;
    }

    public void setObj(Object obj) {
        this.obj = obj;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public ConfigObject getConfigObj() {
        return configObj;
    }

    public void setConfigObj(ConfigObject configObj) {
        this.configObj = configObj;
    }

    public ConfigValue getConfigValue() {
        return configValue;
    }

    public void setConfigValue(ConfigValue configValue) {
        this.configValue = configValue;
    }

    public ConfigList getList() {
        return list;
    }

    public void setList(ConfigList list) {
        this.list = list;
    }

    public Map<String, Object> getUnwrappedMap() {
        return unwrappedMap;
    }

    public void setUnwrappedMap(Map<String, Object> unwrappedMap) {
        this.unwrappedMap = unwrappedMap;
    }

}
