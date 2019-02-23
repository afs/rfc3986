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

import static org.seaborne.rfc3986.ParseLib.containsAtIgnoreCase;
import static org.seaborne.rfc3986.ParseLib.displayChar;
import static org.seaborne.rfc3986.ParseLib.isHexDigit;
import static org.seaborne.rfc3986.ParseLib.range;

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
 * HTTP IRIs forbid the "user@" part which is strongly discouraged in IRIs.<br/>
 * Some additional check for RFC 8141 for URNs are included such as being of the form {@code urn:NID:NSS}.
 *
 * Restrictions and limitations:
 * <ul>
 * <li>Only Java characters supported (i.e. UTF16 16 bit characters)
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
 * or
 * <pre>
 *     IRI3986 base = ...
 *     IRI3986 iri = RFC3986.create(string);
 *     IRI3986 iri2 = RFC3986.resolve(iri, base);
 * </pre>
 * Normalize:
 * <pre>
 *     RFC3986 base = ...
 *     IRI3986 iri = RFC3986.create(string);
 *     IRI3986 iri2 = RFC3986.normalize(iri);
 * </pre>
 */

public class IRI3986 {
    /**
     * Determine if the string conforms to the IRI syntax. If not, it throws an exception.
     * This operation checks the string against the RFC3986/7 grammar; it does not apply
     * scheme specific rules
     */
    public static void check(String iristr) {
        check(iristr, false);
    }
    
    /**
     * Determine if the string conforms to the IRI syntax. If not, it throws an exception.
     * This operation optionally also applies some scheme specific rules.
     */
    public static void check(String iristr, boolean applySchemeSpecificRules) {
        IRI3986 iri = new IRI3986(iristr).process();
        if ( applySchemeSpecificRules )
            iri.checkSchemeSpecificRules();
    }

    /** 
     * Determine if the string conforms to the IRI syntax.
     * If not, it throws an exception
     */
    public static IRI3986 create(String iristr) {
        IRI3986 iri = new IRI3986(iristr).process();
        return iri;
    }
    
    private final String iriStr;
    private final int length;

    private static ErrorHandler errorHandler = s-> { throw new IRIParseException(s);};
    
    public static void setErrorHandler(ErrorHandler errHandler) {
        errorHandler = errHandler;
    }
    
    // Offsets of parsed components, together with cached value.
    // The value is not calculated until first used, making pure checking
    // not need to create any extra objects.
    private int scheme0 = -1;
    private int scheme1 = -1;
    private String scheme = null;

    private int authority0 = -1;
    private int authority1 = -1;
    private String authority = null;

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

    @Override
    public String toString() {
        if ( iriStr != null )
            return iriStr;
        return rebuild();
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
            errorHandler.error("Bad character in "+label+" component: "+displayChar(charAt(x)));
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

    public boolean hasPath() { return path0 != -1 ; }
    public String getPath() {
        if ( hasPath() && path == null)
            path = part(iriStr, path0, path1);
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


    /** <a href="https://tools.ietf.org/html/rfc3986#section-4.2">RFC 3986, Section 4.2</a> */
    public boolean isAbsolute() {
        // With scheme, without fragment
        return scheme0 >= 0 && fragment0 < 0;
    }

//    public boolean isHierarchical() {
//        ????    
//        return false;
//    }

    private static char[] HTTPchars = { 'h','t','t','p',':' };
    private static char[] HTTPSchars = { 'h','t','t','p','s',':' };
    private static char[] URNchars =  { 'u','r','n',':' };
    private static char[] FILEchars = { 'f','i','l','e',':' };
    
    private boolean isScheme(char[] schemeChars) { return containsAtIgnoreCase(iriStr, 0, schemeChars); } 
    
    /** Apply scheme specific rules */
    public IRI3986 checkSchemeSpecificRules() {
        // No internal objects created.
        if ( ! hasScheme() )
            // no scheme, no checks.
            return this;
        if ( isScheme(HTTPchars) )
            checkHTTP();
        else if ( isScheme(FILEchars) )
            checkFILE();
        else if ( isScheme(URNchars) )
            checkURN();
        return this;
    }

    /** Encode RFC 3987 (IRI) as strict 3986 (URI) using %-encoding */
    public IRI3986 as3986() {
        // The URI is valid so we just need to encode non-ASCII characters.
        for ( int i = 0 ; i < iriStr.length(); i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch > 0x7F)
                return encode();
        }
        return this;
    }
    
    // The encoding work.
    private IRI3986 encode() {
        StringBuilder sb = new StringBuilder(iriStr.length()+20); 
        for ( int i = 0 ; i < iriStr.length(); i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch <= 0x7F)
                sb.append(ch);
            else
                ParseLib.encodeAsHex(sb, '%', ch) ;
        }
        String s = sb.toString();
        return new IRI3986(s);
    }

