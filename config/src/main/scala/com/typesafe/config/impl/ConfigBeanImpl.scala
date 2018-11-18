package com.typesafe.config.impl

import java.beans.BeanInfo
import java.beans.IntrospectionException
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.{ util => ju }
import java.{ lang => jl }
import java.time.Duration
import scala.util.control.Breaks._
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType
import com.typesafe.config.Optional

/**
 * Internal implementation detail, not ABI stable, do not touch.
 * For use only by the {@link com.typesafe.config} package.
 */
object ConfigBeanImpl {

    /**
     * This is public ONLY for use by the "config" package, DO NOT USE this ABI
     * may change.
     *
     * @param <T> type of the bean
     * @param config config to use
     * @param clazz class of the bean
     * @return the bean instance
     */
    def createInternal[T](config: Config, clazz: Class[T]): T = {
        if (config
            .asInstanceOf[SimpleConfig]
            .root
            .resolveStatus != ResolveStatus.RESOLVED)
            throw new ConfigException.NotResolved(
                "need to Config#resolve() a config before using it to initialize a bean, see the API docs for Config#resolve()")
        val configProps =
            new ju.HashMap[String, AbstractConfigValue]
        val originalNames = new ju.HashMap[String, String]
        import scala.collection.JavaConverters._
        for (configProp <- config.root.entrySet.asScala) {
            val originalName = configProp.getKey
            val camelName = ConfigImplUtil.toCamelCase(originalName)
            // if a setting is in there both as some hyphen name and the camel name,
            // the camel one wins
            if (originalNames.containsKey(camelName) && !(originalName == camelName)) {
                // if we aren't a camel name to start with, we lose.
                // if we are or we are the first matching key, we win.
            } else {
                configProps.put(
                    camelName,
                    configProp.getValue.asInstanceOf[AbstractConfigValue])
                originalNames.put(camelName, originalName)
            }
        }
        var beanInfo: BeanInfo = null
        try beanInfo = Introspector.getBeanInfo(clazz)
        catch {
            case e: IntrospectionException =>
                throw new ConfigException.BadBean(
                    "Could not get bean information for class " + clazz.getName,
                    e)
        }
        try {
            val beanProps =
                new ju.ArrayList[PropertyDescriptor]
            for (beanProp <- beanInfo.getPropertyDescriptors) {
                breakable {
                    if (beanProp.getReadMethod == null || beanProp.getWriteMethod == null)
                        break // continue
                    beanProps.add(beanProp)
                }
            }
            // Try to throw all validation issues at once (this does not comprehensively
            // find every issue, but it should find common ones).
            val problems = new ju.ArrayList[ConfigException.ValidationProblem]
            import scala.collection.JavaConverters._
            for (beanProp <- beanProps.asScala) {
                val setter: Method = beanProp.getWriteMethod
                val parameterClass: Class[_] = setter.getParameterTypes()(0)
                val expectedType = getValueTypeOrNull(parameterClass)
                if (expectedType != null) {
                    var name = originalNames.get(beanProp.getName)
                    if (name == null) name = beanProp.getName
                    val path = Path.newKey(name)
                    val configValue =
                        configProps.get(beanProp.getName)
                    if (configValue != null) SimpleConfig.checkValid(path, expectedType, configValue, problems) else if (!isOptionalProperty(clazz, beanProp))
                        SimpleConfig.addMissing(
                            problems,
                            expectedType,
                            path,
                            config.origin)
                }
            }
            if (!problems.isEmpty) throw new ConfigException.ValidationFailed(problems)
            // Fill in the bean instance
            val bean = clazz.newInstance
            import scala.collection.JavaConverters._
            for (beanProp <- beanProps.asScala) {
                breakable {
                    val setter = beanProp.getWriteMethod
                    val parameterType = setter.getGenericParameterTypes()(0)
                    val parameterClass = setter.getParameterTypes()(0)
                    val configPropName = originalNames.get(beanProp.getName)
                    // Is the property key missing in the config?
                    if (configPropName == null) { // If so, continue if the field is marked as @{link Optional}
                        if (isOptionalProperty(clazz, beanProp))
                            break // continue, Otherwise, raise a {@link Missing} exception right here
                        throw new ConfigException.Missing(beanProp.getName)
                    }
                    val unwrapped =
                        getValue(clazz, parameterType, parameterClass, config, configPropName)
                    setter.invoke(bean, unwrapped.asInstanceOf[AnyRef])
                }
            }
            bean
        } catch {
            case e: InstantiationException =>
                throw new ConfigException.BadBean(
                    clazz.getName + " needs a public no-args constructor to be used as a bean",
                    e)
            case e: IllegalAccessException =>
                throw new ConfigException.BadBean(
                    clazz.getName + " getters and setters are not accessible, they must be for use as a bean",
                    e)
            case e: InvocationTargetException =>
                throw new ConfigException.BadBean(
                    "Calling bean method on " + clazz.getName + " caused an exception",
                    e)
        }
    }
    // we could magically make this work in many cases by doing
    // getAnyRef() (or getValue().unwrapped()), but anytime we
    // rely on that, we aren't doing the type conversions Config
    // usually does, and we will throw ClassCastException instead
    // of a nicer error message giving the name of the bad
    // setting. So, instead, we only support a limited number of
    // types plus you can always use Object, ConfigValue, Config,
    // ConfigObject, etc.  as an escape hatch.
    private def getValue[T <: Enum[T]](
        beanClass: Class[_],
        parameterType: Type,
        parameterClass: Class[_],
        config: Config,
        configPropName: String)(implicit m: Manifest[T]): Any =
        if ((parameterClass == classOf[jl.Boolean]) || (parameterClass == classOf[Boolean])) config.getBoolean(configPropName)
        else if ((parameterClass == classOf[Integer]) || (parameterClass == classOf[Int]))
            config.getInt(configPropName)
        else if ((parameterClass == classOf[jl.Double]) || (parameterClass == classOf[Double])) config.getDouble(configPropName)
        else if ((parameterClass == classOf[jl.Long]) || (parameterClass == classOf[Long]))
            config.getLong(configPropName)
        else if (parameterClass == classOf[String]) config.getString(configPropName)
        else if (parameterClass == classOf[Duration]) config.getDuration(configPropName)
        else if (parameterClass == classOf[ConfigMemorySize])
            config.getMemorySize(configPropName)
        else if (parameterClass == classOf[Any]) config.getAnyRef(configPropName)
        else if (parameterClass == classOf[ju.List[_]])
            getListValue(
                beanClass,
                parameterType,
                parameterClass,
                config,
                configPropName)
        else if (parameterClass == classOf[ju.Set[_]])
            getSetValue(
                beanClass,
                parameterType,
                parameterClass,
                config,
                configPropName)
        else if (parameterClass == classOf[ju.Map[_, _]]) { // we could do better here, but right now we don't.
            val typeArgs = parameterType
                .asInstanceOf[ParameterizedType]
                .getActualTypeArguments
            if ((typeArgs(0) != classOf[String]) || (typeArgs(1) != classOf[Any]))
                throw new ConfigException.BadBean(
                    "Bean property '" + configPropName + "' of class " + beanClass.getName + " has unsupported Map<" + typeArgs(
                        0) + "," + typeArgs(1) + ">, only Map<String,Object> is supported right now")
            config.getObject(configPropName).unwrapped
        } else if (parameterClass == classOf[Config]) config.getConfig(configPropName)
        else if (parameterClass == classOf[ConfigObject]) config.getObject(configPropName)
        else if (parameterClass == classOf[ConfigValue]) config.getValue(configPropName)
        else if (parameterClass == classOf[ConfigList]) config.getList(configPropName)
        else if (parameterClass.isEnum) {
            val enumValue = config.getEnum(
                parameterClass.asInstanceOf[Class[T]],
                configPropName)
            enumValue
        } else if (hasAtLeastOneBeanProperty(parameterClass))
            createInternal(config.getConfig(configPropName), parameterClass)
        else
            throw new ConfigException.BadBean(
                "Bean property " + configPropName + " of class " + beanClass.getName + " has unsupported type " + parameterType)

