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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URI;

import org.apache.jena.iri.IRI;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** Detailed testing IPv6 parsing is in {@link TestAddressIPv6} */ 
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestRFC3986 {
    // Assumes full authority parsing and not scheme-specific checks.
    
    // ---- Compare to jena-iri
    // parser - and compare with jena-iri-full (the original jena-iri)
    // jena-iri does not allow userinfo in http URIs (removed by HTTP RFC 2616 and later).
    @Test public void parse_00() { good("http://host"); }

    @Test public void parse_01() { good("http://host:8081/abc/def?qs=ghi#jkl"); }

    @Test public void parse_02() { good("http://[::1]:8080/abc/def?qs=ghi#jkl"); }

    // jena-iri does not allow %XX in the authority. It originated pre-3986 and does not seem to have updated completely.
    // %XX in host added at RFC 3986.
    @Test public void parse_03() { goodNoIRICheck("http://ab%AAdef:80/xyzβ/abc"); }

    @Test public void parse_04() { good("/abcdef"); }

    @Test public void parse_05() { good("/ab%FFdef"); }

    // jena-iri mandates upper case.  The RFC 3986 grammar only has upper case but the normalization section discusses lower case.
    // Unclear status.
    @Test public void parse_06() { goodNoIRICheck("/ab%ffdef"); }

    @Test public void parse_07() { good("http://host/abcdef?qs=foo#frag"); }

    @Test public void parse_08() { good(""); }

    @Test public void parse_09() { good("."); }

    @Test public void parse_10() { good(".."); }

    @Test public void parse_11() { good("//host:8081/abc/def?qs=ghi#jkl"); }

    @Test public void parse_12() { goodNoIRICheck("a+.-9://h/"); }

    @Test public void parse_13() { 
        good("http://host");
    }

    @Test public void parse_file_01() { good("file:///file/name.txt"); }

    @Test public void parse_urn_01() { good("urn:x-local:abc/def"); }

    // rq-components = [ "?+" r-component ]
    //                 [ "?=" q-component ]
    
    @Test public void parse_urn_02()        { good("urn:x-local:abc/def?+more"); }

    @Test public void parse_urn_03()        { good("urn:x-local:abc/def?=123"); }

    @Test public void parse_ftp_01() { good("ftp://user@host:3333/abc/def?qs=ghi#jkl"); }

    @Test public void parse_ftp_02() { good("ftp://[::1]/abc/def?qs=ghi#jkl"); }

    @Test public void components_http_01() { 
        testComponents("http://user@host:8081/abc/def?qs=ghi#jkl", "http", "user@host:8081", "/abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_http_02() { 
        testComponents("http://host/abc/def?qs=ghi#jkl", "http", "host", "/abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_http_03() { 
        testComponents("http://host/abc/def#jkl", "http", "host", "/abc/def", null, "jkl");
    }

    @Test public void components_http_04() { 
        testComponents("http://host/abc/def?q=", "http", "host", "/abc/def", "q=", null);
    }

    @Test public void components_http_05() { 
        testComponents("http://host/abc/def#", "http", "host", "/abc/def", null, "");
    }
    
    @Test public void components_http_06() { 
        testComponents("http://host", "http", "host", "", null, null);
    }

    @Test public void components_some_01() { 
        testComponents("//host:8888/abc/def?qs=ghi#jkl", null, "host:8888", "/abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_some_02() { 
        testComponents("http:///abc/def?qs=ghi#jkl", "http", "", "/abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_some_03() { 
        testComponents("/abc/def?qs=ghi#jkl", null, null, "/abc/def", "qs=ghi", "jkl");
    }

    @Test public void components_some_04() { 
        testComponents("abc/def?qs=ghi#jkl", null, null, "abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_some_05() { 
        testComponents("http:abc/def?qs=ghi#jkl", "http", null, "abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_some_06() { 
        testComponents("abc/def", null, null, "abc/def", null, null);
    }
    
    @Test public void components_some_07() { 
        testComponents("abc/def?qs=ghi#jkl", null, null, "abc/def", "qs=ghi", "jkl");
    }
    
    @Test public void components_some_08() { 
        testComponents("http:", "http", null, "", null, null);
    }
    
    // Important cases.
    @Test public void components_cases_01() { 
        testComponents("", null, null, "", null, null);
    }

    @Test public void components_21() { 
        testComponents("#foo", null, null, "", null, "foo");
    }
    
    @Test public void components_22() { 
        testComponents(".", null, null, ".", null , null);
    }
    
    @Test public void components_23() { 
        testComponents("..", null, null, "..", null , null);
    }
    
    @Test public void components_24() { 
        testComponents("/", null, null, "/", null , null);
    }
    
    @Test public void components_25() { 
        testComponents("/..", null, null, "/..", null , null);
    }
    
    // URN
    @Test public void components_urn_1() {
        testComponents("urn:NID:NSS", "urn", null, "NID:NSS", null, null);
    }
    
    @Test public void components_urn_2() {
        testComponents("urn:local:abc/def?+more", "urn", null, "local:abc/def", "+more", null);
    }
    
    @Test public void components_urn_3() {
        testComponents("urn:local:abc/def?=more", "urn", null, "local:abc/def", "=more", null);
    }
    
    @Test public void components_urn_4() {
        testComponents("urn:local:abc/def#frag", "urn", null, "local:abc/def", null, "frag");
    }
    
    @Test public void components_urn_5() {
        testComponents("urn:local:abc/def#frag", "urn", null, "local:abc/def", null, "frag");
    }
    
    // file:
    @Test public void components_file_1() {
        testComponents("file:///path/file.txt", "file", "", "/path/file.txt", null, null);
    }
    
    // file:
    @Test public void components_file_2() {
        testComponents("file:/path/file.txt", "file", null, "/path/file.txt", null, null);
    }

    // ftp:
    @Test public void components_ftp_1() {
        testComponents("ftp://user@host:3333/abc/def?qs=ghi#jkl", "ftp", "user@host:3333", "/abc/def", "qs=ghi", "jkl");
    }
    
    // mailto:
    @Test public void components_mailto_1() {
        testComponents("mailto:support@example.com", "mailto", null, "support@example.com", null, null);
    }
    
    // ---- bad

    // Leading ':'
    @Test public void bad_scheme_1() { bad(":segment"); }

    // Bad scheme
    @Test public void bad_scheme_2() { bad("://host/xyz"); }

    // Bad scheme
    @Test public void bad_scheme_3() { bad("1://host/xyz"); }

    // Bad scheme
    @Test public void bad_scheme_4() { bad("a~b://host/xyz"); }
    
    // Bad scheme
    @Test public void bad_scheme_5() { bad("aβ://host/xyz"); }
    
    // Space!
    @Test public void bad_chars_1() { bad("http://abcdef:80/xyz /abc"); }

    // colons
    @Test public void bad_host_1() { bad("http://abcdef:80:/xyz"); }

    // Bad IPv6
    @Test public void bad_ipv6_1() { bad("http://[::80/xyz"); }

    // Bad IPv6
    @Test public void bad_ipv6_2() { bad("http://host]/xyz"); }

    // Bad IPv6
    @Test public void bad_ipv6_3() { bad("http://[]/xyz"); }

    // Multiple @
    @Test public void bad_authority_1() { bad("ftp://abc@def@host/abc"); }

    // Multiple colon in authority 
    @Test public void bad_authority_2() { bad("http://abc:def:80/abc"); }

    // Bad %-encoding.
    @Test public void bad_percent_1() { bad("/abc%ZZdef"); }

    @Test public void bad_percent_2() { bad("http://abc%ZZdef/"); }

    // Bad %-encoded
    @Test public void bad_percent_3() { bad("http://example/xyz%"); }
    
    // Bad %-encoded
    @Test public void bad_percent_4() { bad("http://example/xyz%A"); }
    
    // Bad %-encoded
    @Test public void bad_percent_5() { bad("http://example/xyz%A?"); }
    
    // [] not allowed.
    @Test public void bad_frag_1() { bad("http://eg.com/test.txt#xpointer(/unit[5])"); }
   
    // ---- bad by scheme.
    @Test public void parse_http_bad_01() { badSpecific("http://user@host:8081/abc/def?qs=ghi#jkl"); }

    //  urn:2char:1char
    // urn:NID:NSS where NID is at least 2 alphas, and at most 32 long 
    @Test public void parse_urn_bad_01() { badSpecific("urn:"); }
    @Test public void parse_urn_bad_02() { badSpecific("urn:x:abc"); }

    @Test public void parse_urn_bad_03() { badSpecific("urn:abc:"); }
    @Test public void parse_urn_bad_04() { badSpecific("urn:abc0:def"); }
    // 33 chars
    @Test public void parse_urn_bad_05() { badSpecific("urn:abcdefghij-123456789-123456789-yz:a"); }
    
    // Bad by URN specific rule for the query components.
    @Test public void parse_urn_bad_09()    { badSpecific("urn:local:abc/def?query=foo"); }
    
    

    @Test public void build_01() {
        testBuild("http://host/abc/def?qs=ghi#jkl", "http", "host", "/abc/def", "qs=ghi", "jkl");
    }

    @Test public void build_02() {
        testBuild("http://host/abc/def", "http", "host", "/abc/def", null, null);
    }

    @Test public void build_03() {
        IRI3986 iri = 
            RFC3986.create()
                .scheme("http")
                .authority("AUTH")
                .host("host")
                .path("/abc")
                .build();
        assertEquals("http://host/abc", iri.toString());
    }
    
    @Test public void build_04() {
        IRI3986 iri = 
            RFC3986.create()
                .scheme("http")
                .authority("AUTH")
                .host("host")
                .port(8080)
                .path("/abc")
                .build();
        assertEquals("http://host:8080/abc", iri.toString());
    }
    
    @Test
    public void normalize_01() {
        testNormalize("http://host/a/b/c?q=1#2", "http://host/a/b/c?q=1#2");
    }

    @Test
    public void normalize_02() {
        testNormalize("HtTp://host/a/b/c?q=1#2", "http://host/a/b/c?q=1#2");
    }

    @Test
    public void normalize_03() {
        testNormalize("HTTP://HOST/a/b/c?q=1#2", "http://host/a/b/c?q=1#2");
    }

    @Test
    public void normalize_04() {
        testNormalize("HTTP://HOST:/a/b/c?q=1#2", "http://host/a/b/c?q=1#2");
    }

    @Test
    public void normalize_05() {
        testNormalize("HTTP://HOST:80/a/b/c?q=1#2", "http://host/a/b/c?q=1#2");
    }

    @Test
    public void normalize_06() {
        testNormalize("HTTPs://HOST:443/a/b/c?q=1#2", "https://host/a/b/c?q=1#2");
    }

    @Test
    public void resolve_01() {
        testResolve("http://example/dir/", "", "http://example/dir/");
    }

    @Test
    public void resolve_02() {
        testResolve("http://example/dir/", "A", "http://example/dir/A");
    }

    @Test
    public void resolve_03() {
        testResolve("http://example/dir", "A", "http://example/A");
    }

    @Test
    public void resolve_04() {
        testResolve("http://example/dir", "A/", "http://example/A/");
    }

    @Test
    public void resolve_05() {
        testResolve("http://example/dir1/dir2/dir3/dir4", "..", "http://example/dir1/dir2/");
    }

    @Test
    public void resolve_06() {
        testResolve("http://example/dir1/dir2/", "..", "http://example/dir1/");
    }

    @Test
    public void resolve_07() {
        testResolve("http://example/dir1/dir2", "..", "http://example/");
    }
    @Test
    public void resolve_08() {
        testResolve("http://example/dir1/dir2/f3", "..", "http://example/dir1/");
    }


    @Test
    public void resolve_09() {
        testResolve("http://example/dir1/dir2/", "../a", "http://example/dir1/a");
    }

    @Test
    public void resolve_10() {
        testResolve("http://example/dir1/dir2/f3", "../a", "http://example/dir1/a");
    }

    @Test
    public void resolve_11() {
        testResolve("http://example/dir1/f2", "../a", "http://example/a");
    }

    @Test
    public void resolve_12() {
        testResolve("http://example/dir1/dir2/", "..", "http://example/dir1/");
    }

    @Test
    public void resolve_13() {
        testResolve("http://example/dir/", "..", "http://example/");
    }

    @Test
    public void resolve_14() {
        testResolve("http://example/dir", "..", "http://example/");
    }

    @Test
    public void resolve_15() {
        testResolve("http://example/", "..", "http://example/");
    }

    @Test
    public void resolve_16() {
        testResolve("http://example", "..", "http://example/");
    }
    
    @Test
    public void resolve_17() {
        testResolve("http://example/path?query#frag", "http://host", "http://host");
    }
    
    @Test
    public void resolve_20() {
        testResolve("http://example/dir/file", ".", "http://example/dir/");
    }
    
    @Test
    public void resolve_21() {
        testResolve("http://example/dir/", ".", "http://example/dir/");
    }
    
    @Test
    public void resolve_22() {
        testResolve("http://example/dir1/dir2/", ".", "http://example/dir1/dir2/");
    }
    
    @Test
    public void resolve_23() {
        testResolve("http://example/", ".", "http://example/");
    }
    
    @Test
    public void resolve_24() {
        testResolve("http://example/#fragment", "path?q=arg", "http://example/path?q=arg");
    }
    
    @Test
    public void resolve_25() {
        testResolve("http://example/", "../path?q=arg", "http://example/path?q=arg");
    }
    
    @Test
    public void resolve_26() {
        testResolve("http://example/?query", "../path?q=arg", "http://example/path?q=arg");
    }
    
    @Test
    public void resolve_27() {
        testResolve("http://example/?query", "../path?q=arg", "http://example/path?q=arg");
    }

    @Test
    public void resolve_28() {
        testResolve("http://example/path", "?query", "http://example/path?query");
    }

    @Test
    public void resolve_29() {
        testResolve("http://example/path", "#frag", "http://example/path#frag");
    }

    @Test
    public void resolve_30() {
        testResolve("http://example/path", "..#frag", "http://example/#frag");
    }

    @Test
    public void resolve_31() {
        testResolve("http://example/path#fragment", "..#frag", "http://example/#frag");
    }
    
    private void testNormalize(String input, String expected) {
        IRI3986 iri = RFC3986.create(input);
        IRI3986 iri2 = iri.normalize();
        String s = iri2.toString();
        assertEquals(expected, s);
    }

    
    private void testResolve(String base, String rel, String expected) {
        IRI3986 baseiri = IRI3986.create(base);
        IRI3986 iri = IRI3986.create(rel);
        IRI3986 iri2 = iri.resolve(baseiri);
        String s1 = iri2.toString();
        assertEquals(expected, s1);
        // Check the expected with jena-iri.
        IRI baseJenaIRI = IRIResolvers.iriFactory().create(base);
        IRI resolvedJenaIRI = baseJenaIRI.resolve(rel);
        
        String s2 = resolvedJenaIRI.toString();
        assertEquals(expected, s2);
    }

    private void testBuild(String expected, String scheme, String authority, String path, String query, String fragment) {
        IRI3986 iri = 
            RFC3986.create()
                .scheme(scheme)
                .authority(authority)
                .path(path)
                .query(query)
                .fragment(fragment)
                .build();
        assertEquals(expected, iri.toString());
    }

    private void testComponents(String string, String scheme, String authority, String path, String query, String fragment) {
        IRI3986 iri = RFC3986.create(string);
        assertEquals("scheme",      scheme,     iri.getScheme());
        assertEquals("authority",   authority,  iri.getAuthority());
        assertEquals("path",        path,       iri.getPath());
        assertEquals("query",       query,      iri.getQuery());
        assertEquals("fragment",    fragment,   iri.getFragment());
    }
    
    private void good(String string) {
        RFC3986.check(string);
        IRI3986 iri = RFC3986.create(string);
        if ( true ) {
            IRI iri1 = IRIResolvers.iriFactory().create(string);
            if ( iri1.hasViolation(false) ) {
                //iri1.violations(false).forEachRemaining(v-> System.err.println("IRI = "+string + " :: "+v.getLongMessage()));
                fail("Violations "+string);
            }
        }
        iri.checkSchemeSpecificRules();
        URI javaURI = URI.create(string);
        assertEquals(string, iri.rebuild());
    }

    private void goodNoIRICheck(String string) {
        RFC3986.check(string);
        IRI3986 iri = RFC3986.create(string);
        URI javaURI = URI.create(string);
    }

    
    private void bad(String string) {
        try { RFC3986.check(string); }
        catch (IRIParseException ex) {}
    }
    
    private void badSpecific(String string) {
        RFC3986.check(string);
        try { 
            RFC3986.create(string)
                .checkSchemeSpecificRules();
            fail("Expected a parse exception: '"+string+"'"); 
        } catch (IRIParseException ex) {}
    }
}