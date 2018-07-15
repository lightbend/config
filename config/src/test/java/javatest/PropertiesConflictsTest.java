package javatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueType;

public class PropertiesConflictsTest {

    private Properties simpleProps;
    private Properties valueProps;
    private Properties nestingProps;

    public static enum TestEnum {
        one, two, three
    }

    @Before
    public void setup() {
        simpleProps = new Properties();
        simpleProps.put("a.b", "conflictingValue");
        simpleProps.put("a.b.c", "value");

        valueProps = new Properties();
        valueProps.put("a.i", "123");
        valueProps.put("a.l", "2384762387462234");
        valueProps.put("a.d", "1.0");
        valueProps.put("a.b", "false");
        valueProps.put("a.e", "two");
        valueProps.put("a.s", "1s");
        valueProps.put("a.il", "1,2,3");

        valueProps.put("a.i.x", "123");
        valueProps.put("a.l.x", "2384762387462234");
        valueProps.put("a.d.x", "1.0");
        valueProps.put("a.b.x", "false");
        valueProps.put("a.e.x", "two");
        valueProps.put("a.s.x", "1s");

        nestingProps = new Properties();
        nestingProps.put("a", "0");
        nestingProps.put("a.b", "123");
        nestingProps.put("a.b.c", "456");
        nestingProps.put("a.b.c.d", "789");
        nestingProps.put("a.b.c.d.x", "");
    }

    @Test
    public void loadNoConflicts() {
        Config conf = ConfigFactory.parseProperties(simpleProps);

        assertTrue(conf.hasPath("a"));
        assertTrue(conf.hasPath("a.b"));
        assertTrue(conf.hasPath("a.b.c"));
        assertTrue(conf.hasPathAndObject("a"));
        assertTrue(conf.hasPathAndObject("a.b"));
        assertFalse(conf.hasPathAndObject("a.b.c"));
        assertFalse(conf.hasPathAndObject("a.b.x"));
        assertFalse(conf.hasPathAndNoObject("a"));
        assertFalse(conf.hasPathAndNoObject("a.b"));
        assertTrue(conf.hasPathAndNoObject("a.b.c"));
        assertFalse(conf.hasPathAndNoObject("a.b.x"));

        assertNotNull(conf.getConfig("a.b"));
        assertNotNull(conf.getObject("a.b"));
        assertNull(conf.getObject("a.b").getConflictingValue());
        assertEquals(conf.root().render(ConfigRenderOptions.concise()), "{\"a\":{\"b\":{\"c\":\"value\"}}}");
        assertEquals(conf.root().render(ConfigRenderOptions.concise().setConflictingValues(true)), "{\"a\":{\"b\":{\"c\":\"value\"}}}");

        try {
            conf.getString("a.b");
            assertTrue(false);
        } catch (ConfigException e) {
            assertTrue(e instanceof ConfigException.WrongType);
            assertEquals(e.getMessage(), "properties: a.b has type OBJECT rather than STRING");
        }
    }

    @Test
    public void loadConflicts() {
        Config conf = ConfigFactory.parseProperties(simpleProps, ConfigParseOptions.defaults().setAllowConflictingValues(true));

        assertTrue(conf.hasPath("a"));
        assertTrue(conf.hasPath("a.b"));
        assertTrue(conf.hasPath("a.b.c"));
        assertTrue(conf.hasPathAndObject("a"));
        assertTrue(conf.hasPathAndObject("a.b"));
        assertFalse(conf.hasPathAndObject("a.b.c"));
        assertFalse(conf.hasPathAndObject("a.b.x"));
        assertFalse(conf.hasPathAndNoObject("a"));
        assertTrue(conf.hasPathAndNoObject("a.b"));
        assertTrue(conf.hasPathAndNoObject("a.b.c"));
        assertFalse(conf.hasPathAndNoObject("a.b.x"));

        assertNotNull(conf.getConfig("a.b"));
        assertNotNull(conf.getObject("a.b"));
        assertNotNull(conf.getObject("a.b").getConflictingValue());
        assertEquals(conf.getObject("a.b").getConflictingValue().valueType(), ConfigValueType.STRING);
        assertEquals(conf.getString("a.b"), "conflictingValue");
        assertEquals(conf.root().render(ConfigRenderOptions.concise()), "{\"a\":{\"b\":{\"c\":\"value\"}}}");
        assertEquals(conf.root().render(ConfigRenderOptions.concise().setConflictingValues(true)), "{\"a\":{\"b\":{\"c\":\"value\"}|\"conflictingValue\"}}");
    }
    
    @Test
    public void testValues() {
        Config conf = ConfigFactory.parseProperties(valueProps, ConfigParseOptions.defaults().setAllowConflictingValues(true));

        assertEquals(conf.getInt("a.i"), 123);
        assertEquals(conf.getNumber("a.i").intValue(), 123);
        assertEquals(conf.getLong("a.l"), 2384762387462234L);
        assertEquals(conf.getNumber("a.l").longValue(), 2384762387462234L);
        assertEquals(conf.getDouble("a.d"), 1.0d, 0);
        assertEquals(conf.getBoolean("a.b"), false);
        assertEquals(conf.getEnum(TestEnum.class, "a.e"), TestEnum.two);
        assertEquals(conf.getDuration("a.s", TimeUnit.MILLISECONDS), 1000);
    }

    @Test
    public void testNesting() {
        Config conf = ConfigFactory.parseProperties(nestingProps, ConfigParseOptions.defaults().setAllowConflictingValues(true));
        assertEquals(conf.toString(), "Config(SimpleConfigObject({\"a\":{\"b\":{\"c\":{\"d\":{\"x\":\"\"}|\"789\"}|\"456\"}|\"123\"}|\"0\"}))");

        assertEquals(conf.getInt("a"), 0);
        conf = conf.getConfig("a");
        assertNotNull(conf);

        assertEquals(conf.getInt("b"), 123);
        conf = conf.getConfig("b");
        assertNotNull(conf);

        assertEquals(conf.getInt("c"), 456);
        conf = conf.getConfig("c");
        assertNotNull(conf);

        assertEquals(conf.getInt("d"), 789);
        conf = conf.getConfig("d");
        assertNotNull(conf);

        assertTrue(conf.hasPath("x"));
    }

}
