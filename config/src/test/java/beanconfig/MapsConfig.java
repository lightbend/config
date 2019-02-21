package beanconfig;

import java.util.Map;

public class MapsConfig {

    private Map<String, StringsConfig> beanMap;
    private Map<String, String> stringMap;
    private Map<String, Map<String, StringsConfig>> mapOfMaps;
    private Map<String, String> stringMapWithDots;

    public Map<String, StringsConfig> getBeanMap() {
        return beanMap;
    }

    public void setBeanMap(Map<String, StringsConfig> beanMap) {
        this.beanMap = beanMap;
    }

    public Map<String, String> getStringMap() {
        return stringMap;
    }

    public void setStringMap(Map<String, String> stringMap) {
        this.stringMap = stringMap;
    }

    public Map<String, Map<String, StringsConfig>> getMapOfMaps() {
        return mapOfMaps;
    }

    public void setMapOfMaps(Map<String, Map<String, StringsConfig>> mapOfMaps) {
        this.mapOfMaps = mapOfMaps;
    }

    public Map<String, String> getStringMapWithDots() {
        return stringMapWithDots;
    }

    public void setStringMapWithDots(Map<String, String> stringMapWithDots) {
        this.stringMapWithDots = stringMapWithDots;
    }
}
