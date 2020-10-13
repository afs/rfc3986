/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seaborne.rfc3986;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.Predicate;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Other tests of IRIs : parse and run methods.
 * @see TestNormalize
 * @see TestResolve
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestRFC3986_Extra {

    @Test public void absolute1() {
        isTrue("http://example/foo", IRI3986::isAbsolute);
    }

    @Test public void absolute2() {
        isFalse("http://example/foo#a", IRI3986::isAbsolute);
    }

    @Test public void absolute3() {
        isFalse("example/foo#a", IRI3986::isAbsolute);
    }

    @Test public void query1() {
        isTrue("http://example/foo?query=bar", IRI3986::hasQuery);
    }

    @Test public void query2() {
        isTrue("http://example/foo?", IRI3986::hasQuery);
    }

    @Test public void query3() {
        isTrue("http://example/foo?query", IRI3986::hasQuery);
    }

    @Test public void query4() {
        isFalse("http://example/foo#query", IRI3986::hasQuery);
    }

    @Test public void query5() {
        isFalse("http://example/foo#", IRI3986::hasQuery);
    }

    @Test public void fragment1() {
        isTrue("http://example/foo#frag", IRI3986::hasFragment);
    }

    @Test public void fragment2() {
        isTrue("http://example/foo#", IRI3986::hasFragment);
    }

    @Test public void fragment3() {
        isTrue("foo#fragment", IRI3986::hasFragment);
    }

    @Test public void fragment4() {
        isTrue("#fragment", IRI3986::hasFragment);
    }

    @Test public void fragment5() {
        isFalse("foo", IRI3986::hasFragment);
    }

    // ----

    private static void isTrue(String iriStr, Predicate<IRI3986> testPredicate) {
        assertTrue(testPredicate.test(IRI3986.create(iriStr)));
    }

    private static void isFalse(String iriStr, Predicate<IRI3986> testPredicate) {
        assertFalse(testPredicate.test(IRI3986.create(iriStr)));
    }
}
