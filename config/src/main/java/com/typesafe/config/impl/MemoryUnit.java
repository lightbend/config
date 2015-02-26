package com.typesafe.config.impl;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * https://github.com/typesafehub/config/blob/master/HOCON.md#size-in-bytes-format
 */
public enum MemoryUnit {
    BYTES("", 1024, 0),

    KILOBYTES("kilo", 1000, 1),
    MEGABYTES("mega", 1000, 2),
    GIGABYTES("giga", 1000, 3),
    TERABYTES("tera", 1000, 4),
    PETABYTES("peta", 1000, 5),
    EXABYTES("exa", 1000, 6),
    ZETTABYTES("zetta", 1000, 7),
    YOTTABYTES("yotta", 1000, 8),

    KIBIBYTES("kibi", 1024, 1),
    MEBIBYTES("mebi", 1024, 2),
    GIBIBYTES("gibi", 1024, 3),
    TEBIBYTES("tebi", 1024, 4),
    PEBIBYTES("pebi", 1024, 5),
    EXBIBYTES("exbi", 1024, 6),
    ZEBIBYTES("zebi", 1024, 7),
    YOBIBYTES("yobi", 1024, 8);

    final String prefix;
    final int powerOf;
    final int power;
    final BigInteger bytes;

    MemoryUnit(String prefix, int powerOf, int power) {
        this.prefix = prefix;
        this.powerOf = powerOf;
        this.power = power;
        this.bytes = BigInteger.valueOf(powerOf).pow(power);
    }

    private static Map<String, MemoryUnit> makeUnitsMap() {
        Map<String, MemoryUnit> map = new HashMap<String, MemoryUnit>();
        for (MemoryUnit unit : MemoryUnit.values()) {
            map.put(unit.prefix + "byte", unit);
            map.put(unit.prefix + "bytes", unit);
            if (unit.prefix.length() == 0) {
                map.put("b", unit);
                map.put("B", unit);
                map.put("", unit); // no unit specified means bytes
            } else {
                String first = unit.prefix.substring(0, 1);
                String firstUpper = first.toUpperCase();
                if (unit.powerOf == 1024) {
                    map.put(first, unit);             // 512m
                    map.put(firstUpper, unit);        // 512M
                    map.put(firstUpper + "i", unit);  // 512Mi
                    map.put(firstUpper + "iB", unit); // 512MiB
                } else if (unit.powerOf == 1000) {
                    if (unit.power == 1) {
                        map.put(first + "B", unit);      // 512kB
                    } else {
                        map.put(firstUpper + "B", unit); // 512MB
                    }
                } else {
                    throw new RuntimeException("broken MemoryUnit enum");
                }
            }
        }
        return map;
    }

    private static Map<String, MemoryUnit> unitsMap = makeUnitsMap();

    static MemoryUnit parseUnit(String unit) {
        return unitsMap.get(unit);
    }

    /**
     * Checks whether given string contains memory  token.
     * @param rawVal - string like '10B','45bytes' etc.
     *               String is case sensitive, i.e. '10KILOS' is not treated as valid memory size string
     * @return true - if string contains memory data, false otherwise
     */
    public static boolean containsMemoryToken(String rawVal) {
        String unitStr = rawVal.replaceAll("[^A-Za-z]","");
        for (String unit : unitsMap.keySet()) {
            if(unit.equals(unitStr)) {
                return true;
            }
        }
        return false;
    }
}
