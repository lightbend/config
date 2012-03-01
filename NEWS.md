# 0.3.0: March 1, 2012

 - ConfigFactory methods now use the thread's context class loader
   by default, and have overloads so you can specify a class
   loader. Because jars may come with "reference.conf" in the jar,
   a config must always be loaded with the same class loader as
   the jar using the config.
 - ConfigValue instances are now serializable
 - new methods ConfigObject.withoutKey, ConfigObject.withOnlyKey,
   Config.withoutPath, Config.withOnlyPath allow subsetting
   configs more easily.
 - better handle complex interdependent substitutions (the
   `${foo}` syntax) without getting confused; just about anything
   that makes conceptual sense should now work. Only inherently
   circular config files should fail.
 - some minor documentation fixes.