    /** Normalize : 3986 section 6.2.2.  Syntax-Based Normalization */
    public IRI3986 normalize() {
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
        
        if ( scheme != null )
            scheme = scheme.toLowerCase(Locale.ROOT);
        if ( authority != null )
            authority = authority.toLowerCase(Locale.ROOT);

        // percent encoding - to upper case.
        
//     6.2.2.2.  Percent-Encoding Normalization
//
//        The percent-encoding mechanism (Section 2.1) is a frequent source of
//        variance among otherwise identical URIs.  In addition to the case
//        normalization issue noted above, some URI producers percent-encode
//        octets that do not require percent-encoding, resulting in URIs that
//        are equivalent to their non-encoded counterparts.  These URIs should
//        be normalized by decoding any percent-encoded octet that corresponds
//        to an unreserved character, as described in Section 2.3.
        
        // percent encoding - to unreserved

//     6.2.2.3.  Path Segment Normalization
        
        if ( path != null )
            path = remove_dot_segments(path);

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

        String s = rebuild(scheme, authority, path, query, fragment); 
        return new IRI3986(s);
    }

    /** Resolve {@code this } using {@code baseIRI} as the base : 3986 section ?? */
    public IRI3986 resolve(IRI3986 baseIRI) {
        // Base must have scheme. Be lax.
        return transformReferences(this, baseIRI);
    }
    
    // Make absolute = resolve(Base)