    private def getSetValue(
        beanClass: Class[_],
        parameterType: Type,
        parameterClass: Class[_],
        config: Config,
        configPropName: String) = new ju.HashSet(
        getListValue(
            beanClass,
            parameterType,
            parameterClass,
            config,
            configPropName))

    private def getListValue[T <: Enum[T]](
        beanClass: Class[_],
        parameterType: Type,
        parameterClass: Class[_],
        config: Config,
        configPropName: String)(implicit m: Manifest[T]): ju.List[_] = {
        val elementType: Type =
            parameterType.asInstanceOf[ParameterizedType].getActualTypeArguments()(0)
        if (elementType == classOf[jl.Boolean]) config.getBooleanList(configPropName)
        else if (elementType == classOf[Integer]) config.getIntList(configPropName)
        else if (elementType == classOf[jl.Double]) config.getDoubleList(configPropName)
        else if (elementType == classOf[jl.Long]) config.getLongList(configPropName)
        else if (elementType == classOf[String]) config.getStringList(configPropName)
        else if (elementType == classOf[Duration]) config.getDurationList(configPropName)
        else if (elementType == classOf[ConfigMemorySize]) config.getMemorySizeList(configPropName)
        else if (elementType == classOf[Any]) config.getAnyRefList(configPropName)
        else if (elementType == classOf[Config]) config.getConfigList(configPropName)
        else if (elementType == classOf[ConfigObject]) config.getObjectList(configPropName)
        else if (elementType == classOf[ConfigValue]) config.getList(configPropName)
        else if (elementType.asInstanceOf[Class[_]].isEnum) {
            val enumValues = config.getEnumList(
                elementType.asInstanceOf[Class[T]],
                configPropName)
            enumValues
        } else if (hasAtLeastOneBeanProperty(
            elementType.asInstanceOf[Class[_]])) {
            val beanList = new ju.ArrayList[AnyRef]
            val configList: ju.List[_ <: Config] = config.getConfigList(configPropName)
            import scala.collection.JavaConverters._
            for (listMember <- configList.asScala) {
                beanList.add(
                    createInternal(
                        listMember,
                        elementType.asInstanceOf[Class[T]]))
            }
            beanList
        } else
            throw new ConfigException.BadBean(
                "Bean property '" + configPropName + "' of class " + beanClass.getName + " has unsupported list element type " + elementType)
    }

