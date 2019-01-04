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

package iri.dev;

import java.net.URI;
import java.util.Arrays;
import java.util.regex.Matcher;

import org.apache.jena.iri.IRI;
import org.seaborne.rfc3986.IRI3986;
import org.seaborne.rfc3986.IRIResolvers;
import org.seaborne.rfc3986.RFC3986;

//jena-iri has two roles - checking and resolving.

//RFC 3986 : 5.2.  Relative Resolution

//Normalization
//%aa => normalized to %AA : ~ and %7E
//DNS host name to lower case
//scheme to lower case.

//HTTP 1.1: https://tools.ietf.org/html/rfc7230 (userinfo removal)

// Punycode is 3492 updated by 5891; mostly the same. IDN good enough (for now!)
//  Uses ACE (ASCII-compatible encoding) prefix "xn--"

// TODO
//   Tests of normalization and resolving.
//   Resolving - order of arguments.
//   file: schema specific rules and normalization.
//   NormalformC ?

public class Dev3986 {
    //internationalized domain name (IDN) RFC 3490
    //java.net.IDN idn ; // rfc3490 ==> 5890, 5891
    //javax.net.ssl.SNIHostName sni;
    
    public static void main(String ...a) {
        dwim("http://example/dir/", "");
        dwim("http://example/dir/", "A");
        dwim("http://example/dir/", "/A");
        dwim("http://example/dir1/dir2/dir3/dir4", "..");
        dwim("http://example/dir1/dir2/dir3/dir4", "../a");
        dwim("http://example/dir1/dir2/dir3", "..");
        dwim("http://example/dir1/dir2", "..");
        dwim("http://example/dir1/dir2/", "..");
        dwim("http://example/dir/", "..");
        dwim("http://example/dir", "..");
        dwim("http://example/", "..");
        dwim("http://example", "..");
    }
    
    public static void dwim(String _base, String _rel) {
        IRI3986 base = RFC3986.create(_base);
        IRI3986 rel = RFC3986.create(_rel);
        IRI3986 iri2 = rel.resolve(base); //base.resolve.
        System.out.printf("Base=<%s>, rel=<%s> => <%s>\n", _base, _rel, iri2); 
        IRI baseIRI = IRIResolvers.iriFactory().construct( _base);
        IRI relIRI = IRIResolvers.iriFactory().construct(_rel);
        IRI iri3 = baseIRI.resolve(_rel);
        
        if ( ! iri2.toString().equals(iri3.toString()) ) {
            System.out.println(">>3986>> "+iri2);
            System.out.println(">>IRI >> "+iri3);
        }
        System.out.println();
        return;
//        
//        
//        System.out.println(iri2);
//        System.exit(0);
//        
//        String[] as1 = {
//            "/", "a", ".", "..", "/../x", "/./z","/a/../b", "/a/../b/../c",
//            "/a/../b/.",
//            "/////",
//            "/.",
//            "/../a/.",
//            "/../a/b/c/d/./e/f",
//            "/../a/b/c/d/./e/f/"
//        };
//        String[] as2 = {"/./z" };
//        for ( String s : as1 ) {
//            System.out.print(s+" => ");
//            System.out.flush();
//            String s2 = IRI3986.remove_dot_segments(s) ;
//            System.out.println(s2);
//        }
    }
    
    public static void main1(String ...a) {
        IRI3986 base = RFC3986.create("http://example/dir/");
        IRI3986 iri = RFC3986.create("a/b.txt");
        IRI3986 iri2 = iri.resolve(base);
        System.out.println(iri2);
        System.exit(0);
        
        
        parseOne("http://[::1]:80/path");
        System.out.println();
        parseOne("http://u:p@[::1]:80/path");

        //        parseOne("http://user:pw:x@host:80/abc/def?qs=ghi#jkl");
//        System.out.println();
//        
//        parseOne("http://abcdef:80/xyzβ/abc");
//        System.out.println();
//
//        parseOne("http:abc");
//        System.out.println();
//
//        parseOne("/abcdef");
//        System.out.println();
//
//        parseOne("abcdef");
//        System.out.println();
//        
//        parseOne("http://host/abcdef?qs=foo#frag");
//        System.out.println();
//        
//        parseOne("file:///path/name.txt");
//        System.out.println();
//        
//        parseOne("urn:x-local:abc/def");
//        System.out.println();
//        
//        parseOne("http://user1@host:80/path");
    }

    private static void parseOne(String str) {
        System.out.println("Input : "+str);
        URI uri = URI.create(str);
//        System.out.println("URI :   "+uri.toString());
//        System.out.println("URI :   "+uri.toASCIIString());
        
        if ( true ) {
            Matcher m1 = RFC3986.rfc3986regex.matcher(str);
            if ( m1.matches() ) {
                for ( int i = 1 ; i <= m1.groupCount() ; i++)
                    System.out.print(" "+i+"'"+m1.group(i)+"'");
                System.out.println();
                System.out.print("scheme|"+m1.group(2)+"|");
                System.out.print("  authority|"+m1.group(4)+"|");
                System.out.print("  path|"+m1.group(5)+"|");
                System.out.print("  query|"+m1.group(7)+"|");
                System.out.print("  fragment|"+m1.group(9)+"|");
                System.out.println();
                //        scheme    = $2
                //        authority = $4
                //        path      = $5
                //        query     = $7
                //        fragment  = $9
            } else {
                System.out.println("Regex 1:no match");
            }
        }
        
        IRI3986 iri3986 = RFC3986.create(str);
        
        System.out.printf("%s|%s|  ", "Scheme",     iri3986.getScheme());
        System.out.printf("%s|%s|  ", "Authority",  iri3986.getAuthority());
        System.out.printf("%s|%s|  ", "Path",       iri3986.getPath());
        System.out.printf("%s|%s|  ", "Query",      iri3986.getQuery());
        System.out.printf("%s|%s|  ", "Fragment",   iri3986.getFragment());
        System.out.println();
        iri3986.rebuild();
        System.out.println(str + " ==> " + iri3986.rebuild());
        System.out.println(Arrays.asList(iri3986.getPathSegments()));
    }
}
