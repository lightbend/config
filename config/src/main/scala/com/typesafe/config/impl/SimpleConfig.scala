/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import java.io.ObjectStreamException
import java.io.Serializable
import java.{ lang => jl }
import java.math.BigDecimal
import java.math.BigInteger
import java.time.DateTimeException
import java.time.Duration
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.{ util => ju }
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._
import scala.util.control.Breaks._
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType
import com.typesafe.config.impl.SimpleConfig.MemoryUnit

/**
 * One thing to keep in mind in the future: as Collection-like APIs are added
 * here, including iterators or size() or anything, they should be consistent
 * with a one-level java.util.Map from paths to non-null values. Null values are
 * not "in" the map.
 */
@SerialVersionUID(1L)
object SimpleConfig {
    private def findPaths(entries: ju.Set[ju.Map.Entry[String, ConfigValue]], parent: Path, obj: AbstractConfigObject): Unit = {
        for (entry <- obj.entrySet.asScala) {
            val elem = entry.getKey
            val v = entry.getValue
            var path = Path.newKey(elem)
            if (parent != null) path = path.prepend(parent)
            if (v.isInstanceOf[AbstractConfigObject])
                findPaths(entries, path, v.asInstanceOf[AbstractConfigObject])
            else if (v.isInstanceOf[ConfigNull]) {
                // nothing; nulls are conceptually not in a Config
            } else entries.add(new ju.AbstractMap.SimpleImmutableEntry[String, ConfigValue](path.render, v))
        }
    }
    private def throwIfNull(v: AbstractConfigValue, expected: ConfigValueType, originalPath: Path): AbstractConfigValue =
        if (v.valueType eq ConfigValueType.NULL)
            throw new ConfigException.Null(v.origin,
                originalPath.render,
                if (expected != null) expected.name else null)
        else v
    private def findKey(
        self: AbstractConfigObject,
        key: String,
        expected: ConfigValueType,
        originalPath: Path): AbstractConfigValue =
        throwIfNull(findKeyOrNull(self, key, expected, originalPath), expected, originalPath)

    private def findKeyOrNull(
        self: AbstractConfigObject,
        key: String,
        expected: ConfigValueType,
        originalPath: Path): AbstractConfigValue = {
        var v = self.peekAssumingResolved(key, originalPath)
        if (v == null)
            throw new ConfigException.Missing(self.origin, originalPath.render)
        if (expected != null) v = DefaultTransformer.transform(v, expected)
        if (expected != null && ((v.valueType ne expected) && (v.valueType ne ConfigValueType.NULL)))
            throw new ConfigException.WrongType(v.origin, originalPath.render, expected.name, v.valueType.name)
        else v
    }

