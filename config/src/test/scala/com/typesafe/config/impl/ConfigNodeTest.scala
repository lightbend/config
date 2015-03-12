package com.typesafe.config.impl

import org.junit.Assert._
import org.junit.Test

class ConfigNodeTest extends TestUtils {

    private def basicNodeTest(token: Token) {
        val node = configNodeBasic(token);
        assertEquals(node.render(), token.tokenText())
    }

    private def keyNodeTest(path: String) {
        val node = configNodeKey(path)
        assertEquals(path, node.render())
    }

    private def simpleValueNodeTest(token: Token) {
        val node = configNodeSimpleValue(token)
        assertEquals(node.render(), token.tokenText())
    }

    private def keyValueNodeTest(key: ConfigNodePath, value: AbstractConfigNodeValue, trailingWhitespace: ConfigNodeSingleToken, newValue: AbstractConfigNodeValue) {
        val keyValNode = nodeKeyValuePair(key, value, trailingWhitespace)
        assertEquals(key.render() + " : " + value.render() + trailingWhitespace.render(), keyValNode.render())
        assertEquals(key.render, keyValNode.path().render())
        assertEquals(value.render, keyValNode.value().render())

        val newKeyValNode = keyValNode.replaceValue(newValue)
        assertEquals(key.render() + " : " + newValue.render() + trailingWhitespace.render(), newKeyValNode.render())
        assertEquals(newValue.render(), newKeyValNode.value().render())
    }

    private def topLevelValueReplaceTest(value: AbstractConfigNodeValue, newValue: AbstractConfigNodeValue, key: String = "foo") {
        val complexNodeChildren = List(nodeOpenBrace,
                                       nodeKeyValuePair(nodeWhitespace("       "), configNodeKey(key),value, nodeWhitespace("    ")),
                                       nodeCloseBrace)
        val complexNode = configNodeComplexValue(complexNodeChildren)
        val newNode = complexNode.setValueOnPath(key, newValue)
        val origText = "{       " + key + " : " + value.render() + "    }"
        val finalText = "{       " + key + " : " + newValue.render() + "    }"

        assertEquals(origText, complexNode.render())
        assertEquals(finalText, newNode.render())
    }

    private def replaceDuplicatesTest(value1: AbstractConfigNodeValue, value2: AbstractConfigNodeValue, value3: AbstractConfigNodeValue) {
        val key = configNodeKey("foo")
        val keyValPair1 = nodeKeyValuePair(key, value1)
        val keyValPair2 = nodeKeyValuePair(key, value2)
        val keyValPair3 = nodeKeyValuePair(key, value3)
        val complexNode = configNodeComplexValue(List(keyValPair1, keyValPair2, keyValPair3))
        val origText = keyValPair1.render() + keyValPair2.render() + keyValPair3.render()
        val finalText = key.render() + " : 15"

        assertEquals(origText, complexNode.render())
        assertEquals(finalText, complexNode.setValueOnPath("foo", nodeInt(15)).render())
    }

