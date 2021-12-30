package com.browserup.bup.proxy.assertion.field.header.mostrecent

import com.browserup.bup.proxy.assertion.field.header.HeaderBaseTest
import org.junit.Test

import java.util.regex.Pattern

class HeaderContainsTest extends HeaderBaseTest {
    private static final Pattern URL_PATTERN = Pattern.compile(".*${URL_PATH}.*")

    @Test
    void anyNameAndMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, HEADER_VALUE)

        assertAssertionPassed(result)
    }

    @Test
    void anyNameIfEmptyNameProvidedAndMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, null, HEADER_VALUE)

        assertAssertionPassed(result)

        result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, '', HEADER_VALUE)

        assertAssertionPassed(result)
    }


    @Test
    void matchingNameAndMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, HEADER_NAME, HEADER_VALUE)

        assertAssertionPassed(result)
    }

    @Test
    void matchingNameAndNotMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, HEADER_NAME, NOT_MATCHING_HEADER_VALUE)

        assertAssertionFailed(result)
    }

    @Test
    void notMatchingNameAndMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, NOT_MATCHING_HEADER_NAME, HEADER_VALUE)

        assertAssertionFailed(result)
    }

    @Test
    void notMatchingNameAndNotMatchingValue() {
        requestToMockedServer(URL_PATH)

        def result = proxy.assertMostRecentResponseHeaderContains(URL_PATTERN, NOT_MATCHING_HEADER_NAME, NOT_MATCHING_HEADER_VALUE)

        assertAssertionFailed(result)
    }
}
