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

import static java.lang.String.format;
import static org.seaborne.rfc3986.ParseLib.*;
import static org.seaborne.rfc3986.SystemIRI3986.*;
import static org.seaborne.rfc3986.SystemIRI3986.Compliance.*;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Implementation of RFC 3986 (URI), RFC 3987 (IRI). As is common, these are referred to
 * as "3986" regardless, just as {@code java.net.URI} covers IRIs.
 *
 * This provides a fast checking operation which does not copy the various parts of the
 * IRI and which creates a single object. The cost of extracting and allocating strings
 * happen when the getter for the component is called.
 *
 * {@code java.net.URI}
 * parses and allocates and follows RFC 2396 with modifications (several of which are in
 * RFC 3986).
 *
 * See {@link RFC3986} for operations involving {@code IRI3986}.
 *
 * This package implements the algorithms specified in RFC 3986 operations for:
 * <ul>
 * <li>Checking a string matches the IRI grammar.
 * <li>Extracting components of an IRI
 * <li>Normalizing an IRI
 * <li>Resolving an IRI against a base IRI.
 * <li>Rebuilding an IRI from components.
 * </ul>
 *
 * Additions:
 * <ul>
 * <li>Scheme specific rules for Linked Data usage of HTTP IRIs and URNs.
 * </ul>
 * HTTP IRIs forbids the "user@" part which is strongly discouraged in IRIs and requires a host name if the "//" is present.<br/>
 * Some additional check for RFC 8141 for URNs are included such as being of the form {@code urn:NID:NSS}.
 *
 * Restrictions and limitations:
 * <ul>
 * <li>No normal form C checking when checking (currently) See {@link Normalizer#isNormalized(CharSequence, java.text.Normalizer.Form)}.
 * </ul>
 *
 * Usage:<br/>
 * Check conformance with the RFC 3986 grammar:
 * <pre>
 * RFC3986.check(string);
 * </pre>
 * Check conformance with the RFC 3986 grammar and any applicable scheme specific rules:
 * <pre>
 * RFC3986.check(string, true);
 * </pre>
 * Validate and extract the components of IRI:
 * <pre>
 *     IRI3986 iri = RFC3986.create(string);
 *     iri.getPath();
 *     ...
 * </pre>
 * Resolve:
 * <pre>
 *     IRI3986 base = ...
 *     RFC3986 iri = RFC3986.create(string);
 *     IRI3986 iri2 = iri.resolve(base);
 * </pre>
 * Normalize:
 * <pre>
 *     RFC3986 base = ...
 *     IRI3986 iri = RFC3986.create(string);
 *     IRI3986 iri2 = iri.normalize();
 * </pre>
 */
public class IRI3986 {
    // Grammars at the end of the file.

    // RFC 3986, RFC 3987 and the definition of ABNF names (RFC 5234)
    /**
     * Determine if the string conforms to the IRI syntax. If not, it throws an exception.
     * This operation checks the string against the RFC3986/7 grammar; it does not apply
     * scheme specific rules
     */
    /*package*/ static void check(String iristr) {
        check(iristr, false);
    }

    /**
     * Determine if the string conforms to the IRI syntax. If not, it throws an exception.
     * This operation optionally also applies some scheme specific rules.
     */
    /*package*/ static void check(String iristr, boolean applySchemeSpecificRules) {
        IRI3986 iri = new IRI3986(iristr).process();
        if ( applySchemeSpecificRules )
            iri.schemeSpecificRules();
    }

    /**
     * Determine if the string conforms to the IRI syntax.
     * If not, it throws an exception.
     * This operations does not check the resulting IRI conforms to
     * URI scheme specific rules-- see {@link #schemeSpecificRules()}.
     */
    public static IRI3986 create(String iristr) {
        IRI3986 iri = new IRI3986(iristr).process();
        return iri;
    }

    // Always set,
    private final String iriStr;
    private final int length;

    // Offsets of parsed components, together with cached value.
    // The value is not calculated until first used, so that pure checking
    // not need to create any extra objects.
    private int scheme0 = -1;
    private int scheme1 = -1;
    private String scheme = null;

    private int authority0 = -1;
    private int authority1 = -1;
    private String authority = null;

    private int userinfo0 = -1;
    private int userinfo1 = -1;
    private String userinfo = null;

    private int host0 = -1;
    private int host1 = -1;
    private String host = null;

    private int port0 = -1;
    private int port1 = -1;
    private String port = null;

    private int path0 = -1;
    private int path1 = -1;
    private String path = null;

    private int query0 = -1;
    private int query1 = -1;
    private String query = null;

    private int fragment0 = -1;
    private int fragment1 = -1;
    private String fragment = null;
    /*package*/ IRI3986(String iriStr) {
        this.iriStr = iriStr;
        this.length = iriStr.length();
    }

    /** The IRI in string form. This is guaranteed to parse to an equals IRI. */
    public final String str() {
        if ( iriStr != null )
            return iriStr;
        return rebuild();
    }

    /** Human-readable appearance. Use {@link #str()} to a string to use in code. */
    @Override
    public String toString() {
        // Human readable form - may be overridden.
        return str();
    }

    /** Parse (i.e. check) or create an IRI object. */
    /*package*/ IRI3986 process() {
        int x = scheme(0);
        if ( x > 0 ) {
            // URI           = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
            // absolute-URI  = scheme ":" hier-part [ "?" query ]
            scheme0 = 0;
            scheme1 = x;
            // and move over ':'
            x = withScheme(x+1);
        } else {
            // relative-ref  = relative-part [ "?" query ] [ "#" fragment ]
            x = withoutScheme(0);
        }

        // Did the process consume the whole string?
        if ( x != length ) {
            String label;
            if ( fragment0 != 0)
                label = "fragment";
            else if ( query0 != 0)
                label = "query";
            else
                label = "path";
            //System.err.printf("(x3=%d, length=%d)\n", x, length);
            parseError("Bad character in "+label+" component: "+displayChar(charAt(x)));
        }
        return this;
    }

    /** Don't make the parts during parsing but wait until needed, if at all */
    private static String part(String str, int start, int finish) {
        if ( start >= 0 ) {
            if ( finish > str.length() ) {
                // Safety.
                return str.substring(start);
            }
            return str.substring(start, finish);
        }
        return null;
    }

    public boolean hasScheme() { return scheme0 != -1 ; }
    public String getScheme() {
        if ( hasScheme() && scheme == null)
            scheme = part(iriStr, scheme0, scheme1);
        return scheme;
    }

    public boolean hasAuthority() { return authority0 != -1 ; }
    public String getAuthority() {
        if ( hasAuthority() && authority == null)
            authority = part(iriStr, authority0, authority1);
        return authority;
    }

    public boolean hasUserInfo() { return userinfo0 != -1 ; }
    public String getUserInfo() {
        if ( hasUserInfo() && userinfo == null)
            userinfo = part(iriStr, userinfo0, userinfo1);
        return userinfo;
    }

    public boolean hasHost() { return host0 != -1 ; }
    public String getHost() {
        if ( hasHost() && host == null)
            host = part(iriStr, host0, host1);
        return host;
    }

    public boolean hasPort() { return port0 != -1 ; }
    public String getPort() {
        if ( hasPort() && port == null)
            port = part(iriStr, port0, port1);
        return port;
    }

    // Should this be "true" because getPath returns at least ""?
    // not path0 != -1 ; }/
    // Rule path-abempty (or path-empty ) is "".
    public boolean hasPath() {
        // Not "path0 != -1"
        // Rule path-abempty (or path-empty ) is "".
        // There is alwas a path, it may be "".
        return true;
    }

    public String getPath() {
        if ( hasPath() && path == null)
            path = part(iriStr, path0, path1);
        if ( path == null )
            return "";
        return path;
    }

    public String[] getPathSegments() {
        String x = getPath();
        if ( x == null )
            return null;
        return x.split("/");
    }

    public boolean hasQuery() { return query0 != -1 ; }
    public String getQuery() {
        if ( hasQuery() && query == null)
            query = part(iriStr, query0, query1);
        return query;
    }

    public boolean hasFragment() { return fragment0 != -1 ; }
    public String getFragment() {
        if ( hasFragment() && fragment == null)
            fragment = part(iriStr, fragment0, fragment1);
        return fragment;
    }

    /** <a href="https://tools.ietf.org/html/rfc3986#section-4.3">RFC 3986, Section 4.3</a> */
    public boolean isAbsolute() {
        // With scheme, without fragment
        return hasScheme() && ! hasFragment();
    }

    /** <a href="https://tools.ietf.org/html/rfc3986#section-4.2">RFC 3986, Section 4.2</a> */
    public boolean isRelative() {
        // No scheme.
        // This is not "! isAbsolute()"
        return ! hasScheme();
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc3986#section-3">RFC 3986, Section 3</a>.
     * IRI has a scheme, no authority (no //) and is path-rootless (does not start with /)
     * e.g. URN's.
     */
    public boolean isRootless() {
        return hasScheme() && !hasAuthority() && rootlessPath();
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc3986#section-1.2.3">RFC 3986, Section 1.2.3 : Hierarchical Identifiers</a>.
     */
    public boolean isHierarchical() {
        return hasScheme() && hasAuthority() && hierarchicalPath();
    }

    private boolean hierarchicalPath() {
        return hasPath() && charAt(path0) == '/';
    }

    private boolean rootlessPath() {
        return hasPath() && charAt(path0) != '/';
    }

    // Scheme names, include ':' as the terminator.
    private static char[] HTTPchars     = "http:".toCharArray();
    private static char[] HTTPSchars    = "https:".toCharArray();
    private static char[] URNchars      = "urn:".toCharArray();
    private static char[] URN_UUIDchars = "urn:uuid:".toCharArray();
    // It's not officially registered but may be found in the wild.
    private static char[] UUIDchars     = "uuid:".toCharArray();
    private static char[] FILEchars     = "file:".toCharArray();

    private boolean isScheme(char[] schemeChars) { return containsAtIgnoreCase(iriStr, 0, schemeChars); }

    /**
     * Apply scheme specific rules.
     * Return 'this' if there are no violations.
     */
    public IRI3986 schemeSpecificRules() {
        // No internal objects created.
        if ( ! hasScheme() )
            // no scheme, no checks.
            return this;
        // Avoid any object creation and also be case insensitive.
        if ( isScheme(HTTPchars) || isScheme(HTTPSchars) )
            checkHTTPx();
        else if ( isScheme(FILEchars) )
            checkFILE();
        else if ( isScheme(URN_UUIDchars) )
            checkUUID(URN_UUIDchars);
        else if ( isScheme(URNchars) )
            checkURN();
        else if ( isScheme(UUIDchars) )
            checkUUID(UUIDchars);
        return this;
    }

    /** Encode RFC 3987 (IRI) as strict 3986 (URI) using %-encoding */
    public IRI3986 as3986() {
        // The URI is valid so we just need to encode non-ASCII characters.
        for ( int i = 0 ; i < iriStr.length() ; i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch > 0x7F)
                return encode();
        }
        return this;
    }

    // The encoding work.
    private IRI3986 encode() {
        StringBuilder sb = new StringBuilder(iriStr.length()+20);
        for ( int i = 0 ; i < iriStr.length() ; i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch <= 0x7F)
                sb.append(ch);
            else
                ParseLib.encodeAsHex(sb, '%', ch);
        }
        String s = sb.toString();
        return new IRI3986(s);
    }

    /** Normalize : 3986 section 6.2.2.  Syntax-Based Normalization */
    public IRI3986 normalize() {
        // Consider - a pass to c
        String scheme = getScheme();
        String authority = getAuthority();
        String path = getPath();
        String query = getQuery();
        String fragment = getFragment();

//        6.2.2.  Syntax-Based Normalization
//
//        Implementations may use logic based on the definitions provided by
//        this specification to reduce the probability of false negatives.
//        This processing is moderately higher in cost than character-for-
//        character string comparison.  For example, an application using this
//        approach could reasonably consider the following two URIs equivalent:
//
//           example://a/b/c/%7Bfoo%7D
//           eXAMPLE://a/./b/../b/%63/%7bfoo%7d
//
//        Web user agents, such as browsers, typically apply this type of URI
//        normalization when determining whether a cached response is
//        available.  Syntax-based normalization includes such techniques as
//        case normalization, percent-encoding normalization, and removal of
//        dot-segments.
//
//     6.2.2.1.  Case Normalization

        scheme = toLowerCase(scheme);
        authority = toLowerCase(authority);

//     6.2.2.2.  Percent-Encoding Normalization
//
//        The percent-encoding mechanism (Section 2.1) is a frequent source of
//        variance among otherwise identical URIs.  In addition to the case
//        normalization issue noted above, some URI producers percent-encode
//        octets that do not require percent-encoding, resulting in URIs that
//        are equivalent to their non-encoded counterparts.  These URIs should
//        be normalized by decoding any percent-encoded octet that corresponds
//        to an unreserved character, as described in Section 2.3.

        // percent to upper case.
        // percent encoding - to unreserved
        // Occurs in authority, path, query and fragment.
        authority = normalizePercent(authority);
        path =      normalizePercent(path);
        query =     normalizePercent(query);
        fragment =  normalizePercent(fragment);

//     6.2.2.3.  Path Segment Normalization

        if ( path != null )
            path = remove_dot_segments(path);
        if ( path == null || path.isEmpty() )
            path = "/";

//     6.2.3.  Scheme-Based Normalization

        // HTTP and :80.
        // HTTPS and :443

        if ( authority != null && authority.endsWith(":") )
            authority = authority.substring(0, authority.length()-1);

        if ( Objects.equals("http", scheme) ) {
            if ( authority != null && authority.endsWith(":80") )
                authority = authority.substring(0, authority.length()-3);
        } else if ( Objects.equals("https", scheme) ) {
            if ( authority != null && authority.endsWith(":443") )
                authority = authority.substring(0, authority.length()-4);
        }

//     6.2.4.  Protocol-Based Normalization
        // None.

        // Rebuild.
        if ( Objects.equals(scheme, getScheme()) && Objects.equals(authority, getAuthority()) &&
             Objects.equals(path, getPath()) &&
             Objects.equals(query, getQuery()) && Objects.equals(fragment, getFragment()) ) {
            // No change and this has had all the elements calculated and substring done.
            return this;
        }

        String s = rebuild(scheme, authority, path, query, fragment);
        return new IRI3986(s).process();
    }

    private String normalizePercent(String str) {
        if ( str == null )
            return str;
        int idx = str.indexOf('%');
        if ( idx < 0 )
            return str;
        final int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for ( int i = 0 ; i < len ; i++ ) {
            char ch = str.charAt(i);
            if ( ! isPctEncoded(str, i) ) {
                sb.append(ch);
                continue;
            }
            char ch1 = toUpper(str.charAt(i+1));
            char ch2 = toUpper(str.charAt(i+2));
            i += 2;
            char x = (char)(hexValue(ch1)*16 + hexValue(ch2));

            if ( unreserved(x) ) {
                sb.append(x);
                continue;
            }
            sb.append('%');
            sb.append(ch1);
            sb.append(ch2);
        }
        return sb.toString();
    }

    private char toUpper(char ch) {
        if (ch >= 'a' && ch <= 'z')
            ch = (char)(ch + ('A'-'a'));
        return ch;
    }

    private String toLowerCase(String string) {
        if ( string == null )
            return null;
        return string.toLowerCase(Locale.ROOT);
    }

    /**
     * Return (if possible), an IRI that is relative to the base argument.
     * If this IRI is a relative path, this is returned unchanged.
     * <p>
     * The base must have a scheme, have no fragment and no query string.
     * <p>
     * If no relative IRI can be found, return null.
     */
    public IRI3986 relativize(IRI3986 iri) {
        // "this" is the base.
        Objects.requireNonNull(iri);
        if ( ! this.hasScheme() || this.hasQuery() )
            return null;
        if ( ! iri.hasScheme() && !iri.hasAuthority() )
            return null;
        if ( ! Objects.equals(iri.getScheme(), this.getScheme()) )
            return null;
        if ( ! Objects.equals(iri.getAuthority(), this.getAuthority()) )
            return null;
//        if ( ! Objects.equals(base.getHost(), this.getHost()) )
//            return null;
//        if ( ! Objects.equals(base.getPort(), this.getPort()) )
//            return null;

        String basePath = this.getPath();
        String targetPath = iri.getPath();

        if ( basePath.equals(targetPath) &&
                ( iri.hasFragment() || iri.hasQuery() ) ) {
            var relIRI = build(null, null, "", iri.getQuery(), iri.getFragment());
            return relIRI;
        }

        String relPath = relativePath(basePath, targetPath);

        if ( relPath == null )
            return null;
        var relIRI = build(null, null, relPath, iri.getQuery(), iri.getFragment());
        return relIRI;
    }

    /**
     * Calculate a relative path so that resolve("base", relative path) = "path". This is
     * limited to the case where basePath is a prefix of path segments for the path
     * to be made relative.
     * <p>
     * If basePath == path, return "".
     * <p>
     * It is "same document", "child" relative, and does not
     * return not "network" ("//host/a/c" or "/a/c" for same schema and host ) or
     * parent ("../a/b/c") relative IRIs.
     * <p>
     */
    private static String relativePath(String basePath, String path) {
        if ( basePath.equals(path) )
            return "";
        int idx = basePath.lastIndexOf('/');
        if ( idx < 0 )
            return null;
        // Include the "/"
        String basePrefix = basePath.substring(0,idx);
        if ( ! path.startsWith(basePrefix) )
            return null;
        String relPath = path.substring(idx+1);

        // Initial segment has ':' ? A ':' before first '/'
        int rColon = relPath.indexOf(':');
        if ( rColon < 0 )
            return relPath;
        int rPathSep = relPath.indexOf('/');
        if ( rPathSep < 0 || rColon < rPathSep )
            // and so rPathSep != 0.
            relPath = "./"+relPath;
        return relPath;
    }

    /** Resolve an IRI , using this as the base. <a href=https://tools.ietf.org/html/rfc3986#section-5">RFC 3986 section 5</a> */
    public IRI3986 resolve(IRI3986 other) {
        //if ( ! hasScheme()() ) {}
        // Base must have scheme. Be lax.
        return transformReferences(other, this);
    }

    /** 5.2.2.  Transform References */
    private static IRI3986 transformReferences(IRI3986 reference, IRI3986 base) {
        // Base should be absolute
        String t_scheme = null;
        String t_authority = "";
        String t_path = "";
        String t_query = null;
        String t_fragment = null;

//        -- The URI reference is parsed into the five URI components
//        --
//        (R.scheme, R.authority, R.path, R.query, R.fragment) = parse(R);
//
//        -- A non-strict parser may ignore a scheme in the reference
//        -- if it is identical to the base URI's scheme.
//        --
//        if ((not strict) and (R.scheme == Base.scheme)) then
//           undefine(R.scheme);
//        endif;
//
//        if defined(R.scheme) then
//           T.scheme    = R.scheme;
//           T.authority = R.authority;
//           T.path      = remove_dot_segments(R.path);
//           T.query     = R.query;
//        else
//           if defined(R.authority) then
//              T.authority = R.authority;
//              T.path      = remove_dot_segments(R.path);
//              T.query     = R.query;
//           else
//              if (R.path == "") then
//                 T.path = Base.path;
//                 if defined(R.query) then
//                    T.query = R.query;
//                 else
//                    T.query = Base.query;
//                 endif;
//              else
//                 if (R.path starts-with "/") then
//                    T.path = remove_dot_segments(R.path);
//                 else
//                    T.path = merge(Base.path, R.path);
//                    T.path = remove_dot_segments(T.path);
//                 endif;
//                 T.query = R.query;
//              endif;
//              T.authority = Base.authority;
//           endif;
//           T.scheme = Base.scheme;
//        endif;
//
//        T.fragment = R.fragment;

        // "not strict"
        boolean sameScheme = Objects.equals(reference.getScheme(), base.getScheme());

        if ( reference.hasScheme() && ! sameScheme ) {
            t_scheme = reference.getScheme();
            t_authority = reference.getAuthority();
            t_path = remove_dot_segments(reference.getPath());
            t_query = reference.getQuery();
        } else {
            if ( reference.hasAuthority() ) {
                t_authority = reference.getAuthority();
                t_path = remove_dot_segments(reference.getPath());
                t_query = reference.getQuery();
            } else {
                if ( reference.getPath().isEmpty() ) {
                    t_path = base.getPath();
                    if ( reference.hasQuery() )
                        t_query = reference.getQuery();
                    else
                        t_query = base.getQuery();
                } else {
                    if ( reference.getPath().startsWith("/") )
                        t_path = remove_dot_segments(reference.getPath());
                    else {
                        t_path = merge(base, reference.getPath());
                        t_path = remove_dot_segments(t_path);
                    }
                    t_query = reference.getQuery();
                }
                t_authority = base.getAuthority();
            }
            t_scheme = base.getScheme();
        }
        t_fragment = reference.getFragment();
        return RFC3986.create()
            .scheme(t_scheme).authority(t_authority).path(t_path).query(t_query).fragment(t_fragment)
            .build();
    }

    /** 5.2.3.  Merge Paths */
    private static String merge(IRI3986 base, String ref) {
/*
     o  If the base URI has a defined authority component and an empty
        path, then return a string consisting of "/" concatenated with the
        reference's path; otherwise,

     o  return a string consisting of the reference's path component
        appended to all but the last segment of the base URI's path (i.e.,
        excluding any characters after the right-most "/" in the base URI
        path, or excluding the entire base URI path if it does not contain
        any "/" characters).
*/
        if ( base.hasAuthority() && base.getPath().isEmpty() ) {
            if ( ref.startsWith("/") )
                return ref;
            return "/"+ref;
        }
        String path = base.getPath();
        int j = path.lastIndexOf('/');
        if ( j < 0 )
            return ref;
        return path.substring(0, j)+"/"+ref;
    }

    /** 5.2.4.  Remove Dot Segments */
    private static String remove_dot_segments(String path) {
        String s1 = remove_dot_segments$(path);
//        if ( false ) {
//            String s2 = jenaIRIremoveDotSegments(path);
//            if ( ! Objects.equals(s1, s2) )
//                System.err.printf("remove_dot_segments : %s %s\n", s1, s2);
//        }
        return s1;
    }

    /* Implement using segments. -- * 5.2.4.  Remove Dot Segments */
    private static String remove_dot_segments$(String path) {
        if ( path == null || path.isEmpty() )
            return "";
        if ( path.equals("/") )
            return "/";
        String[] segments = path.split("/");
//        if ( segments.length == 0 )
//            // Path is "/" - already done.
//            return "/";

        int N = segments.length;
        boolean initialSlash = segments[0].isEmpty();
        boolean trailingSlash = false;
        // Trailing slash if it isn't the initial "/" and it ends in "/" or "/." or "/.."
        if ( N > 1 ) {
            if ( segments[N-1].equals(".") || segments[N-1].equals("..") )
                trailingSlash = true;
            else if ( path.charAt(path.length()-1) == '/' )
                trailingSlash = true;
//            else if ( path.equals("..") )
//                trailingSlash = true;
        }

        for ( int j = 0 ; j < N ; j++ ) {
            String s = segments[j];
            if ( s.equals(".") )
                // Remove.
                segments[j] = null;
            if ( s.equals("..") ) {
                // Remove.
                segments[j] = null;
                // and remove previous
                if ( j >= 1 )
                    segments[j-1] = null;
            }
        }

        // Build string again. Skip nulls.
        StringJoiner joiner = new StringJoiner("/");
        if ( initialSlash )
            joiner.add("");
        for ( int k = 0 ; k < segments.length ; k++ ) {
            if ( segments[k] == null )
                continue;
            if ( segments[k].isEmpty() )
                continue;
            joiner.add(segments[k]);
        }
        if ( trailingSlash )
            joiner.add("");
        String s = joiner.toString();
        return s;
    }

//    // >> Copied from jena-iri to use for comparison.
//    static String jenaIRIremoveDotSegments(String path) {
//        // 5.2.4 step 1.
//        int inputBufferStart = 0;
//        int inputBufferEnd = path.length();
//        StringBuffer output = new StringBuffer();
//        // 5.2.4 step 2.
//        while (inputBufferStart < inputBufferEnd) {
//            String in = path.substring(inputBufferStart);
//            // 5.2.4 step 2A
//            if (in.startsWith("./")) {
//                inputBufferStart += 2;
//                continue;
//            }
//            if (in.startsWith("../")) {
//                inputBufferStart += 3;
//                continue;
//            }
//            // 5.2.4 2 B.
//            if (in.startsWith("/./")) {
//                inputBufferStart += 2;
//                continue;
//            }
//            if (in.equals("/.")) {
//                in = "/"; // don't continue, process below.
//                inputBufferStart += 2; // force end of loop
//            }
//            // 5.2.4 2 C.
//            if (in.startsWith("/../")) {
//                inputBufferStart += 3;
//                removeLastSeqment(output);
//                continue;
//            }
//            if (in.equals("/..")) {
//                in = "/"; // don't continue, process below.
//                inputBufferStart += 3; // force end of loop
//                removeLastSeqment(output);
//            }
//            // 5.2.4 2 D.
//            if (in.equals(".")) {
//                inputBufferStart += 1;
//                continue;
//            }
//            if (in.equals("..")) {
//                inputBufferStart += 2;
//                continue;
//            }
//            // 5.2.4 2 E.
//            int nextSlash = in.indexOf('/', 1);
//            if (nextSlash == -1)
//                nextSlash = in.length();
//            inputBufferStart += nextSlash;
//            output.append(in.substring(0, nextSlash));
//        }
//        // 5.2.4 3
//        return output.toString();
//    }
//
//    private static void removeLastSeqment(StringBuffer output) {
//        int ix = output.length();
//        while (ix > 0) {
//            ix--;
//            if (output.charAt(ix) == '/')
//                break;
//        }
//        output.setLength(ix);
//    }
//    // << Copied from jena-iri

    /** RFC 3986 : 5.3.  Component Recomposition */
    public String rebuild() {
        return rebuild(getScheme(), getAuthority(), getPath(), getQuery(), getFragment());
    }

    static IRI3986 build(String scheme, String authority, String path, String query, String fragment) {
        String s = rebuild(scheme, authority, path, query, fragment);
        return IRI3986.create(s);
    }

    private static String rebuild(String scheme, String authority, String path, String query, String fragment) {
        StringBuilder result = new StringBuilder();
        if ( scheme != null ) {
            result.append(scheme);
            result.append(":");
        }

        if ( authority != null ) {
            result.append("//");
            result.append(authority);
        }

        if ( path != null )
            result.append(path);

        if ( query != null ) {
            result.append("?");
            result.append(query);
        }

        if ( fragment != null ) {
            result.append("#");
            result.append(fragment);
        }
        return result.toString();
    }

    // URN specific.
    //   "urn", ASCII, min 2 char NID min two char NSS (urn:NID:NSS)
    //   Query string starts ?+ or ?=

    /*
        namestring    = assigned-name
            [ rq-components ]
            [ "#" f-component ]
        assigned-name = "urn" ":" NID ":" NSS
        NID           = (alphanum) 0*30(ldh) (alphanum)
        ldh           = alphanum / "-"
        NSS           = pchar *(pchar / "/")
        rq-components = [ "?+" r-component ]
                        [ "?=" q-component ]
        r-component   = pchar *( pchar / "/" / "?" )
        q-component   = pchar *( pchar / "/" / "?" )
        f-component   = fragment
     */
    // Without specifically testing for rq-components and "#" f-component
    // Strict - requires 2 char NID and one char NSS.
    private static Pattern URN_PATTERN_ASSIGNED_NAME_STRICT = Pattern.compile("^urn:[a-zA-Z0-9][-a-zA-Z0-9]{0,30}[a-zA-Z0-9]:.+");

    // More generous.
    // NID, can be one char and can be "X"-" (RFC 2141)
    // NSS, and it's colon can be absent. base names can be <urn:nid:> but not <urn:nid>
    private static Pattern URN_PATTERN_ASSIGNED_NAME_LOOSE = Pattern.compile("^urn:[a-zA-Z0-9][-a-zA-Z0-9]{0,31}:(.*)");

    private void checkURN() {
//        if ( URN_SCHEME == NOT_STRICT )
//            return;

        Pattern pattern = ( URN_SCHEME == STRICT ) ? URN_PATTERN_ASSIGNED_NAME_STRICT : URN_PATTERN_ASSIGNED_NAME_LOOSE;

        String scheme = getScheme();
        if ( ! scheme.equals("urn") )
            schemeError("urn", "scheme name is not lowercase 'urn'");
        // Matched: anchored. (find() is not).
        boolean matches = pattern.matcher(iriStr).matches();

        if ( !matches )
            schemeError("urn", "does not match the 'assigned-name' rule regular expression (\"urn\" \":\" NID \":\" NSS)");
        if ( hasQuery() ) {
            String qs = getQuery();
            if ( ! qs.startsWith("+") && ! qs.startsWith("=") )
                schemeError("urn", "improper start to query string.");
            urnCharCheck("query", qs);
        }

        if ( hasFragment() )
            urnCharCheck("fragment", getFragment());
    }

    private void urnCharCheck(String label, String string) {
        for ( int i = 0 ; i < string.length(); i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch > 0x7F)
                schemeError("urn", label+" : Non-ASCII character");
        }
    }

    private void checkHTTPx() {
        if ( ! hasAuthority() )
            schemeError("http", "http and https URI schemes require //");
        /*
         * https://tools.ietf.org/html/rfc7230#section-2.7.1
         *
         *   A sender MUST NOT generate an "http" URI with an empty host
         *   identifier.  A recipient that processes such a URI reference MUST
         *   reject it as invalid.
         */
        // See https://tools.ietf.org/html/rfc7230#section-2.7.1
        if ( hasHost() && (host0 == host1) ) //getHost().isEmpty()
            schemeError("http", "http and https URI schemes do not allow the host to be empty");

        /*
         * https://tools.ietf.org/html/rfc2616#section-3.2.2
         *
         *   http_URL = "http:" "//" host [ ":" port ] [ abs_path [ "?" query ]]
         *
         * https://tools.ietf.org/html/rfc7230#section-2.7.1
         *
         *   A sender MUST NOT
         *   generate the userinfo subcomponent (and its "@" delimiter) when an
         *   "http" URI reference is generated within a message as a request
         *   target or header field value.  Before making use of an "http" URI
         *   reference received from an untrusted source, a recipient SHOULD parse
         *   for userinfo and treat its presence as an error; it is likely being
         *  used to obscure the authority for the sake of phishing attacks.
         */

        if ( hasUserInfo() ) // getAuthority().contains("@") )
            schemeError("http", "userinfo (e.g. user:password) in authority section");
    }

    private void checkFILE() {
        // We only support file:// because file://path1/path2/ makes the host "path1" (which is then ignored!)
        if ( hasAuthority() && authority0 != authority1 ) {
            // file://path1/path2/..., so path becomes the "authority"
            schemeError("file", "file: URLs are of the form file:///path/...");
        }
    }

    /**
     *  Both "urn:uuid:" and the unofficial "uuid:"
     */
    private static Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private void checkUUID(char[] scheme) {
        int idx = scheme.length;
        int uuidLen = iriStr.length()-scheme.length;
        String uuidStr = iriStr.substring(idx);
        if ( uuidLen != 36 )
            schemeError(scheme, "Bad UUID string (wrong length): "+uuidStr);
        if ( hasQuery() )
            schemeError(scheme, "query component not allowed: "+iriStr);
        if ( hasFragment() )
            schemeError(scheme, "fragment not allowed: "+iriStr);

        boolean matches = UUID_PATTERN.matcher(uuidStr).matches();
        if ( !matches )
            schemeError(String.valueOf(scheme), "Not a valid UUID string: "+uuidStr);
    }

    // ---- Scheme

    //scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
    private int scheme(int start) {
        int p = start;
        int end = length;
        while (p < end) {
            char c = charAt(p);
            if ( c == ':' )
                return p;
            if ( ! isAlpha(c) ) {
                if ( p == start )
                    // Bad first character
                    return -1;
                if ( ! ( isDigit(c) || c == '+' || c == '-' || c == '.' ) )
                    // Bad subsequent character
                    return -1;
            }
            // alpha
            p++;
        }
        // Did not find ':'
        return 0;
    }

    private int withScheme(int start){
        // URI           = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
        // absolute-URI  = scheme ":" hier-part [ "?" query ]
        // hier-part     = "//" authority path-abempty
        //               / path-absolute
        //               / path-rootless
        //               / path-empty

        int p = maybeAuthority(start);
        return pathQueryFragment(p, true);
    }

    private int withoutScheme(int start) {
        // relative-ref  = relative-part [ "?" query ] [ "#" fragment ]
        // relative-part = "//" authority path-abempty
        //               / path-absolute
        //               / path-noscheme
        //               / path-empty
        //
        // Check not starting with ':' then path-noscheme is the same as path-rootless.
        char ch = charAt(start);
        if ( ch == ':' )
            parseError("A URI without a scheme can't start with a ':'");
        int p = maybeAuthority(start);
        return pathQueryFragment(p, false);
    }

    // ---- Authority

    private int maybeAuthority(int start) {
        // "//" authority
        int p = start;
        char ch1 = charAt(p);
        char ch2 = charAt(p+1);
        if ( ch1 == '/' && ch2 == '/' ) {
            p += 2;
            p = authority(p);
//            char ch3 = charAt(p);
//            if ( p != length && ch3 != '/' )
//                error("Bad path after authority: "+displayChar(ch3));
        }
        return p;
    }

    /*
     * authority     = [ userinfo "@" ] host [ ":" port ]
     * userinfo      = *( unreserved / pct-encoded / sub-delims / ":" )
     * host          = IP-literal / IPv4address / reg-name
     * port          = *DIGIT
     *
     * IP-literal    = "[" ( IPv6address / IPvFuture  ) "]"
     * IPvFuture     = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
     * IPv6address   = hex and ":", and "." for IPv4 in IPv6.
     * IPv4address   = dec-octet "." dec-octet "." dec-octet "." dec-octet
     *
     * reg-name      = *( unreserved / pct-encoded / sub-delims )
     *
     * So the section is only
     * unreserved / pct-encoded / sub-delims / ":" / "@" / "[" / "]".
     *   isPChar includes ":" / "@"
     *   unreserved has "."
     *
     * iauthority     = [ iuserinfo "@" ] ihost [ ":" port ]
     * iuserinfo      = *( iunreserved / pct-encoded / sub-delims / ":" )
     * ihost          = IP-literal / IPv4address / ireg-name
     *
     * There are further restrictions on DNS names.
     * RFC 5890, RFC 5891, RFC 5892, RFC 5893
     */
    private int authority(int start) {
        int end = length;
        int p = start;
        // Indexes for userinfo@host:port
        int endUserInfo = -1;
        int lastColon = -1;
        int countColon = 0;
        int startIPv6 = -1;
        int endIPv6 = -1;

        // Scan for whole authority then do some checking.
        // We need to know e.g. whether there is a userinfo section to check colons.
        while( p < end ) {
            char ch = charAt(p);
            if ( ch == ':' ) {
                countColon++;
                lastColon = p;
            } else if ( ch == '/' ) {
                // Normal exit
                if ( startIPv6 >= 0 && endIPv6 == -1)
                    parseError(p+1, "Bad IPv6 address - No closing ']'");
                break;
            } else if ( ch == '@' ) {
                if ( endUserInfo != -1 )
                    parseError(p+1, "Bad authority segment - multiple '@'");
                // Found userinfo end; reset counts and trackers.
                // Check for IPv6 []
                if ( startIPv6 != -1 || endIPv6 != -1 )
                    parseError(p+1, "Bad authority segment - contains '[' or ']'");
                endUserInfo = p;
                // Reset port colon tracking.
                countColon = 0;
                lastColon = -1;
            } else if ( ch == '[' ) {
                // Still to check whether user authority
                if ( startIPv6 >= 0 )
                    parseError(p+1, "Bad IPv6 address - multiple '['");
                startIPv6 = p;
            } else if ( ch == ']' ) {
                // Still to check whether user authority
                if ( startIPv6 == -1 )
                    parseError(p+1, "Bad IPv6 address - No '[' to match ']'");
                if ( endIPv6 >= 0 )
                    parseError(p+1, "Bad IPv6 address - multiple ']'");
                endIPv6 = p;
                // Reset port colon tracking.
                countColon = 0;
                lastColon = -1;
            } else if ( ! isIPChar(ch, p) ) {
                // All the characters in an (i)authority section, regardless of correct use.
                break;
            }
            p++;
        }

        if ( startIPv6 != -1 ) {
            if ( endIPv6 == -1 )
                parseError(startIPv6, "Bad IPv6 address - missing ']'");
            char ch1 = iriStr.charAt(startIPv6);
            char ch2 = iriStr.charAt(endIPv6);
            ParseIPv6Address.checkIPv6(iriStr, startIPv6, endIPv6+1);
        }

        // May not be valid but if tests fail there is an exception.
        authority0 = start;
        authority1 = p;
        int limit = p;

        if ( endUserInfo != -1 ) {
            userinfo0 = start;
            userinfo1 = endUserInfo;
            host0 = endUserInfo+1;
            if ( lastColon != -1 && lastColon < endUserInfo )
                // Not port, part of userinfo - ignore.
                lastColon = -1;
        } else {
            host0 = start;
        }

        // Check only one ":" in host.
        if ( countColon > 1 )
            parseError(-1, "Multiple ':' in host:port section");

        if ( lastColon != -1 ) {
            host1 = lastColon;
            port0 = lastColon+1;
            port1 = limit;
            int x = port0;
            // check digits in port.
            while( x < port1 ) {
                char ch = charAt(x);
                if ( ! isDigit(ch) )
                    break;
                x++;
            }
            if ( x != port1 )
                parseError(-1, "Bad port");
        } else
            host1 = limit;
//        String u = part(input, endUserInfo==-1?-1:start, endUserInfo);
//        String h = part(input, host0, host1);
//        String ps = part(input, port0, port1);
//        System.out.printf("Authority: |%s|%s|%s|\n", u,h,ps);

        // XXX Check DNS name.
        // IPv4 address or DNS name between host0 and host1.

        return limit;
    }

    // ---- hier-part :: /path?query#fragment

    private int pathQueryFragment(int start, boolean withScheme) {
        // hier-part [ "?" query ] [ "#" fragment ]
        // relative-ref  = relative-part [ "?" query ] [ "#" fragment ]

        // hier-part => path-abempty
        // relative-part = path-abempty
        //               / path-absolute
        //               / path-noscheme
        //               / path-empty
        // then [ "?" query ] [ "#" fragment ]

        int x1 = path(start, withScheme);

        if ( x1 < 0 ) {
            x1 = start;
        }

        int x2 = query(x1);
        if ( x2 < 0 ) {
            x2 = x1;
        }
        int x3 = fragment(x2);
        return x3;
    }

    // ---- Path
    // If not withScheme, then segment-nz-nc applies.
    private int path(int start, boolean withScheme) {
        // path          = path-abempty   ; begins with "/" or is empty
        //               / path-absolute  ; begins with "/" but not "//"
        //               / path-noscheme  ; begins with a non-colon segment
        //               / path-rootless  ; begins with a segment
        //               / path-empty     ; zero characters

        // path-abempty, path-absolute, path-rootless, path-empty
        //
        // path-abempty  = *( "/" segment )
        // path-absolute = "/" [ segment-nz *( "/" segment ) ]
        // path-noscheme = segment-nz-nc *( "/" segment )
        // path-rootless = segment-nz *( "/" segment )
        // path-empty    = 0<pchar>

        if ( start == length )
            return start;
        int segStart = start;
        int p = start;
        boolean allowColon = withScheme;

        while (p < length ) {
            // skip segment-nz    = 1*pchar
            char ch = charAt(p);
            if ( isIPChar(ch, p) ) {
                if ( ! allowColon && ch == ':' )
                    // segment-nz-nc
                    parseError(p+1, "':' in initial segment of a scheme-less IRI");
            } else {
                // End segment.
                allowColon = true;
                segStart = p+1;
                // Maybe new one.
                if ( ch != '/') {
                    if ( ch == ' ' )
                        parseError(p+1, "Space found in IRI");
                    // ? or # else error
                    if ( ch == '?' || ch == '#' )
                        break;
                    // Not IPChar
                    parseError(p+1, format("bad character in IRI path: "+ch+" (U+%04X)", (int)ch));
                }
            }
            p++;
        }

        if ( p > start ) {
            path0 = start;
            path1 = Math.min(p,length);
        }
        return p;
    }

    // ---- Query & Fragment

    private int query(int start) {
        // query      = *( pchar / "/" / "?" )
        // iquery     = *( ipchar / iprivate / "/" / "?" )
        int x = trailer('?', start, true);

        if ( x >= 0 && x != start ) {
            query0 = start+1;
            query1 = x;
        }
        if ( x < 0 )
            x = start;
        return x;
    }

    private int fragment(int start) {
        // fragment      = *( pchar / "/" / "?" )
        // ifragment     = *( ipchar / "/" / "?" )
        int x = trailer('#', start, false);
        if ( x >= 0 && x != start ) {
            fragment0 = start+1;
            fragment1 = x;
        }
        if ( x < 0 )
            x = start;
        return x;
    }

    private int trailer(char startChar, int start, boolean allowPrivate) {
        if ( start >= length )
            return -1;
        if ( charAt(start) != startChar )
            return -1;
        int p = start+1;
        while(p < length ) {
            char ch = charAt(p);
            //System.out.println("    char="+ch);
            if ( ! isIPChar(ch, p) && ch != '/' && ch != '?' ) {
                // 3986 -> 3987 extra test
                if ( ! allowPrivate )
                    // p is the index of the non-query/fragment char
                    return p;
                // query string allows iPrivate
                if ( ! isIPrivate(ch) )
                    return p;
            }
            // Character OK for trailer.
            p++;
        }
        return p; // = length if correct IRI.
    }

    /** String.charAt except with an EOF character, not an exception. */
    private char charAt(int x) {
        if ( x >= length )
            return EOF;
        return iriStr.charAt(x);
    }

    // ---- Character classification

    // Unicode - not a character
    private static final char EOF = ParseLib.EOF;

    // Is the character at location 'x' percent-encoded? Looks at next two characters if an only if ch is '%'
    private boolean isPctEncoded(char ch, int x) {
        if ( ch != '%' )
            return false;
        char ch1 = charAt(x+1);
        char ch2 = charAt(x+2);
        return percentCheck(x, ch1, ch2);
    }

    private static boolean isPctEncoded(String str, int x) {
        char ch = str.charAt(x);
        if ( ch != '%' )
            return false;
        char ch1 = str.charAt(x+1);
        char ch2 = str.charAt(x+2);
        return percentCheck(x, ch1, ch2);
    }

    private static boolean percentCheck(int idx, char ch1, char ch2) {
        if ( ch1 == EOF || ch2 == EOF )
            parseError(idx+1, "Incomplete %-encoded character");
        if ( isHexDigit(ch1) && isHexDigit(ch2) )
            return true;
        parseError(idx+1, "Bad %-encoded character ["+displayChar(ch1)+" "+displayChar(ch2)+"]");
        return false;
    }

    private static boolean isAlpha(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    // International alphabetic.  RFC 3987
    private static boolean isIAlpha(char ch) {
        return isAlpha(ch) || isUcsChar(ch);
    }

//    ucschar        = %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
//                   / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
//                   / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
//                   / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
//                   / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
//                   / %xD0000-DFFFD / %xE1000-EFFFD

    // Surrogates are "hi-lo" : DC000-DFFF and D800-DFFF
    // We assume the java string is valid and surrogates are correctly in high-low pairs.

    private static boolean isUcsChar(char ch) {
        return range(ch, 0xA0, 0xD7FF)  || range(ch, 0xF900, 0xFDCF)  || range(ch, 0xFDF0, 0xFFEF)
                // Allow surrogates.
                || Character.isSurrogate(ch);
            // Java is 16 bits chars.
//            || range(ch, 0x10000, 0x1FFFD) || range(ch, 0x20000, 0x2FFFD) || range(ch, 0x30000, 0x3FFFD)
//            || range(ch, 0x40000, 0x4FFFD) || range(ch, 0x50000, 0x5FFFD) || range(ch, 0x60000, 0x6FFFD)
//            || range(ch, 0x70000, 0x7FFFD) || range(ch, 0x80000, 0x8FFFD) || range(ch, 0x90000, 0x9FFFD)
//            || range(ch, 0xA0000, 0xAFFFD) || range(ch, 0xB0000, 0xBFFFD) || range(ch, 0xC0000, 0xCFFFD)
//            || range(ch, 0xD0000, 0xDFFFD) || range(ch, 0xE1000, 0xEFFFD)

    }

    // int version - includes support for beyond 16 bit chars.
    private static boolean int_isUcsChar(int ch) {
        // RFC 3987
        // ucschar    = %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
        //            / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
        //            / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
        //            / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
        //            / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
        //            / %xD0000-DFFFD / %xE1000-EFFFD
        boolean b = range(ch, 0xA0, 0xD7FF)  || range(ch, 0xF900, 0xFDCF)  || range(ch, 0xFDF0, 0xFFEF);
        if ( b )
            return true;
        if ( ch < 0x1000 )
            return false;
        // 32 bit checks.
        return
            range(ch, 0x10000, 0x1FFFD) || range(ch, 0x20000, 0x2FFFD) || range(ch, 0x30000, 0x3FFFD) ||
            range(ch, 0x40000, 0x4FFFD) || range(ch, 0x50000, 0x5FFFD) || range(ch, 0x60000, 0x6FFFD) ||
            range(ch, 0x70000, 0x7FFFD) || range(ch, 0x80000, 0x8FFFD) || range(ch, 0x90000, 0x9FFFD) ||
            range(ch, 0xA0000, 0xAFFFD) || range(ch, 0xB0000, 0xBFFFD) || range(ch, 0xC0000, 0xCFFFD) ||
            range(ch, 0xD0000, 0xDFFFD) || range(ch, 0xE1000, 0xEFFFD);
    }


    //iprivate       = %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD
    private static boolean isIPrivate(char ch) {
        return range(ch, 0xE000, 0xF8FF)
            // Java is 16 bits chars.
           ;
    }

    private static boolean int_isIPrivate(int ch) {
        return range(ch, 0xE000, 0xF8FF) || range(ch, 0xF0000, 0xFFFFD) || range(ch, 0x100000, 0X10FFFD);
    }

    private static boolean isDigit(char ch) {
        return (ch >= '0' && ch <= '9');
    }

//  pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
//  pct-encoded   = "%" HEXDIG HEXDIG
//
//  unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
//  iunreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" / ucschar
//  reserved      = gen-delims / sub-delims
//  gen-delims    = ":" / "/" / "?" / "#" / "[" / "]" / "@"
//  sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
//                / "*" / "+" / "," / ";" / "="

    private boolean isPChar(char ch, int posn) {
        return unreserved(ch) || isPctEncoded(ch, posn) || subDelims(ch) || ch == ':' || ch == '@';
    }

    /*package*/ static boolean unreserved(char ch) {
        if ( isAlpha(ch) || isDigit(ch) )
            return true;
        switch(ch) {
            // unreserved
            case '-': case '.': case '_': case '~': return true;
        }
        return false;
    }

    private static boolean iunreserved(char ch) {
        if ( isIAlpha(ch) || isDigit(ch) )
            return true;
        switch(ch) {
            // unreserved
            case '-': case '.': case '_': case '~': return true;
        }
        return false;
    }

    /*package*/ static boolean subDelims(char ch) {
        switch(ch) {
            case '!': case '$': case '&': case '\'': case '(': case ')':
            case '*': case '+': case ',': case ';': case '=': return true;
        }
        return false;
    }

    private static boolean genDelims(char ch) {
        switch(ch) {
            case ':': case '/': case '?': case '#': case '[': case ']': case '@': return true;
        }
        return false;
    }

    private boolean isIPChar(char ch, int posn) {
        return isPChar(ch, posn) || isUcsChar(ch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iriStr);
    }

    // hashCode and equals.
    // Slots like "authority" are caches and only set when their value is needed
    // in the associated getter.
    // The positions in the iriStr (authority0, authority1) are set.
    // An IRI3986 always as the iriStr set.

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( !(obj instanceof IRI3986) )
            return false;
        IRI3986 other = (IRI3986)obj;
        return Objects.equals(iriStr, other.iriStr);
    }
}
/* RDF 3986

   URI           = scheme ":" hier-part [ "?" query ] [ "#" fragment ]

   hier-part     = "//" authority path-abempty
                 / path-absolute
                 / path-rootless
                 / path-empty

   URI-reference = URI / relative-ref

   absolute-URI  = scheme ":" hier-part [ "?" query ]

   relative-ref  = relative-part [ "?" query ] [ "#" fragment ]

   relative-part = "//" authority path-abempty
                 / path-absolute
                 / path-noscheme
                 / path-empty

   scheme        = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )

   authority     = [ userinfo "@" ] host [ ":" port ]
   userinfo      = *( unreserved / pct-encoded / sub-delims / ":" )
   host          = IP-literal / IPv4address / reg-name
   port          = *DIGIT

   IP-literal    = "[" ( IPv6address / IPvFuture  ) "]"

   IPvFuture     = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )

   IPv6address   =                            6( h16 ":" ) ls32
                 /                       "::" 5( h16 ":" ) ls32
                 / [               h16 ] "::" 4( h16 ":" ) ls32
                 / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
                 / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
                 / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
                 / [ *4( h16 ":" ) h16 ] "::"              ls32
                 / [ *5( h16 ":" ) h16 ] "::"              h16
                 / [ *6( h16 ":" ) h16 ] "::"

   h16           = 1*4HEXDIG
   ls32          = ( h16 ":" h16 ) / IPv4address
   IPv4address   = dec-octet "." dec-octet "." dec-octet "." dec-octet

   dec-octet     = DIGIT                ; 0-9
                 / %x31-39 DIGIT        ; 10-99
                 / "1" 2DIGIT           ; 100-199
                 / "2" %x30-34 DIGIT    ; 200-249
                 / "25" %x30-35         ; 250-255

   reg-name      = *( unreserved / pct-encoded / sub-delims )

   path          = path-abempty   ; begins with "/" or is empty
                 / path-absolute  ; begins with "/" but not "//"
                 / path-noscheme  ; begins with a non-colon segment
                 / path-rootless  ; begins with a segment
                 / path-empty     ; zero characters

   path-abempty  = *( "/" segment )
   path-absolute = "/" [ segment-nz *( "/" segment ) ]
   path-noscheme = segment-nz-nc *( "/" segment )
   path-rootless = segment-nz *( "/" segment )
   path-empty    = 0<pchar>

   segment       = *pchar
   segment-nz    = 1*pchar
   segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
                ; non-zero-length segment without any colon ":"

   pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"

   query         = *( pchar / "/" / "?" )

   fragment      = *( pchar / "/" / "?" )

   pct-encoded   = "%" HEXDIG HEXDIG

   unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
   reserved      = gen-delims / sub-delims
   gen-delims    = ":" / "/" / "?" / "#" / "[" / "]" / "@"
   sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
                 / "*" / "+" / "," / ";" / "="
                 / "*" / "+" / "," / ";" / "="

  RFC 3897 : IRIs
----
    NB "unreserved" used in
    IPvFuture      = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
----

   ipchar         = iunreserved / pct-encoded / sub-delims / ":" / "@"

   iquery         = *( ipchar / iprivate / "/" / "?" )

   iunreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" / ucschar

   ucschar        = %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
                  / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
                  / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
                  / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
                  / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
                  / %xD0000-DFFFD / %xE1000-EFFFD

   iprivate       = %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD


            ALPHA          =  %x41-5A / %x61-7A  ; A-Z / a-z
            DIGIT          =  %x30-39            ; 0-9


 */
/* ABNF core rules: (ABNF is RFC 5234)

         ALPHA          =  %x41-5A / %x61-7A  ; A-Z / a-z

         BIT            =  "0" / "1"

         CHAR           =  %x01-7F
                               ; any 7-bit US-ASCII character,
                               ;  excluding NUL

         CR             =  %x0D
                               ; carriage return

         CRLF           =  CR LF
                               ; Internet standard newline

         CTL            =  %x00-1F / %x7F
                               ; controls

         DIGIT          =  %x30-39
                               ; 0-9

         DQUOTE         =  %x22
                               ; " (Double Quote)

         HEXDIG         =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"

         HTAB           =  %x09
                               ; horizontal tab

         LF             =  %x0A
                               ; linefeed

         LWSP           =  *(WSP / CRLF WSP)
                               ; Use of this linear-white-space rule
                               ;  permits lines containing only white
                               ;  space that are no longer legal in
                               ;  mail headers and have caused
                               ;  interoperability problems in other
                               ;  contexts.
                               ; Do not use when defining mail
                               ;  headers and use with caution in
                               ;  other contexts.

         OCTET          =  %x00-FF
                               ; 8 bits of data

         SP             =  %x20

         VCHAR          =  %x21-7E
                               ; visible (printing) characters

         WSP            =  SP / HTAB
                               ; white space

 */