    // null if we can't easily say; this is heuristic/best-effort
    private def getValueTypeOrNull(parameterClass: Class[_]) =
        if ((parameterClass == classOf[jl.Boolean]) || (parameterClass == classOf[Boolean])) ConfigValueType.BOOLEAN
        else if ((parameterClass == classOf[Integer]) || (parameterClass == classOf[Int])) ConfigValueType.NUMBER
        else if ((parameterClass == classOf[jl.Double]) || (parameterClass == classOf[Double])) ConfigValueType.NUMBER
        else if ((parameterClass == classOf[jl.Long]) || (parameterClass == classOf[Long])) ConfigValueType.NUMBER
        else if (parameterClass == classOf[String]) ConfigValueType.STRING
        else if (parameterClass == classOf[Duration]) null
        else if (parameterClass == classOf[ConfigMemorySize]) null
        else if (parameterClass == classOf[ju.List[_]]) ConfigValueType.LIST
        else if (parameterClass == classOf[ju.Map[_, _]]) ConfigValueType.OBJECT
        else if (parameterClass == classOf[Config]) ConfigValueType.OBJECT
        else if (parameterClass == classOf[ConfigObject]) ConfigValueType.OBJECT
        else if (parameterClass == classOf[ConfigList]) ConfigValueType.LIST else null

    private def hasAtLeastOneBeanProperty(clazz: Class[_]): Boolean = {
        var beanInfo: BeanInfo = null
        try beanInfo = Introspector.getBeanInfo(clazz)
        catch {
            case e: IntrospectionException =>
                return false
        }
        for (beanProp <- beanInfo.getPropertyDescriptors) {
            if (beanProp.getReadMethod != null && beanProp.getWriteMethod != null) return true
        }
        false
    }

    private def isOptionalProperty(
        beanClass: Class[_],
        beanProp: PropertyDescriptor) = {
        val field = getField(beanClass, beanProp.getName)
        if (field != null) field.getAnnotationsByType(classOf[Optional]).length > 0
        else beanProp.getReadMethod.getAnnotationsByType(classOf[Optional]).length > 0
    }

    private def getField(beanClass: Class[_], fieldName: String): Field = {
        try {
            val field = beanClass.getDeclaredField(fieldName)
            field.setAccessible(true)
            return field
        } catch {
            case e: NoSuchFieldException =>
            // Don't give up yet. Try to look for field in super class, if any.
        }
        val beanSuperClass: Class[_] = beanClass.getSuperclass
        if (beanSuperClass == null) return null
        getField(beanSuperClass, fieldName)
    }
}