    private def findOrNull(self: AbstractConfigObject, path: Path,
        expected: ConfigValueType, originalPath: Path): AbstractConfigValue =
        try {
            val key = path.first
            val next = path.remainder
            if (next == null) findKeyOrNull(self, key, expected, originalPath)
            else {
                val o = findKey(self, key, ConfigValueType.OBJECT,
                    originalPath.subPath(0, originalPath.length - next.length)).asInstanceOf[AbstractConfigObject]
                assert(o != null) // missing was supposed to throw
                findOrNull(o, next, expected, originalPath)
            }
        } catch {
            case e: ConfigException.NotResolved =>
                throw ConfigImpl.improveNotResolved(path, e)
        }
    private def getUnits(s: String): String = {
        var i = s.length - 1
        breakable {
            while (i >= 0) {
                val c = s.charAt(i)
                if (!Character.isLetter(c)) break // break
                i -= 1
            }
        }
        return s.substring(i + 1)
    }
    /**
     * Parses a period string. If no units are specified in the string, it is
     * assumed to be in days. The returned period is in days.
     * The purpose of this function is to implement the period-related methods
     * in the ConfigObject interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return duration in days
     * @throws ConfigException
     *             if string is invalid
     */
    def parsePeriod(input: String, originForException: ConfigOrigin, pathForException: String) = {
        val s = ConfigImplUtil.unicodeTrim(input)
        val originalUnitString = getUnits(s)
        var unitString = originalUnitString
        val numberString = ConfigImplUtil.unicodeTrim(s.substring(0, s.length - unitString.length))
        var units: ChronoUnit = null
        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length == 0)
            throw new ConfigException.BadValue(originForException, pathForException, "No number in period value '" + input + "'")
        if (unitString.length > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s"
        // note that this is deliberately case-sensitive
        if (unitString == "" || unitString == "d" || unitString == "days")
            units = ChronoUnit.DAYS
        else if (unitString == "w" || unitString == "weeks") units = ChronoUnit.WEEKS
        else if (unitString == "m" || unitString == "mo" || unitString == "months") units = ChronoUnit.MONTHS
        else if (unitString == "y" || unitString == "years") units = ChronoUnit.YEARS
        else throw new ConfigException.BadValue(originForException, pathForException, "Could not parse time unit '" + originalUnitString + "' (try d, w, mo, y)")
        try periodOf(numberString.toInt, units)
        catch {
            case e: NumberFormatException =>
                throw new ConfigException.BadValue(originForException, pathForException, "Could not parse duration number '" + numberString + "'")
        }
    }
    private def periodOf(n: Int, unit: ChronoUnit): Period = {
        if (unit.isTimeBased) throw new DateTimeException(unit + " cannot be converted to a java.time.Period")
        unit match {
            case ChronoUnit.DAYS =>
                return Period.ofDays(n)
            case ChronoUnit.WEEKS =>
                return Period.ofWeeks(n)
            case ChronoUnit.MONTHS =>
                return Period.ofMonths(n)
            case ChronoUnit.YEARS =>
                return Period.ofYears(n)
            case _ =>
                throw new DateTimeException(unit + " cannot be converted to a java.time.Period")
        }
    }
    /**
     * Parses a duration string. If no units are specified in the string, it is
     * assumed to be in milliseconds. The returned duration is in nanoseconds.
     * The purpose of this function is to implement the duration-related methods
     * in the ConfigObject interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return duration in nanoseconds
     * @throws ConfigException
     *             if string is invalid
     */
    def parseDuration(input: String, originForException: ConfigOrigin, pathForException: String): Long = {
        val s = ConfigImplUtil.unicodeTrim(input)
        val originalUnitString = getUnits(s)
        var unitString = originalUnitString
        val numberString =
            ConfigImplUtil.unicodeTrim(s.substring(0, s.length - unitString.length))
        var units: TimeUnit = null

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length == 0)
            throw new ConfigException.BadValue(
                originForException,
                pathForException,
                "No number in duration value '" + input + "'")
        if (unitString.length > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s"

        // note that this is deliberately case-sensitive
        if (unitString == "" || unitString == "ms" || unitString == "millis" || unitString == "milliseconds")
            units = TimeUnit.MILLISECONDS
        else if (unitString == "us" || unitString == "micros" || unitString == "microseconds")
            units = TimeUnit.MICROSECONDS
        else if (unitString == "ns" || unitString == "nanos" || unitString == "nanoseconds")
            units = TimeUnit.NANOSECONDS
        else if (unitString == "d" || unitString == "days") units = TimeUnit.DAYS
        else if (unitString == "h" || unitString == "hours") units = TimeUnit.HOURS
        else if (unitString == "s" || unitString == "seconds") units = TimeUnit.SECONDS
        else if (unitString == "m" || unitString == "minutes") units = TimeUnit.MINUTES
        else {
            throw new ConfigException.BadValue(
                originForException,
                pathForException,
                "Could not parse time unit '" + originalUnitString + "' (try ns, us, ms, s, m, h, d)")
        }
        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[+-]?[0-9]+")) {
                units.toNanos(jl.Long.parseLong(numberString))
            } else {
                val nanosInUnit = units.toNanos(1)
                (numberString.toDouble * nanosInUnit).toLong
            }
        } catch {
            case e: NumberFormatException =>
                throw new ConfigException.BadValue(originForException, pathForException, "Could not parse duration number '" + numberString + "'")
        }
    }

    /**
     * Parses a size-in-bytes string. If no units are specified in the string,
     * it is assumed to be in bytes. The returned value is in bytes. The purpose
     * of this function is to implement the size-in-bytes-related methods in the
     * Config interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return size in bytes
     * @throws ConfigException
     *             if string is invalid
     */
    def parseBytes(input: String, originForException: ConfigOrigin, pathForException: String): Long = {
        val s = ConfigImplUtil.unicodeTrim(input)
        val unitString = getUnits(s)
        val numberString = ConfigImplUtil.unicodeTrim(s.substring(0, s.length - unitString.length))
        if (numberString.length == 0) throw new ConfigException.BadValue(originForException, pathForException, "No number in size-in-bytes value '" + input + "'")
        val units = MemoryUnit.parseUnit(unitString)
        if (units == null) throw new ConfigException.BadValue(originForException, pathForException, "Could not parse size-in-bytes unit '" + unitString + "' (try k, K, kB, KiB, kilobytes, kibibytes)")
        try {
            var result: BigInteger = null
            // possible precision loss; otherwise as a double.
            if (numberString.matches("[0-9]+")) result = units.bytes.multiply(new BigInteger(numberString))
            else {
                val resultDecimal = new BigDecimal(units.bytes).multiply(new BigDecimal(numberString))
                result = resultDecimal.toBigInteger
            }
            if (result.bitLength < 64) result.longValue
            else throw new ConfigException.BadValue(originForException, pathForException, "size-in-bytes value is out of range for a 64-bit long: '" + input + "'")
        } catch {
            case e: NumberFormatException =>
                throw new ConfigException.BadValue(originForException, pathForException, "Could not parse size-in-bytes number '" + numberString + "'")
        }
    }
    private def addProblem(accumulator: ju.List[ConfigException.ValidationProblem], path: Path, origin: ConfigOrigin, problem: String): Unit = {
        accumulator.add(new ConfigException.ValidationProblem(path.render, origin, problem))
    }
    private def getDesc(`type`: ConfigValueType): String = {
        return `type`.name.toLowerCase
    }
    private def getDesc(refValue: ConfigValue): String = {
        if (refValue.isInstanceOf[AbstractConfigObject]) {
            val obj = refValue.asInstanceOf[AbstractConfigObject]
            if (!obj.isEmpty) return "object with keys " + obj.keySet
            else return getDesc(refValue.valueType)
        } else return getDesc(refValue.valueType)
    }
    private def addMissing(accumulator: ju.List[ConfigException.ValidationProblem], refDesc: String, path: Path, origin: ConfigOrigin): Unit = {
        addProblem(accumulator, path, origin, "No setting at '" + path.render + "', expecting: " + refDesc)
    }
    private def addMissing(accumulator: ju.List[ConfigException.ValidationProblem], refValue: ConfigValue, path: Path, origin: ConfigOrigin): Unit = {
        addMissing(accumulator, getDesc(refValue), path, origin)
    }
    // JavaBean stuff uses this
    private[impl] def addMissing(accumulator: ju.List[ConfigException.ValidationProblem], refType: ConfigValueType, path: Path, origin: ConfigOrigin): Unit = {
        addMissing(accumulator, getDesc(refType), path, origin)
    }
    private def addWrongType(accumulator: ju.List[ConfigException.ValidationProblem], refDesc: String, actual: AbstractConfigValue, path: Path): Unit = {
        addProblem(accumulator, path, actual.origin, "Wrong value type at '" + path.render + "', expecting: " + refDesc + " but got: " + getDesc(actual))
    }
    private def addWrongType(accumulator: ju.List[ConfigException.ValidationProblem], refValue: ConfigValue, actual: AbstractConfigValue, path: Path): Unit = {
        addWrongType(accumulator, getDesc(refValue), actual, path)
    }
    private def addWrongType(accumulator: ju.List[ConfigException.ValidationProblem], refType: ConfigValueType, actual: AbstractConfigValue, path: Path): Unit = {
        addWrongType(accumulator, getDesc(refType), actual, path)
    }
    private def couldBeNull(v: AbstractConfigValue): Boolean = {
        return DefaultTransformer.transform(v, ConfigValueType.NULL).valueType eq ConfigValueType.NULL
    }
    private def haveCompatibleTypes(reference: ConfigValue, value: AbstractConfigValue): Boolean = {
        if (couldBeNull(reference.asInstanceOf[AbstractConfigValue])) {
            // we allow any setting to be null
            return true
        } else return haveCompatibleTypes(reference.valueType, value)
    }
    private def haveCompatibleTypes(referenceType: ConfigValueType, value: AbstractConfigValue): Boolean = {
        if ((referenceType eq ConfigValueType.NULL) || couldBeNull(value)) return true
        else if (referenceType eq ConfigValueType.OBJECT) if (value.isInstanceOf[AbstractConfigObject]) return true
        else return false
        else if (referenceType eq ConfigValueType.LIST) { // objects may be convertible to lists if they have numeric keys
            if (value.isInstanceOf[SimpleConfigList] || value.isInstanceOf[SimpleConfigObject]) return true
            else return false
        } else if (referenceType eq ConfigValueType.STRING) { // assume a string could be gotten as any non-collection type;
            // allows things like getMilliseconds including domain-specific
            // interpretations of strings
            return true
        } else if (value.isInstanceOf[ConfigString]) { // assume a string could be gotten as any non-collection type
            return true
        } else if (referenceType eq value.valueType) return true
        else return false
    }
    // path is null if we're at the root
    private def checkValidObject(path: Path, reference: AbstractConfigObject, value: AbstractConfigObject, accumulator: ju.List[ConfigException.ValidationProblem]): Unit = {
        for (entry <- reference.entrySet.asScala) {
            val key = entry.getKey
            val childPath: Path = if (path != null) Path.newKey(key).prepend(path) else Path.newKey(key)
            val v = value.get(key)
            if (v == null) addMissing(accumulator, entry.getValue, childPath, value.origin)
            else checkValid(childPath, entry.getValue, v, accumulator)
        }
    }
    private def checkListCompatibility(path: Path, listRef: SimpleConfigList, listValue: SimpleConfigList, accumulator: ju.List[ConfigException.ValidationProblem]): Unit = {
        if (listRef.isEmpty || listValue.isEmpty) {
            // can't verify type, leave alone
        } else {
            val refElement = listRef.get(0)
            breakable {
                for (elem <- listValue.asScala) {
                    val e = elem.asInstanceOf[AbstractConfigValue]
                    if (!haveCompatibleTypes(refElement, e)) {
                        addProblem(
                            accumulator,
                            path,
                            e.origin,
                            "List at '" + path.render + "' contains wrong value type, expecting list of " + getDesc(
                                refElement) + " but got element of type " + getDesc(e))
                        // don't add a problem for every last array element
                        break // break
                    }
                }
            }
        }
    }
    // Used by the JavaBean-based validator
    private[impl] def checkValid(path: Path, referenceType: ConfigValueType, value: AbstractConfigValue,
        accumulator: ju.List[ConfigException.ValidationProblem]): Unit = {
        if (haveCompatibleTypes(referenceType, value)) {
            if ((referenceType eq ConfigValueType.LIST) && value.isInstanceOf[SimpleConfigObject]) {
                // attempt conversion of indexed object to list
                val listValue = DefaultTransformer.transform(value, ConfigValueType.LIST)
                if (!listValue.isInstanceOf[SimpleConfigList])
                    addWrongType(accumulator, referenceType, value, path)
            }
        } else {
            addWrongType(accumulator, referenceType, value, path)
        }
    }

    private def checkValid(path: Path, reference: ConfigValue, value: AbstractConfigValue,
        accumulator: ju.List[ConfigException.ValidationProblem]): Unit = {
        // Unmergeable is supposed to be impossible to encounter in here
        // because we check for resolve status up front.
        if (haveCompatibleTypes(reference, value)) {
            if (reference.isInstanceOf[AbstractConfigObject] && value
                .isInstanceOf[AbstractConfigObject]) {
                checkValidObject(
                    path,
                    reference.asInstanceOf[AbstractConfigObject],
                    value.asInstanceOf[AbstractConfigObject],
                    accumulator)
            } else if (reference.isInstanceOf[SimpleConfigList] && value
                .isInstanceOf[SimpleConfigList]) {
                val listRef = reference.asInstanceOf[SimpleConfigList]
                val listValue = value.asInstanceOf[SimpleConfigList]
                checkListCompatibility(path, listRef, listValue, accumulator)
            } else if (reference.isInstanceOf[SimpleConfigList] && value
                .isInstanceOf[SimpleConfigObject]) {
                val listRef = reference.asInstanceOf[SimpleConfigList]
                val listValue =
                    DefaultTransformer.transform(value, ConfigValueType.LIST)
                if (listValue.isInstanceOf[SimpleConfigList])
                    checkListCompatibility(
                        path,
                        listRef,
                        listValue.asInstanceOf[SimpleConfigList],
                        accumulator)
                else
                    addWrongType(accumulator, reference, value, path)
            }
        } else {
            addWrongType(accumulator, reference, value, path)
        }
    }

    private final class MemoryUnit private (name: String, ordinal: Int,
        val prefix: String, val powerOf: Int, val power: Int)
        extends Enum[MemoryUnit](name, ordinal) {
        val bytes = BigInteger.valueOf(powerOf).pow(power)

    }

    private object MemoryUnit {

        final val BYTES = new MemoryUnit("BYTES", 0, "", 1024, 0)
        final val KILOBYTES = new MemoryUnit("KILOBYTES", 1, "kilo", 1000, 1)
        final val MEGABYTES = new MemoryUnit("MEGABYTES", 2, "mega", 1000, 2)
        final val GIGABYTES = new MemoryUnit("GIGABYTES", 3, "giga", 1000, 3)
        final val TERABYTES = new MemoryUnit("TERABYTES", 4, "tera", 1000, 4)
        final val PETABYTES = new MemoryUnit("PETABYTES", 5, "peta", 1000, 5)
        final val EXABYTES = new MemoryUnit("EXABYTES", 6, "exa", 1000, 6)
        final val ZETTABYTES = new MemoryUnit("ZETTABYTES", 7, "zetta", 1000, 7)
        final val YOTTABYTES = new MemoryUnit("YOTTABYTES", 8, "yotta", 1000, 8)

        final val KIBIBYTES = new MemoryUnit("KIBIBYTES", 9, "kibi", 1024, 1)
        final val MEBIBYTES = new MemoryUnit("MEBIBYTES", 10, "mebi", 1024, 2)
        final val GIBIBYTES = new MemoryUnit("GIBIBYTES", 11, "gibi", 1024, 3)
        final val TEBIBYTES = new MemoryUnit("TEBIBYTES", 12, "tebi", 1024, 4)
        final val PEBIBYTES = new MemoryUnit("PEBIBYTES", 13, "pebi", 1024, 5)
        final val EXBIBYTES = new MemoryUnit("EXBIBYTES", 14, "exbi", 1024, 6)
        final val ZEBIBYTES = new MemoryUnit("ZEBIBYTES", 15, "zebi", 1024, 7)
        final val OBIBYTES = new MemoryUnit("OBIBYTES", 16, "yobi", 1024, 8)

        private[this] val _values: Array[MemoryUnit] =
            Array(BYTES, KILOBYTES, MEGABYTES, GIGABYTES, TERABYTES, PETABYTES, EXABYTES, ZETTABYTES, YOTTABYTES,
                KIBIBYTES, MEBIBYTES, GIBIBYTES, TEBIBYTES, PEBIBYTES, EXBIBYTES, ZEBIBYTES, OBIBYTES)

        def values(): Array[MemoryUnit] = _values.clone()

        def valueOf(name: String): MemoryUnit = {
            _values.find(_.name == name).getOrElse {
                throw new IllegalArgumentException("No enum const MemoryUnit." + name)
            }
        }

        lazy val unitsMap: ju.Map[String, MemoryUnit] = {
            val map = new ju.HashMap[String, MemoryUnit]
            for (unit <- MemoryUnit.values) {
                map.put(unit.prefix + "byte", unit)
                map.put(unit.prefix + "bytes", unit)
                if (unit.prefix.length == 0) {
                    map.put("b", unit)
                    map.put("B", unit)
                    map.put("", unit) // no unit specified means bytes
                } else {
                    val first = unit.prefix.substring(0, 1)
                    val firstUpper = first.toUpperCase
                    if (unit.powerOf == 1024) {
                        map.put(first, unit) // 512m
                        map.put(firstUpper, unit) // 512M
                        map.put(firstUpper + "i", unit) // 512Mi
                        map.put(firstUpper + "iB", unit) // 512MiB
                    } else if (unit.powerOf == 1000) {
                        if (unit.power == 1) map.put(first + "B", unit) // 512kB
                        else map.put(firstUpper + "B", unit) // 512MB
                    } else throw new RuntimeException("broken MemoryUnit enum")
                }
            }
            map
        }
        private[impl] def parseUnit(unit: String): MemoryUnit = unitsMap.get(unit)
    }

}

