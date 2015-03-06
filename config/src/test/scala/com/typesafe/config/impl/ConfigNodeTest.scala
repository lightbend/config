package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test
import com.typesafe.config.ConfigNode

class ConfigNodeTest extends TestUtils {

    private def basicNodeTest(token: Token) {
        val node = configNodeBasic(token);
        assertEquals(node.render(), token.tokenText())
    }

    private def keyNodeTest(token: Token) {
        val node = configNodeKey(token)
        assertEquals(node.render(), token.tokenText())
    }

    private def simpleValueNodeTest(token: Token) {
        val node = configNodeSimpleValue(token)
        assertEquals(node.render(), token.tokenText())
    }

    private def topLevelValueReplaceTest(value: ConfigNodeValue, newValue: ConfigNodeValue, key: Token = tokenString("foo")) {
        val complexNodeChildren = List(nodeOpenBrace, nodeWhitespace("       "),
                                       configNodeKey(key), nodeWhitespace("    "), nodeColon,
                                       value, nodeWhitespace("    "), nodeCloseBrace)
        val complexNode = configNodeComplexValue(complexNodeChildren)
        val newNode = complexNode.replaceValueOnPath(Path.newPath(key.tokenText()), newValue)
        val origText = "{       " + key.tokenText() + "    :" + value.render() + "    }"
        val finalText = "{       " + key.tokenText() + "    :" + newValue.render() + "    }"

        assertEquals(origText, complexNode.render())
        assertEquals(finalText, newNode.render())
    }

    private def replaceInComplexValueTest(nodes: List[ConfigNode], origText: String, newText: String, replaceVal: ConfigNodeValue, replacePath: String) {
        val complexNode = configNodeComplexValue(nodes)
        assertEquals(complexNode.render(), origText)
        val newNode = complexNode.replaceValueOnPath(Path.newPath(replacePath), replaceVal)
        assertEquals(newNode.render(), newText)
    }

    @Test
    def createBasicConfigNode() {
        //Ensure a BasicConfigNode can handle all its required token types
        basicNodeTest(Tokens.START)
        basicNodeTest(Tokens.END)
        basicNodeTest(Tokens.OPEN_CURLY)
        basicNodeTest(Tokens.CLOSE_CURLY)
        basicNodeTest(Tokens.OPEN_SQUARE)
        basicNodeTest(Tokens.CLOSE_SQUARE)
        basicNodeTest(Tokens.COMMA)
        basicNodeTest(Tokens.EQUALS)
        basicNodeTest(Tokens.COLON)
        basicNodeTest(Tokens.PLUS_EQUALS)
        basicNodeTest(tokenUnquoted("             "))
        basicNodeTest(tokenWhitespace("             "))
        basicNodeTest(tokenLine(1))
        basicNodeTest(tokenCommentDoubleSlash("  this is a double slash comment   "))
        basicNodeTest(tokenCommentHash("   this is a hash comment   "))
    }

    @Test
    def createConfigNodeSetting() {
        //Ensure a ConfigNodeSetting can handle the normal key types
        keyNodeTest(tokenUnquoted("foo"))
        keyNodeTest(tokenString("Hello I am a key how are you today"))
    }

    @Test
    def createConfigNodeSimpleValue() {
        //Ensure a ConfigNodeSimpleValue can handle the normal value types
        simpleValueNodeTest(tokenInt(10))
        simpleValueNodeTest(tokenLong(10000))
        simpleValueNodeTest(tokenDouble(3.14159))
        simpleValueNodeTest(tokenFalse)
        simpleValueNodeTest(tokenTrue)
        simpleValueNodeTest(tokenNull)
        simpleValueNodeTest(tokenString("Hello my name is string"))
        simpleValueNodeTest(tokenUnquoted("mynameisunquotedstring"))
        simpleValueNodeTest(tokenKeySubstitution("c.d"))
        simpleValueNodeTest(tokenOptionalSubstitution(tokenUnquoted("x.y")))
        simpleValueNodeTest(tokenSubstitution(tokenUnquoted("a.b")))
    }