    /** 5.2.2.  Transform References */
    private static IRI3986 transformReferences(IRI3986 reference, IRI3986 base) {
        String t_scheme = null;
        String t_authority = null;
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

        if ( reference.hasScheme() ) {
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
        if ( base.hasAuthority() && base.getPath().isBlank() ) {
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
        if ( true ) {
            String s2 = removeDotSegments(path);
            if ( ! Objects.equals(s1, s2) )
                System.err.printf("remove_dot_segments : %s %s\n", s1, s2); 
        }
        return s1;
    }
    
    /* Implement using segments. -- * 5.2.4.  Remove Dot Segments */
    private static String remove_dot_segments$(String path) {
        if ( path.isEmpty() )
            // Strictly, unnecessary.
            return "";
       
        String[] segments = path.split("/");
//        if ( segments.length == 0 )
//            // Can't happen. Even "" splits to [""]
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
    
    // >> Copied from jena-iri
    static String removeDotSegments(String path) {
        // 5.2.4 step 1.
        int inputBufferStart = 0;
        int inputBufferEnd = path.length();
        StringBuffer output = new StringBuffer();
        // 5.2.4 step 2.
        while (inputBufferStart < inputBufferEnd) {
            String in = path.substring(inputBufferStart);
            // 5.2.4 step 2A
            if (in.startsWith("./")) {
                inputBufferStart += 2;
                continue;
            }
            if (in.startsWith("../")) {
                inputBufferStart += 3;
                continue;
            }
            // 5.2.4 2 B.
            if (in.startsWith("/./")) {
                inputBufferStart += 2;
                continue;
            }
            if (in.equals("/.")) {
                in = "/"; // don't continue, process below.
                inputBufferStart += 2; // force end of loop
            }
            // 5.2.4 2 C.
            if (in.startsWith("/../")) {
                inputBufferStart += 3;
                removeLastSeqment(output);
                continue;
            }
            if (in.equals("/..")) {
                in = "/"; // don't continue, process below.
                inputBufferStart += 3; // force end of loop
                removeLastSeqment(output);
            }
            // 5.2.4 2 D.
            if (in.equals(".")) {
                inputBufferStart += 1;
                continue;
            }
            if (in.equals("..")) {
                inputBufferStart += 2;
                continue;
            }
            // 5.2.4 2 E.
            int nextSlash = in.indexOf('/', 1);
            if (nextSlash == -1)
                nextSlash = in.length();
            inputBufferStart += nextSlash;
            output.append(in.substring(0, nextSlash));
        }
        // 5.2.4 3
        return output.toString();
    }
    
    private static void removeLastSeqment(StringBuffer output) {
        int ix = output.length();
        while (ix > 0) {
            ix--;
            if (output.charAt(ix) == '/')
                break;
        }
        output.setLength(ix);
    }
    // << Copied from jena-iri

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
    //   "urn", ASCII, min 2 char NID min one char  NSS (urn:NID:NSS)
    //   Query string starts ?+ or ?=
    
    // Without rq-components and "#" f-component
    private static String URN_REGEX_ASSIGNED_NAME = "([uU][rR][nN]:)([a-zA-Z0-9][-a-zA-Z0-9]{0,28}[a-zA-Z0-9])?:.*";
    private static Pattern URN_PATTERN_ASSIGNED_NAME = Pattern.compile("^urn:([a-zA-Z0-9][-a-zA-Z0-9]{0,30}[a-zA-Z]):..*");
    
    private void checkURN() {
        String scheme = getScheme();
        if ( ! scheme.equals("urn") )
            errorHandler.error("urn: scheme name is not lowercase 'urn\'");
        boolean matches = URN_PATTERN_ASSIGNED_NAME.matcher(iriStr).matches();
        if ( !matches )
            errorHandler.error("urn: does not match the assigned-name regular expession");
        if ( hasQuery() ) {
            String qs = getQuery();
            if ( ! qs.startsWith("+") && ! qs.startsWith("=") )
                errorHandler.error("urn: improper start to query string.");
            urnCharCheck("query", qs);
        }
        
        if ( hasFragment() )
            urnCharCheck("fragement", getFragment());
    }
    
    private void urnCharCheck(String label, String string) {
        for ( int i = 0 ; i < string.length(); i++ ) {
            char ch = iriStr.charAt(i);
            if ( ch > 0x7F)
                errorHandler.error("urn "+label+" : Non-ASCII character"); 
        }
    }

    private void checkHTTP() {
        if ( getAuthority().contains("@") )
            //Warning?
            errorHandler.error("userinfo (e.g. user:password) in authority section");
    }

    private void checkFILE() {
        if ( ! hasAuthority() )
            // file:/path.
            errorHandler.error("file: URLs are of the form file:///path/..."); 
        if ( authority0 != authority1 )
            // file://path1/path2/..., so path becomes the "authority"
            errorHandler.error("file: URLs are of the form file:///path/...");
    }

    // ---- Scheme

    private int scheme(int start) {
        int p = start;
        int end = length;
        while (p < end) {
            char c = charAt(p);
            if ( isAlpha(c) ) {}
            else if ( c == ':' ) {
                return p;
            } else
            {
                if ( p > start ) {
                    if ( isDigit(c) || c == '+' || c == '-' || c == '.' ) {}
                    else {
                        //bad.
                        return -1;
                    }
                }
            }
            p++;
        }
        // Did not find ':'
        return 0;
    }

    private int withScheme(int start){
        // URI           = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
        // absolute-URI  = scheme ":" hier-part [ "?" query ]
        // hier-part     = "//" authority path-abempty
        int p = maybeAuthority(start);
        return pathQueryFragment(p);
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
            errorHandler.error("A URI without a scheme can't start with a ':'");
        int p = maybeAuthority(start);
        return pathQueryFragment(p);
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
            char ch3 = charAt(p);
            if ( p != length && ch3 != '/' )
                errorHandler.error("Bad path after authority: "+displayChar(ch3));
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
     * IPv6address   = hex and ":", and "." for IpV4 in IPv5.
     * IPv4address   = dec-octet "." dec-octet "." dec-octet "." dec-octet
     *
     * So the section is only
     * unreserved / pct-encoded / sub-delims / ":" / "@" / "[" / "]".
     *   isPChar includes ":" / "@"
     *    unreserved has "."
     *
     * iauthority     = [ iuserinfo "@" ] ihost [ ":" port ]
     * iuserinfo      = *( iunreserved / pct-encoded / sub-delims / ":" )
     * ihost          = IP-literal / IPv4address / ireg-name
     */
    private int authority(int start) {
        int end = length;
        int p = start;
        // userinfo@host:port
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
                    errorHandler.error("Bad IPv6 address - No closing ']'");
                break;
            } else if ( ch == '@' ) {
                if ( endUserInfo != -1 )
                    errorHandler.error("Bad authority segment - multiple '@'");
                // Found userinfo end; reset counts and trackers.
                // Check for IPv6 []
                if ( startIPv6 != -1 || endIPv6 != -1 )
                    errorHandler.error("Bad authority segment - contains '[' or ']'");
                endUserInfo = p;
                // Reset port colon tracking.
                countColon = 0;
                lastColon = -1;
            } else if ( ch == '[' ) { 
                // Still to check whether user authority
                if ( startIPv6 >= 0 )
                    errorHandler.error("Bad IPv6 address - multiple '['");
                startIPv6 = p;
            } else if ( ch == ']' ) {
                // Still to check whether user authority
                if ( startIPv6 == -1 )
                    errorHandler.error("Bad IPv6 address - No '[' to match ']'");
                if ( endIPv6 >= 0 )
                    errorHandler.error("Bad IPv6 address - multiple ']'");
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
                errorHandler.error("Bad IPv6 address - missing ']'");
            char ch1 = iriStr.charAt(startIPv6);
            char ch2 = iriStr.charAt(endIPv6);
            ParseIPv6Address.checkIPv6(iriStr, startIPv6, endIPv6+1);
        }

        // May not be valid but if tests fail there is an exception.
        authority0 = start;
        authority1 = p;
        int limit = p;
        int userinfo0;
        int userinfo1;

        if ( endUserInfo != -1 ) {
            userinfo0 = start;
            userinfo1 = endUserInfo;
            host0 = endUserInfo+1;
            //String userinfo = input.substring(start, endUserInfo);
            if ( lastColon != -1 && lastColon < endUserInfo )
                // Not port, part of userinfo - ignore.
                lastColon = -1;
        } else {
            host0 = start;
        }

        // Check only one ":" in host.
        if ( countColon > 1 )
            errorHandler.error("Multiple ':' in host:port section");

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
                errorHandler.error("Bad port");
            // Check port.

        } else
            host1 = limit;
//        String u = part(input, endUserInfo==-1?-1:start, endUserInfo);
//        String h = part(input, host0, host1);
//        String ps = part(input, port0, port1);
//        System.out.printf("Authority: |%s|%s|%s|\n", u,h,ps);
        return limit;
    }

    // Port number - may be zero length.
    private int port(int x) {
        while( x < length ) {
            char ch2 = charAt(x);
            // Normal end.
            if ( ch2 == '/')
                return x;
            if ( !isDigit(ch2) )
                errorHandler.error("Bad character terminating port number : "+ch2);
            x++;
        }
        return x;
    }

    private int authorityBasic(int start) {
        // "Grab-all" authority scan.
        int p = start;
        int end = length;
        while( p < length ) {
            char ch = charAt(p);
            // pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
            // but ':' '@' have restrictions.
            if ( ! unreserved(ch) && ! isPctEncoded(ch, p) && !subDelims(ch) && ch != ':' && ch!='@' )
                break;
            p++;
        }
        if ( p > end-1 )
            return p;
        char chx = charAt(p);
        // port?
        if ( chx == ':' ) {
            int portStart = p;
            p++;
            while( p < length ) {
                char ch = charAt(p);
                if ( ! isDigit(ch) )
                    break;
                p++;
            }
        }
        return p;
    }

    private int pathQueryFragment(int start) {
        // hier-part [ "?" query ] [ "#" fragment ]
        // relative-ref  = relative-part [ "?" query ] [ "#" fragment ]

        // hier-part => path-abempty
        // relative-part = path-abempty
        //               / path-absolute
        //               / path-noscheme
        //               / path-empty
        // then [ "?" query ] [ "#" fragment ]

        int x1 = path(start);

        if ( x1 < 0 ) {
            x1 = start;
        } else {
            path0 = start;
            path1 = x1;
        }

        int x2 = query(x1);
        if ( x2 < 0 ) {
            x2 = x1;
        }
        int x3 = fragment(x2);
        return x3;
    }


    // ---- Path

    private int path(int start) {
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

        while (p < length ) {
            // skip segment-nz    = 1*pchar
            char ch = charAt(p);
            if ( isIPChar(ch, p) ) {
                // OK
            } else {
                // End segment.
                segStart = p+1;
                // Maybe new one.
                if ( ch != '/') {
                    // ? or # else error
                    if ( ch == '?' || ch == '#' )
                        break;
                    errorHandler.error("not query or fragement: "+ch);
                }
            }
            p++;
        }

        if ( p > start ) {
            path0 = start;
            path1 = Math.min(p,length);;
        }
        return p;
    }

    // ---- Query & Fragment
    private int query(int start) {
        // query         = *( pchar / "/" / "?" )
        int x = trailer('?', start);

        if ( x >= 0 && x != start ) {
            query0 = start+1;
            query1 = x;
        }
        if ( x < 0 )
            x = start;
        return x;
    }

    private int fragment(int start) {
        //fragment      = *( pchar / "/" / "?" )
        int x = trailer('#', start);
        if ( x >= 0 && x != start ) {
            fragment0 = start+1;
            fragment1 = x;
        }
        if ( x < 0 )
            x = start;
        return x;
    }

    private int trailer(char startChar, int start) {
        if ( start >= length )
            return -1;
        if ( charAt(start) != startChar )
            return -1;
        int p = start+1;
        while(p < length ) {
            char ch = charAt(p);
            //System.out.println("    char="+ch);
            if ( ! isIPChar(ch, p) && ch != '/' && ch != '?' )
                // p is the index of the non-query char
                return p;
            p++;
        }
        return p; // =length.
    }

    private char charAt(int x) {
        if ( x >= length )
            return EOF;
        return iriStr.charAt(x);
    }

    // ---- Character classification

    // Unicode - not a character
    private static final char EOF = ParseLib.EOF;

    // Is the character at location 'x' percent-encoded? Looks at next two characters. 
    private boolean isPctEncoded(char ch, int x) {
        if ( ch != '%' )
            return false;
        char ch1 = charAt(x+1);
        char ch2 = charAt(x+2);
        if ( ch1 == EOF || ch2 == EOF )
            errorHandler.error("Incomplete %-encoded character");
            //return false;
        if ( isHexDigit(ch1) && isHexDigit(ch2) )
            return true;
//        if ( range(ch1, 'a', 'f') || range(ch2, 'a', 'f') )
//            errorHandler.error("Bad %-encoded character (must be upper case hex digits)");
        errorHandler.error("Bad %-encoded character ["+displayChar(ch1)+" "+displayChar(ch2)+"]");
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
//        / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
//        / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
//        / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
//        / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
//        / %xD0000-DFFFD / %xE1000-EFFFD

    // Combining chars

    private static boolean isUcsChar(char ch) {
        return range(ch, 0xA0, 0xDFF)  || range(ch, 0xF900, 0xFDCF)  || range(ch, 0xFDF, 0xFFEF)
            // Java is 16 bits chars.
//            || range(ch, 0x10000, 0x1FFFD) || range(ch, 0x20000, 0x2FFFD) || range(ch, 0x30000, 0x3FFFD)
//            || range(ch, 0x40000, 0x4FFFD) || range(ch, 0x50000, 0x5FFFD) || range(ch, 0x60000, 0x6FFFD)
//            || range(ch, 0x70000, 0x7FFFD) || range(ch, 0x80000, 0x8FFFD) || range(ch, 0x90000, 0x9FFFD)
//            || range(ch, 0xA0000, 0xAFFFD) || range(ch, 0xB0000, 0xBFFFD) || range(ch, 0xC0000, 0xCFFFD)
//            || range(ch, 0xD0000, 0xDFFFD) || range(ch, 0xE1000, 0xEFFFD)
           ;
    }

    //iprivate       = %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD
    private static boolean isIPrivate(char ch) {
        return range(ch, 0xE000, 0xF8FF)
            // Java is 16 bits chars.
            // ||  range(ch, 0xF0000, 0xFFFFD) || range(ch, 0x100000, 0X10FFFD)
           ;
    }

    private static boolean isDigit(char ch) {
        return (ch >= '0' && ch <= '9');
    }

//  pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
//  pct-encoded   = "%" HEXDIG HEXDIG
//
//  unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
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


    /* ABNF -> parser */
    /* JavaCC */
    // Java's URI - not 3986.
    //https://github.com/dmfs/uri-toolkit

    /*
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



   pct-encoded   = "%" HEXDIG HEXDIG

   unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
   reserved      = gen-delims / sub-delims
   gen-delims    = ":" / "/" / "?" / "#" / "[" / "]" / "@"
   sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
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
    /* ABNF core rules: RFC 5234
     *
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
}