@SerialVersionUID(1L)
final class SimpleConfig private[impl] (val `object`: AbstractConfigObject)
    extends Config with MergeableValue with Serializable {
    override def root: AbstractConfigObject = `object`
    override def origin: ConfigOrigin = `object`.origin
    override def resolve: SimpleConfig = resolve(ConfigResolveOptions.defaults)
    override def resolve(options: ConfigResolveOptions): SimpleConfig = resolveWith(this, options)
    override def resolveWith(source: Config): SimpleConfig = resolveWith(source, ConfigResolveOptions.defaults)
    override def resolveWith(source: Config, options: ConfigResolveOptions): SimpleConfig = {
        val resolved = ResolveContext.resolve(`object`, source.asInstanceOf[SimpleConfig].`object`, options)
        if (resolved eq `object`) this
        else new SimpleConfig(resolved.asInstanceOf[AbstractConfigObject])
    }
    private def hasPathPeek(pathExpression: String) = {
        val path = Path.newPath(pathExpression)
        var peeked: AbstractConfigValue = null
        try peeked = `object`.peekPath(path)
        catch {
            case e: ConfigException.NotResolved =>
                throw ConfigImpl.improveNotResolved(path, e)
        }
        peeked
    }
    override def hasPath(pathExpression: String): Boolean = {
        val peeked = hasPathPeek(pathExpression)
        peeked != null && (peeked.valueType ne ConfigValueType.NULL)
    }
    override def hasPathOrNull(path: String): Boolean = {
        val peeked = hasPathPeek(path)
        peeked != null
    }
    override def isEmpty: Boolean = `object`.isEmpty
    override def entrySet: ju.Set[ju.Map.Entry[String, ConfigValue]] = {
        val entries = new ju.HashSet[ju.Map.Entry[String, ConfigValue]]
        SimpleConfig.findPaths(entries, null, `object`)
        entries
    }
    private[impl] def find(pathExpression: Path, expected: ConfigValueType, originalPath: Path): AbstractConfigValue = SimpleConfig.throwIfNull(SimpleConfig.findOrNull(`object`, pathExpression, expected, originalPath), expected, originalPath)
    private[impl] def find(pathExpression: String, expected: ConfigValueType): AbstractConfigValue = {
        val path = Path.newPath(pathExpression)
        find(path, expected, path)
    }
    private def findOrNull(pathExpression: Path, expected: ConfigValueType, originalPath: Path): AbstractConfigValue = SimpleConfig.findOrNull(`object`, pathExpression, expected, originalPath)
    private def findOrNull(pathExpression: String, expected: ConfigValueType): AbstractConfigValue = {
        val path = Path.newPath(pathExpression)
        findOrNull(path, expected, path)
    }
    override def getValue(path: String): AbstractConfigValue = find(path, null)
    override def getIsNull(path: String): Boolean = {
        val v = findOrNull(path, null)
        v.valueType eq ConfigValueType.NULL
    }
    override def getBoolean(path: String): Boolean = {
        val v = find(path, ConfigValueType.BOOLEAN)
        v.unwrapped.asInstanceOf[Boolean]
    }
    private def getConfigNumber(path: String): ConfigNumber = {
        val v = find(path, ConfigValueType.NUMBER)
        v.asInstanceOf[ConfigNumber]
    }
    override def getNumber(path: String): Number = getConfigNumber(path).unwrapped
    override def getInt(path: String): Int = {
        val n = getConfigNumber(path)
        n.intValueRangeChecked(path)
    }
    override def getLong(path: String): Long = getNumber(path).longValue
    override def getDouble(path: String): Double = getNumber(path).doubleValue
    override def getString(path: String): String = {
        val v = find(path, ConfigValueType.STRING)
        v.unwrapped.asInstanceOf[String]
    }
    def getEnum[T <: Enum[T]](enumClass: Class[T], path: String): T = {
        val v = find(path, ConfigValueType.STRING)
        getEnumValue(path, enumClass, v)
    }
    override def getList(path: String): ConfigList = {
        val v = find(path, ConfigValueType.LIST)
        v.asInstanceOf[ConfigList]
    }
    override def getObject(path: String): AbstractConfigObject = {
        val obj = find(path, ConfigValueType.OBJECT).asInstanceOf[AbstractConfigObject]
        obj
    }
    override def getConfig(path: String): SimpleConfig = getObject(path).toConfig
    override def getAnyRef(path: String): Any = {
        val v = find(path, null)
        v.unwrapped
    }
    override def getBytes(path: String): jl.Long = {
        var size: jl.Long = null
        try size = getLong(path)
        catch {
            case e: ConfigException.WrongType =>
                val v = find(path, ConfigValueType.STRING)
                size = SimpleConfig.parseBytes(v.unwrapped.asInstanceOf[String], v.origin, path)
        }
        size
    }
    override def getMemorySize(path: String): ConfigMemorySize = ConfigMemorySize.ofBytes(getBytes(path))
    @deprecated("", "") override def getMilliseconds(path: String): jl.Long = getDuration(path, TimeUnit.MILLISECONDS)
    @deprecated("", "") override def getNanoseconds(path: String): jl.Long = getDuration(path, TimeUnit.NANOSECONDS)
    override def getDuration(path: String, unit: TimeUnit): Long = {
        val v = find(path, ConfigValueType.STRING)
        val result = unit.convert(SimpleConfig.parseDuration(v.unwrapped.asInstanceOf[String], v.origin, path), TimeUnit.NANOSECONDS)
        result
    }
    override def getDuration(path: String): Duration = {
        val v = find(path, ConfigValueType.STRING)
        val nanos = SimpleConfig.parseDuration(v.unwrapped.asInstanceOf[String], v.origin, path)
        Duration.ofNanos(nanos)
    }
    override def getPeriod(path: String): Period = {
        val v = find(path, ConfigValueType.STRING)
        SimpleConfig.parsePeriod(v.unwrapped.asInstanceOf[String], v.origin, path)
    }
    override def getTemporal(path: String): TemporalAmount = try getDuration(path)
    catch {
        case e: ConfigException.BadValue =>
            getPeriod(path)
    }
    @SuppressWarnings(Array("unchecked"))
    private def getHomogeneousUnwrappedList[T](path: String, expected: ConfigValueType): ju.List[T] = {
        val l = new ju.ArrayList[T]
        val list = getList(path)
        for (cv <- list.asScala) {
            // variance would be nice, but stupid cast will do
            var v = cv.asInstanceOf[AbstractConfigValue]
            if (expected != null)
                v = DefaultTransformer.transform(v, expected)
            if (v.valueType ne expected)
                throw new ConfigException.WrongType(v.origin, path, "list of " + expected.name,
                    "list of " + v.valueType.name)
            l.add(v.unwrapped.asInstanceOf[T])
        }
        l
    }
    override def getBooleanList(path: String): ju.List[jl.Boolean] = getHomogeneousUnwrappedList(path, ConfigValueType.BOOLEAN)
    override def getNumberList(path: String): ju.List[jl.Number] = getHomogeneousUnwrappedList(path, ConfigValueType.NUMBER)
    override def getIntList(path: String): ju.List[jl.Integer] = {
        val l = new ju.ArrayList[Integer]
        val numbers = getHomogeneousWrappedList(path, ConfigValueType.NUMBER).asInstanceOf[ju.List[ConfigNumber]]
        for (v <- numbers.asScala) {
            l.add(v.asInstanceOf[ConfigNumber].intValueRangeChecked(path))
        }
        l
    }
    override def getLongList(path: String): ju.List[jl.Long] = {
        val l = new ju.ArrayList[jl.Long]
        val numbers = getNumberList(path)
        for (n <- numbers.asScala) {
            l.add(n.longValue)
        }
        l
    }
    override def getDoubleList(path: String): ju.List[jl.Double] = {
        val l = new ju.ArrayList[jl.Double]
        val numbers = getNumberList(path)
        for (n <- numbers.asScala) {
            l.add(n.doubleValue)
        }
        l
    }
    override def getStringList(path: String): ju.List[String] = getHomogeneousUnwrappedList(path, ConfigValueType.STRING)

    def getEnumList[T <: Enum[T]](enumClass: Class[T], path: String): ju.List[T] = {
        val enumNames = getHomogeneousWrappedList(path, ConfigValueType.STRING).asInstanceOf[ju.List[ConfigString]]
        val enumList = new ju.ArrayList[T]

        for (enumName <- enumNames.asScala) {
            enumList.add(getEnumValue(path, enumClass, enumName))
        }
        enumList
    }

    private def getEnumValue[T <: Enum[T]](path: String, enumClass: Class[T], enumConfigValue: ConfigValue) = {
        val enumName = enumConfigValue.unwrapped.asInstanceOf[String]
        try Enum.valueOf(enumClass, enumName)
        catch {
            case e: IllegalArgumentException =>
                val enumNames = new ju.ArrayList[String]
                val enumConstants = enumClass.getEnumConstants
                if (enumConstants != null) for (enumConstant <- enumConstants) {
                    enumNames.add(enumConstant.name)
                }
                throw new ConfigException.BadValue(enumConfigValue.origin, path,
                    String.format("The enum class %s has no constant of the name '%s' (should be one of %s.)", enumClass.getSimpleName, enumName, enumNames))
        }
    }

    private def getHomogeneousWrappedList[T <: ConfigValue](path: String, expected: ConfigValueType): ju.List[T] = {
        val l = new ju.ArrayList[T]
        val list = getList(path)
        for (cv <- list.asScala) {
            var v = cv.asInstanceOf[AbstractConfigValue]
            if (expected != null) v = DefaultTransformer.transform(v, expected)
            if (v.valueType ne expected) throw new ConfigException.WrongType(v.origin, path, "list of " + expected.name, "list of " + v.valueType.name)
            l.add(v.asInstanceOf[T])
        }
        l
    }
    override def getObjectList(path: String): ju.List[ConfigObject] = getHomogeneousWrappedList(path, ConfigValueType.OBJECT)
    override def getConfigList(path: String): ju.List[_ <: Config] = {
        val objects = getObjectList(path)
        val l = new ju.ArrayList[Config]
        for (o <- objects.asScala) {
            l.add(o.toConfig)
        }
        l
    }
    override def getAnyRefList(path: String): ju.List[_] = {
        val l = new ju.ArrayList[AnyRef]
        val list = getList(path)
        for (v <- list.asScala) {
            l.add(v.unwrapped)
        }
        l
    }
    override def getBytesList(path: String): ju.List[jl.Long] = {
        val l = new ju.ArrayList[jl.Long]
        val list = getList(path)
        for (v <- list.asScala) {
            if (v.valueType eq ConfigValueType.NUMBER) {
                l.add(v.unwrapped.asInstanceOf[Number].longValue)
            } else if (v.valueType eq ConfigValueType.STRING) {
                val s = v.unwrapped.asInstanceOf[String]
                val n = SimpleConfig.parseBytes(s, v.origin, path)
                l.add(n)
            } else {
                throw new ConfigException.WrongType(v.origin,
                    path,
                    "memory size string or number of bytes",
                    v.valueType.name)
            }
        }
        l
    }
    override def getMemorySizeList(path: String): ju.List[ConfigMemorySize] = {
        val list = getBytesList(path)
        val builder = new ju.ArrayList[ConfigMemorySize]
        for (v <- list.asScala) {
            builder.add(ConfigMemorySize.ofBytes(v))
        }
        builder
    }
    override def getDurationList(path: String, unit: TimeUnit): ju.List[jl.Long] = {
        val l = new ju.ArrayList[jl.Long]
        val list = getList(path)
        for (v <- list.asScala) {
            if (v.valueType eq ConfigValueType.NUMBER) {
                val n = unit.convert(v.unwrapped.asInstanceOf[Number].longValue, TimeUnit.MILLISECONDS)
                l.add(n)
            } else if (v.valueType eq ConfigValueType.STRING) {
                val s = v.unwrapped.asInstanceOf[String]
                val n = unit.convert(SimpleConfig.parseDuration(s, v.origin, path), TimeUnit.NANOSECONDS)
                l.add(n)
            } else {
                throw new ConfigException.WrongType(v.origin,
                    path,
                    "duration string or number of milliseconds",
                    v.valueType.name)
            }
        }
        l
    }
    override def getDurationList(path: String): ju.List[Duration] = {
        val l = getDurationList(path, TimeUnit.NANOSECONDS)
        val builder = new ju.ArrayList[Duration](l.size)
        for (value <- l.asScala) {
            builder.add(Duration.ofNanos(value))
        }
        builder
    }
    @deprecated("", "") override def getMillisecondsList(path: String): ju.List[jl.Long] = getDurationList(path, TimeUnit.MILLISECONDS)
    @deprecated("", "") override def getNanosecondsList(path: String): ju.List[jl.Long] = getDurationList(path, TimeUnit.NANOSECONDS)
    override def toFallbackValue: AbstractConfigObject = `object`
    override def withFallback(other: ConfigMergeable): SimpleConfig = { // this can return "this" if the withFallback doesn't need a new
        // ConfigObject
        `object`.withFallback(other).toConfig
    }
    override final def equals(other: Any): Boolean =
        if (other.isInstanceOf[SimpleConfig]) `object` == other.asInstanceOf[SimpleConfig].`object` else false

    override final def hashCode: Int = { // we do the "41*" just so our hash code won't match that of the
        // underlying object. there's no real reason it can't match, but
        // making it not match might catch some kinds of bug.
        41 * `object`.hashCode
    }
    override def toString: String = "Config(" + `object`.toString + ")"
    private def peekPath(path: Path): AbstractConfigValue = root.peekPath(path)
    override def isResolved: Boolean = root.resolveStatus eq ResolveStatus.RESOLVED
    // This method in Config uses @varargs so it can be called from
    // Java with varargs
    // See https://github.com/scala/bug/issues/10658
    // originally: @Override public void checkValid(Config reference, String... restrictToPaths)
    // Now the code goes through the Scala varargs method and this one is not needed.
    //    override def checkValid(reference: Config, restrictToPaths: Seq[String]): Unit = {
    //        val javaListPaths = JavaConverters.asJavaListConverter(restrictToPaths).asJava
    //        val javaArrayPaths = javaListPaths.toArray(new Array[String](0))
    //        checkValid(reference, restrictToPaths: _*)
    //    }
    // Comment can be removed but here for info purposes
    override def checkValid(reference: Config, restrictToPaths: String*): Unit = {
        val ref = reference.asInstanceOf[SimpleConfig]
        // unresolved reference config is a bug in the caller of checkValid
        if (ref.root.resolveStatus ne ResolveStatus.RESOLVED) throw new ConfigException.BugOrBroken("do not call checkValid() with an unresolved reference config, call Config#resolve(), see Config#resolve() API docs")
        // unresolved config under validation is a bug in something,
        // NotResolved is a more specific subclass of BugOrBroken
        if (root.resolveStatus ne ResolveStatus.RESOLVED) throw new ConfigException.NotResolved("need to Config#resolve() each config before using it, see the API docs for Config#resolve()")
        // Now we know that both reference and this config are resolved
        val problems = new ju.ArrayList[ConfigException.ValidationProblem]
        if (restrictToPaths.length == 0) SimpleConfig.checkValidObject(null, ref.root, root, problems)
        else for (p <- restrictToPaths) {
            val path = Path.newPath(p)
            val refValue = ref.peekPath(path)
            if (refValue != null) {
                val child = peekPath(path)
                if (child != null) SimpleConfig.checkValid(path, refValue, child, problems)
                else SimpleConfig.addMissing(problems, refValue, path, origin)
            }
        }
        if (!problems.isEmpty) throw new ConfigException.ValidationFailed(problems)
    }
    override def withOnlyPath(pathExpression: String): SimpleConfig = {
        val path = Path.newPath(pathExpression)
        new SimpleConfig(root.withOnlyPath(path))
    }
    override def withoutPath(pathExpression: String): SimpleConfig = {
        val path = Path.newPath(pathExpression)
        new SimpleConfig(root.withoutPath(path))
    }
    override def withValue(pathExpression: String, v: ConfigValue): SimpleConfig = {
        val path = Path.newPath(pathExpression)
        new SimpleConfig(root.withValue(path, v))
    }
    private[impl] def atKey(origin: ConfigOrigin, key: String) = root.atKey(origin, key)
    override def atKey(key: String): SimpleConfig = root.atKey(key)
    override def atPath(path: String): Config = root.atPath(path)
    // serialization all goes through SerializedConfigValue
    @throws[ObjectStreamException]
    private def writeReplace(): AnyRef = new SerializedConfigValue(this)
}