    @Test
    def replaceNodesTopLevel() {
        //Ensure simple values can be replaced by other simple values
        topLevelValueReplaceTest(configNodeSimpleValue(tokenInt(10)), configNodeSimpleValue(tokenInt(15)))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenLong(10000)), configNodeSimpleValue(tokenInt(20)))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenDouble(3.14159)), configNodeSimpleValue(tokenLong(10000)))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenFalse), configNodeSimpleValue(tokenTrue))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenTrue), configNodeSimpleValue(tokenNull))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenNull), configNodeSimpleValue(tokenString("Hello my name is string")))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenString("Hello my name is string")), configNodeSimpleValue(tokenUnquoted("mynameisunquotedstring")))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenUnquoted("mynameisunquotedstring")), configNodeSimpleValue(tokenKeySubstitution("c.d")))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenInt(10)), configNodeSimpleValue(tokenOptionalSubstitution(tokenUnquoted("x.y"))))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenInt(10)), configNodeSimpleValue(tokenSubstitution(tokenUnquoted("a.b"))))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenSubstitution(tokenUnquoted("a.b"))), configNodeSimpleValue(tokenInt(10)))

        //Ensure maps can be replaced
        val nestedMap = configNodeComplexValue(List(nodeOpenBrace, nodeUnquotedKey("abc"),
                                                    nodeColon, configNodeSimpleValue(tokenString("a string")),
                                                    nodeCloseBrace))
        topLevelValueReplaceTest(nestedMap, configNodeSimpleValue(tokenInt(10)))
        topLevelValueReplaceTest(configNodeSimpleValue(tokenInt(10)), nestedMap)
        topLevelValueReplaceTest(nestedMap, configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace)))

        //Ensure a key with format "a.b" will be properly replaced
        topLevelValueReplaceTest(configNodeSimpleValue(tokenInt(10)), nestedMap, tokenUnquoted("foo.bar"))
    }

    @Test
    def replaceInNestedMapComplexValue() {
        val origText = "{\n\tfoo : bar\n\tbaz : {\n\t\t\"abc.def\" : 123\n\t\t//This is a comment about the below setting\n\n\t\tabc : {\n\t\t\t" +
          "def : \"this is a string\"\n\t\t\tghi: ${\"a.b\"}\n\t\t}\n\t}\n}"
        val lowestLevelMap = configNodeComplexValue(List(nodeOpenBrace,
                                                         nodeLine(7), nodeWhitespace("\t\t\t"), nodeUnquotedKey("def"), nodeSpace, nodeColon, nodeSpace,
                                                         configNodeSimpleValue(tokenString("this is a string")), nodeLine(8), nodeWhitespace("\t\t\t"),
                                                         nodeUnquotedKey("ghi"), nodeColon, nodeSpace, configNodeSimpleValue(tokenKeySubstitution("a.b")),
                                                         nodeLine(9), nodeWhitespace("\t\t"), nodeCloseBrace))
        val higherLevelMap = configNodeComplexValue(List(nodeOpenBrace, configNodeBasic(tokenLine(3)), nodeWhitespace("\t\t"),
                                                         configNodeKey(tokenString("abc.def")), nodeSpace, nodeColon,
                                                         nodeSpace, configNodeSimpleValue(tokenInt(123)), configNodeBasic(tokenLine(4)),
                                                         nodeWhitespace("\t\t"), configNodeBasic(tokenCommentDoubleSlash("This is a comment about the below setting")),
                                                         configNodeBasic(tokenLine(5)), configNodeBasic(tokenLine(6)), nodeWhitespace("\t\t"),
                                                         nodeUnquotedKey("abc"), nodeSpace, nodeColon, nodeSpace, lowestLevelMap, nodeLine(10), nodeWhitespace("\t"),
                                                         nodeCloseBrace))
        val origNode =  configNodeComplexValue(List(nodeOpenBrace, nodeLine(1), nodeWhitespace("\t"),
                                                    nodeUnquotedKey("foo"), nodeSpace, nodeColon,
                                                    nodeSpace, configNodeSimpleValue(tokenUnquoted("bar")),
                                                    nodeLine(2), nodeWhitespace("\t"), nodeUnquotedKey("baz"),
                                                    nodeSpace, nodeColon, nodeSpace,
                                                    higherLevelMap, nodeLine(11), nodeCloseBrace))
        assertEquals(origText, origNode.render())
        val finalText = "{\n\tfoo : bar\n\tbaz : {\n\t\t\"abc.def\" : true\n\t\t//This is a comment about the below setting\n\n\t\tabc : {\n\t\t\t" +
          "def : false\n\t\t\tghi: randomunquotedString\n\t\t}\n\t}\n}"

        //Can replace settings in nested maps
        var newNode = origNode.replaceValueOnPath(Path.newPath("baz.\"abc.def\""), configNodeSimpleValue(tokenTrue))
        newNode = newNode.replaceValueOnPath(Path.newPath("baz.abc.def"), configNodeSimpleValue(tokenFalse))
        newNode = newNode.replaceValueOnPath(Path.newPath("baz.abc.ghi"), configNodeSimpleValue(tokenUnquoted("randomunquotedString")))
        assertEquals(finalText, newNode.render())
    }

}
