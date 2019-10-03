package beanconfig;

import java.time.DayOfWeek;
import java.util.Map;

public class InvalidEnumMapKeyConfig {

    private Map<DayOfWeek, String> enumKeyMap;

    public Map<DayOfWeek, String> getEnumKeyMap() {
        return enumKeyMap;
    }

    public void setEnumKeyMap(Map<DayOfWeek, String> enumKeyMap) {
        this.enumKeyMap = enumKeyMap;
    }
}
