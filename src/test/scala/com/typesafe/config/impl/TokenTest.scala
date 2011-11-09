package com.typesafe.config.impl

import org.junit.Assert._
import org.junit._

class TokenTest extends TestUtils {

    @Test
    def tokenEquality() {
        checkEqualObjects(Tokens.START, Tokens.START)
        checkNotEqualObjects(Tokens.START, Tokens.OPEN_CURLY)

        checkEqualObjects(Tokens.newInt(fakeOrigin(), 42), Tokens.newInt(fakeOrigin(), 42))
        checkNotEqualObjects(Tokens.newInt(fakeOrigin(), 42), Tokens.newInt(fakeOrigin(), 43))
    }
}
