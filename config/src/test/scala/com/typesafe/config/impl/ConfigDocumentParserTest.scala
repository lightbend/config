package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test

class ConfigDocumentParserTest extends TestUtils{

  private def parseTest(origText: String) {
      val node = ConfigDocumentParser.parse(tokenize(origText))
      assertEquals(origText, node.render())
  }

  @Test
  def parseSuccess {
      parseTest("foo:bar")
      parseTest(" foo : bar ")
      parseTest("""include "foo.conf" """)

      // Can parse a map with all simple types
      parseTest(
        """
        aUnquoted : bar
        aString = "qux"
        aNum:123
        aDouble=123.456
        aTrue=true
        aFalse=false
        aNull=null
        aSub =  ${a.b}
        include "foo.conf"
        """)
      parseTest("{}")
      parseTest("{foo:bar}")
      parseTest("{  foo  :  bar  }")
      parseTest("{foo:bar}     ")
      parseTest("""{include "foo.conf"}""")

      //Is this valid?
      //parseTest("  {  foo  :  bar  }  ")

      //Can parse a map with all simple types
      parseTest(
        """{
          aUnquoted : bar
          aString = "qux"
          aNum:123
          aDouble=123.456
          aTrue=true
          aFalse=false
          aNull=null
          aSub =  ${a.b}
          include "foo.conf"
          }""")

      // Test that maps can be nested within other maps
      parseTest(
          """
          foo.bar.baz : {
            qux : "abcdefg"
            "abc".def."ghi" : 123
            abc = { foo:bar }
          }
          qux = 123.456
          """)

      // Test that comments can be parsed in maps
      parseTest(
         """{
          foo: bar
          // This is a comment
          baz:qux // This is another comment
         }""")

      // Basic array tests
      parseTest("[]")
      parseTest("[foo]")

      // Test trailing comment and whitespace
      parseTest("[foo,]")
      parseTest("[foo,]     ")

      // Can parse arrays with all simple types
      parseTest("""[foo, bar,"qux", 123,123.456, true,false, null, ${a.b}]""")
      parseTest("""[foo,   bar,"qux"    , 123 ,  123.456, true,false, null,   ${a.b}   ]""")

      // Basic concatenation tests
      parseTest("[foo bar baz qux]")
      parseTest("{foo: foo bar baz qux}")
      parseTest("[abc 123 123.456 null true false [1, 2, 3] {a:b}, 2]")

      // Complex node with all types test
      parseTest(
        """{
          foo: bar baz    qux    ernie
          // The above was a concatenation

          baz   =   [ abc 123, {a:12
                                b: {
                                  c: 13
                                  d: {
                                    a: 22
                                    b: "abcdefg"
                                    c: [1, 2, 3]
                                  }
                                }
                                },
                                //The above value is a map containing a map containing a map, all in an array
                                22,
                                // The below value is an array contained in another array
                                [1,2,3]]
          // This is a map with some nested maps and arrays within it, as well as some concatenations
          qux {
            baz: abc 123
            bar: {
              baz: abcdefg
              bar: {
                a: null
                b: true
                c: [true false 123, null, [1, 2, 3]]
              }
            }
          }
        // Did I cover everything?
        }""")
  }
}
