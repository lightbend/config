package beanconfig;


import java.util.List;

public class DurationsConfig {
    Long second;
    List<Long> secondsList;
    Long secondAsNumber;
    Long halfSecond;


    public Long getSecond() {
        return second;
    }

    public void setSecond(Long second) {
        this.second = second;
    }

    public List<Long> getSecondsList() {
        return secondsList;
    }

    public void setSecondsList(List<Long> secondsList) {
        this.secondsList = secondsList;
    }

    public Long getSecondAsNumber() {
        return secondAsNumber;
    }

    public void setSecondAsNumber(Long secondAsNumber) {
        this.secondAsNumber = secondAsNumber;
    }

    public Long getHalfSecond() {
        return halfSecond;
    }

    public void setHalfSecond(Long halfSecond) {
        this.halfSecond = halfSecond;
    }
}