    private def nonExistentPathTest(value: AbstractConfigNodeValue) {
        val node = configNodeComplexValue(List(nodeKeyValuePair(configNodeKey("bar"), nodeInt(15))))
        assertEquals("bar : 15", node.render())
        val newNode = node.setValueOnPath("foo", value)
        val finalText = "bar : 15\nfoo : " + value.render() + "\n"
        assertEquals(finalText, newNode.render())
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
        keyNodeTest("foo")
        keyNodeTest("\"Hello I am a key how are you today\"")
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
        keyValueNodeTest(configNodeKey("\"abc\""), nodeInt(123), nodeLine(1), nodeInt(245))
        keyValueNodeTest(configNodeKey("abc"), nodeInt(123), nodeLine(1), nodeInt(245))

        // Can replace value with values of different types
        keyValueNodeTest(configNodeKey("\"abc\""), nodeInt(123), nodeLine(1), nodeString("I am a string"))
        keyValueNodeTest(configNodeKey("\"abc\""), nodeInt(123), nodeLine(1), configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace)))
    }

    @Test
    def replaceNodes() {
        //Ensure simple values can be replaced by other simple values
        topLevelValueReplaceTest(nodeInt(10), nodeInt(15))
        topLevelValueReplaceTest(nodeLong(10000), nodeInt(20))
        topLevelValueReplaceTest(nodeDouble(3.14159), nodeLong(10000))
        topLevelValueReplaceTest(nodeFalse, nodeTrue)
        topLevelValueReplaceTest(nodeTrue, nodeNull)
        topLevelValueReplaceTest(nodeNull, nodeString("Hello my name is string"))
        topLevelValueReplaceTest(nodeString("Hello my name is string"), nodeUnquotedText("mynameisunquotedstring"))
        topLevelValueReplaceTest(nodeUnquotedText("mynameisunquotedstring"), nodeKeySubstitution("c.d"))
        topLevelValueReplaceTest(nodeInt(10), nodeOptionalSubstitution(tokenUnquoted("x.y")))
        topLevelValueReplaceTest(nodeInt(10), nodeSubstitution(tokenUnquoted("a.b")))
        topLevelValueReplaceTest(nodeSubstitution(tokenUnquoted("a.b")), nodeInt(10))

        // Ensure arrays can be replaced
        val array = configNodeComplexValue(List(nodeOpenBracket, nodeInt(10), nodeSpace, nodeComma, nodeSpace, nodeInt(15), nodeCloseBracket))
        topLevelValueReplaceTest(nodeInt(10), array)
        topLevelValueReplaceTest(array, nodeInt(10))
        topLevelValueReplaceTest(array, configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace)))

        //Ensure maps can be replaced
        val nestedMap = configNodeComplexValue(List(nodeOpenBrace, configNodeKey("abc"),
                                                    nodeColon, configNodeSimpleValue(tokenString("a string")),
                                                    nodeCloseBrace))
        topLevelValueReplaceTest(nestedMap, nodeInt(10))
        topLevelValueReplaceTest(nodeInt(10), nestedMap)
        topLevelValueReplaceTest(array, nestedMap)
        topLevelValueReplaceTest(nestedMap, array)
        topLevelValueReplaceTest(nestedMap, configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace)))

        //Ensure a key with format "a.b" will be properly replaced
        topLevelValueReplaceTest(nodeInt(10), nestedMap, "foo.bar")
    }

    @Test
    def removeDuplicates() {
        val emptyMapNode = configNodeComplexValue(List(nodeOpenBrace, nodeCloseBrace))
        val emptyArrayNode = configNodeComplexValue(List(nodeOpenBracket, nodeCloseBracket))
        //Ensure duplicates of a key are removed from a map
        replaceDuplicatesTest(nodeInt(10), nodeTrue, nodeNull)
        replaceDuplicatesTest(emptyMapNode, emptyMapNode, emptyMapNode)
        replaceDuplicatesTest(emptyArrayNode, emptyArrayNode, emptyArrayNode)
        replaceDuplicatesTest(nodeInt(10), emptyMapNode, emptyArrayNode)
    }

    @Test
    def addNonExistentPaths() {
        nonExistentPathTest(nodeInt(10))
        nonExistentPathTest(configNodeComplexValue(List(nodeOpenBracket, nodeInt(15), nodeCloseBracket)))
        nonExistentPathTest(configNodeComplexValue(List(nodeOpenBrace, nodeKeyValuePair(configNodeKey("foo"), nodeDouble(3.14), nodeSpace))))
    }

    @Test
    def replaceNestedNodes() {
        // Test that all features of node replacement in a map work in a complex map containing nested maps
        val origText = "foo : bar\nbaz : {\n\t\"abc.def\" : 123\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
          "def : \"this is a string\"\n\t\tghi : ${\"a.b\"}\n\t}\n}\nbaz.abc.ghi : 52\nbaz.abc.ghi : 53\n}"
        val lowestLevelMap = configNodeComplexValue(List(nodeOpenBrace, nodeLine(6),
                                                         nodeKeyValuePair(nodeWhitespace("\t\t"), configNodeKey("def"), configNodeSimpleValue(tokenString("this is a string")), nodeLine(7)),
                                                         nodeKeyValuePair(nodeWhitespace("\t\t"), configNodeKey("ghi"), configNodeSimpleValue(tokenKeySubstitution("a.b")), nodeLine(8)),
                                                         nodeWhitespace("\t"), nodeCloseBrace))
        val higherLevelMap = configNodeComplexValue(List(nodeOpenBrace, nodeLine(2),
                                                         nodeKeyValuePair(nodeWhitespace("\t"), configNodeKey("\"abc.def\""), configNodeSimpleValue(tokenInt(123)), nodeLine(3)),
                                                         nodeWhitespace("\t"), configNodeBasic(tokenCommentDoubleSlash("This is a comment about the below setting")),
                                                         nodeLine(4), nodeLine(5),
                                                         nodeKeyValuePair(nodeWhitespace("\t"), configNodeKey("abc"), lowestLevelMap, nodeLine(9)), nodeWhitespace(""),
                                                         nodeCloseBrace))
        val origNode =  configNodeComplexValue(List(nodeKeyValuePair(configNodeKey("foo"), configNodeSimpleValue(tokenUnquoted("bar")), nodeLine(1)),
                                                    nodeKeyValuePair(configNodeKey("baz"), higherLevelMap, nodeLine(10)),
                                                    nodeKeyValuePair(configNodeKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(52)), nodeLine(11)),
                                                    nodeKeyValuePair(configNodeKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(53)), nodeLine(12)),
                                                    nodeCloseBrace))
        assertEquals(origText, origNode.render())
        val finalText = "foo : bar\nbaz : {\n\t\"abc.def\" : true\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
          "def : false\n\t}\n}\nbaz.abc.ghi : randomunquotedString\n}\nbaz.abc.\"this.does.not.exist@@@+$#\".end : doesnotexist\n"

        //Can replace settings in nested maps
        // Paths with quotes in the name are treated as a single Path, rather than multiple sub-paths
        var newNode = origNode.setValueOnPath("baz.\"abc.def\"", configNodeSimpleValue(tokenTrue))
        newNode = newNode.setValueOnPath("baz.abc.def", configNodeSimpleValue(tokenFalse))

        // Repeats are removed from nested maps
        newNode = newNode.setValueOnPath("baz.abc.ghi", configNodeSimpleValue(tokenUnquoted("randomunquotedString")))

        // Missing paths are added to the top level if they don't appear anywhere, including in nested maps
        newNode = newNode.setValueOnPath("baz.abc.\"this.does.not.exist@@@+$#\".end", configNodeSimpleValue(tokenUnquoted("doesnotexist")))

        // The above operations cause the resultant map to be rendered properly
        assertEquals(finalText, newNode.render())
    }

}
