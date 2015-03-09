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

    private def keyValueNodeTest(key: ConfigNodeKey, value: ConfigNodeValue, trailingWhitespace: ConfigNodeBasic, newValue: ConfigNodeValue) {
        val keyValNode = nodeKeyValuePair(key, value, trailingWhitespace)
        assertEquals(key.render() + " : " + value.render() + trailingWhitespace.render(), keyValNode.render())
        assertEquals(key.render, keyValNode.key().render())
        assertEquals(value.render, keyValNode.value().render())

        val newKeyValNode = keyValNode.replaceValue(newValue)
        assertEquals(key.render() + " : " + newValue.render() + trailingWhitespace.render(), newKeyValNode.render())
        assertEquals(newValue.render(), newKeyValNode.value().render())
    }

    private def topLevelValueReplaceTest(value: ConfigNodeValue, newValue: ConfigNodeValue, key: Token = tokenString("foo")) {
        val complexNodeChildren = List(nodeOpenBrace,
                                       nodeKeyValuePair(nodeWhitespace("       "), configNodeKey(key),value, nodeWhitespace("    ")),
                                       nodeCloseBrace)
        val complexNode = configNodeComplexValue(complexNodeChildren)
        val newNode = complexNode.setValueOnPath(Path.newPath(key.tokenText()), newValue)
        val origText = "{       " + key.tokenText() + " : " + value.render() + "    }"
        val finalText = "{       " + key.tokenText() + " : " + newValue.render() + "    }"

        assertEquals(origText, complexNode.render())
        assertEquals(finalText, newNode.render())
    }

    private def replaceInComplexValueTest(nodes: List[ConfigNode], origText: String, newText: String, replaceVal: ConfigNodeValue, replacePath: String) {
        val complexNode = configNodeComplexValue(nodes)
        assertEquals(complexNode.render(), origText)
        val newNode = complexNode.setValueOnPath(Path.newPath(replacePath), replaceVal)
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
    def createConfigNodeKeyValue() {
        // Supports Quoted and Unquoted keys
        keyValueNodeTest(nodeQuotedKey("abc"), nodeInt(123), nodeLine(1), nodeInt(245))
        keyValueNodeTest(nodeUnquotedKey("abc"), nodeInt(123), nodeLine(1), nodeInt(245))

        // Can replace value with values of different types
        keyValueNodeTest(nodeQuotedKey("abc"), nodeInt(123), nodeLine(1), nodeString("I am a string"))
        keyValueNodeTest(nodeQuotedKey("abc"), nodeInt(123), nodeLine(1), configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace)))
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
        val origText = "foo : bar\nbaz : {\n\t\"abc.def\" : 123\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
          "def : \"this is a string\"\n\t\tghi : ${\"a.b\"}\n\t}\n}\nbaz.abc.ghi : 52\nbaz.abc.ghi : 53\n}"
        val lowestLevelMap = configNodeComplexValue(List(nodeOpenBrace, nodeLine(6),
                                                         nodeKeyValuePair(nodeWhitespace("\t\t"), nodeUnquotedKey("def"), configNodeSimpleValue(tokenString("this is a string")), nodeLine(7)),
                                                         nodeKeyValuePair(nodeWhitespace("\t\t"), nodeUnquotedKey("ghi"), configNodeSimpleValue(tokenKeySubstitution("a.b")), nodeLine(8)),
                                                         nodeWhitespace("\t"), nodeCloseBrace))
        val higherLevelMap = configNodeComplexValue(List(nodeOpenBrace, nodeLine(2),
                                                         nodeKeyValuePair(nodeWhitespace("\t"), configNodeKey(tokenString("abc.def")), configNodeSimpleValue(tokenInt(123)), nodeLine(3)),
                                                         nodeWhitespace("\t"), configNodeBasic(tokenCommentDoubleSlash("This is a comment about the below setting")),
                                                         nodeLine(4), nodeLine(5),
                                                         nodeKeyValuePair(nodeWhitespace("\t"), nodeUnquotedKey("abc"), lowestLevelMap, nodeLine(9)), nodeWhitespace(""),
                                                         nodeCloseBrace))
        val origNode =  configNodeComplexValue(List(nodeKeyValuePair(nodeUnquotedKey("foo"), configNodeSimpleValue(tokenUnquoted("bar")), nodeLine(1)),
                                                    nodeKeyValuePair(nodeUnquotedKey("baz"), higherLevelMap, nodeLine(10)),
                                                    nodeKeyValuePair(nodeUnquotedKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(52)), nodeLine(11)),
                                                    nodeKeyValuePair(nodeUnquotedKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(53)), nodeLine(12)),
                                                    nodeCloseBrace))
        assertEquals(origText, origNode.render())
        val finalText = "foo : bar\nbaz : {\n\t\"abc.def\" : true\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
          "def : false\n\t}\n}\nbaz.abc.ghi : randomunquotedString\n}\nbaz.abc.this.does.not.exist : doesnotexist\n"

        //Can replace settings in nested maps
        // Paths with quotes in the name are treated as a single Path, rather than multiple sub-paths
        var newNode = origNode.setValueOnPath(Path.newPath("baz.\"abc.def\""), configNodeSimpleValue(tokenTrue))
        newNode = newNode.setValueOnPath(Path.newPath("baz.abc.def"), configNodeSimpleValue(tokenFalse))

        // Repeats are removed
        newNode = newNode.setValueOnPath(Path.newPath("baz.abc.ghi"), configNodeSimpleValue(tokenUnquoted("randomunquotedString")))

        // Missing paths are added to the top level
        newNode = newNode.setValueOnPath(Path.newPath("baz.abc.this.does.not.exist"), configNodeSimpleValue(tokenUnquoted("doesnotexist")))

        // The above operations cause the resultant map to be rendered properly
        assertEquals(finalText, newNode.render())
    }

}
