package com.typesafe.config.impl;

import static org.junit.Assert.*;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

public class ArrayWithObjectTest {

	static void log(Object text) {
		System.err.println("" + text);
	}

	static Config config(ConfigValue value) {
		return ((ConfigObject) value).toConfig();
	}

	@Test
	public void test1() {

		final String text = "{ list = [] {}  }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");

		assertEquals("emtpy list", list.size(), 0);

	}

	@Test
	public void test2() {

		final String text = "{ list = [ {} ] {}  }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");

		assertEquals("one item list", list.size(), 1);

	}

	@Test
	public void test3() {

		final String text = "{ list = [ { a:1 } ] {}  }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("one item list", list.size(), 1);

		Config entry = config(list.get(0));
		assertEquals("provided a", entry.getNumber("a"), 1);

	}

	@Test
	public void test4() {

		final String text = "{ list = [ { a:1 } ] { a:2, b:2 }  }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("one item list", list.size(), 1);

		Config entry = config(list.get(0));
		assertEquals("provided a", entry.getNumber("a"), 1);
		assertEquals("default  b", entry.getNumber("b"), 2);

	}

	@Test
	public void test5() {

		final String text = "{ item = {} , list = [] ${item}  }";

		Config config = ConfigFactory.parseString(text).resolve();

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("empty list", list.size(), 0);

	}

	@Test
	public void test6() {

		final String text = "{ item = {} , list = [ {} ] ${item}  }";

		Config config = ConfigFactory.parseString(text).resolve();

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("one item list", list.size(), 1);

	}

	@Test
	public void test7() {

		final String text = "{ item = { a:1, b:2 } , list = [ { a:2 } ] ${item}  }";

		Config config = ConfigFactory.parseString(text).resolve();

		ConfigList list = config.getList("list");
		assertEquals("one item list", list.size(), 1);

		Config entry = config(list.get(0));
		assertEquals("provided a", entry.getNumber("a"), 2);
		assertEquals("default  b", entry.getNumber("b"), 2);

	}

	/** left-associative: fall-back to object then concatenate with list */
	@Test
	public void test8() {

		final String text = "{ list = [ { a:1 } ] { b:2 } [ { c:3 } ] }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("two item list", list.size(), 2);

		Config entry0 = config(list.get(0));
		assertEquals("provided a", entry0.getNumber("a"), 1);
		assertEquals("default  b", entry0.getNumber("b"), 2);

		Config entry1 = config(list.get(1));
		assertEquals("concat   c", entry1.getNumber("c"), 3);

	}

	/** left-associative: fall-back to object 1 then fall-back to object 2 */
	@Test
	public void test9() {

		final String text = "{ list = [ { a:1 } ] { b:2 } { c:3 } }";

		Config config = ConfigFactory.parseString(text);

		log(config);

		ConfigList list = config.getList("list");
		assertEquals("one item list", list.size(), 1);

		Config entry = config(list.get(0));
		assertEquals("provided a", entry.getNumber("a"), 1);
		assertEquals("object 1 b", entry.getNumber("b"), 2);
		assertEquals("object 2 c", entry.getNumber("c"), 3);

	}

}
