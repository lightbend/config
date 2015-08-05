package beanconfig;

import java.util.List;
import java.time.Duration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

public class ArraysConfig {

    List<Integer> empty;
    List<Integer> ofInt;
    List<String> ofString;
    List<Double> ofDouble;
    List<Long> ofLong;
    List<Object> ofNull;
    List<Boolean> ofBoolean;
    List<Object> ofObject;
    List<Config> ofConfig;
    List<ConfigObject> ofConfigObject;
    List<ConfigValue> ofConfigValue;
    List<Duration> ofDuration;
    List<ConfigMemorySize> ofMemorySize;

    public List<Integer> getEmpty() {
        return empty;
    }

    public void setEmpty(List<Integer> empty) {
        this.empty = empty;
    }

    public List<Integer> getOfInt() {
        return ofInt;
    }

    public void setOfInt(List<Integer> ofInt) {
        this.ofInt = ofInt;
    }

    public List<String> getOfString() {
        return ofString;
    }

    public void setOfString(List<String> ofString) {
        this.ofString = ofString;
    }

    public List<Double> getOfDouble() {
        return ofDouble;
    }

    public void setOfDouble(List<Double> ofDouble) {
        this.ofDouble = ofDouble;
    }

    public List<Object> getOfNull() {
        return ofNull;
    }

    public void setOfNull(List<Object> ofNull) {
        this.ofNull = ofNull;
    }

    public List<Boolean> getOfBoolean() {
        return ofBoolean;
    }

    public void setOfBoolean(List<Boolean> ofBoolean) {
        this.ofBoolean = ofBoolean;
    }

    public List<Object> getOfObject() {
        return ofObject;
    }

    public void setOfObject(List<Object> ofObject) {
        this.ofObject = ofObject;
    }

    public List<Long> getOfLong() {
        return ofLong;
    }

    public void setOfLong(List<Long> ofLong) {
        this.ofLong = ofLong;
    }

    public List<Config> getOfConfig() {
        return ofConfig;
    }

    public void setOfConfig(List<Config> ofConfig) {
        this.ofConfig = ofConfig;
    }

    public List<ConfigObject> getOfConfigObject() {
        return ofConfigObject;
    }

    public void setOfConfigObject(List<ConfigObject> ofConfigObject) {
        this.ofConfigObject = ofConfigObject;
    }

    public List<ConfigValue> getOfConfigValue() {
        return ofConfigValue;
    }

    public void setOfConfigValue(List<ConfigValue> ofConfigValue) {
        this.ofConfigValue = ofConfigValue;
    }

    public List<Duration> getOfDuration() {
        return ofDuration;
    }

    public void setOfDuration(List<Duration> ofDuration) {
        this.ofDuration = ofDuration;
    }

    public List<ConfigMemorySize> getOfMemorySize() {
        return ofMemorySize;
    }

    public void setOfMemorySize(List<ConfigMemorySize> ofMemorySize) {
        this.ofMemorySize = ofMemorySize;
    }
}
