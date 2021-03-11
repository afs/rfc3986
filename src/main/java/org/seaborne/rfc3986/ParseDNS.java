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

import java.util.ArrayList;
import java.util.List;

public class ParseDNS {
    /*
         RFC 1034:
         <domain> ::= <subdomain> | " "

         <subdomain> ::= <label> | <subdomain> "." <label>

         <label> ::= <letter> [ [ <ldh-str> ] <let-dig> ]
                     letter or digit
         <ldh-str> ::= <let-dig-hyp> | <let-dig-hyp> <ldh-str>

         <let-dig-hyp> ::= <let-dig> | "-"

         <let-dig> ::= <letter> | <digit>

         <letter> ::= any one of the 52 alphabetic characters A through Z in
         upper case and a through z in lower case

         <digit> ::= any one of the ten digits 0 through 9

         Remove leading alpha restriction.
         %-encoded.
     */

    // <label> ::= <let-dig> (<let-dig-hyp>)* <let-dig>
    // allowPercentEncoding

    private final String string;
    private final int length;
    // XXX Migrate to ParseLib.
    private static char EOF = ParseLib.EOF;
    private static char HYPHEN = '-';

    public ParseDNS(String string) {
        this.string = string;
        this.length = string.length();
    }

    static class DNSParseException extends RuntimeException {
        DNSParseException() {}
        DNSParseException(String msg) {super(msg);}
    }

    public static String parse(String string) {
        new ParseDNS(string).parse(false);
        return string;
    }

    /** String.charAt except with an EOF character, not an exception. */
    private char charAt(int x) {
        if ( x >= length )
            return EOF;
        return string.charAt(x);
    }

    public void parse(boolean allowPercentEncoding) {
        int end = length;
        int p = 0 ;

        List<Integer> dots = new ArrayList<>(4);

        // XXX allowPercentEncoding
        // XXX if string is empty or single space.
        // XXX if string starts "."
        // XXX if string ends "."

        while (p < end) {
            p = label(p);
            if ( p < 0 )
                // Error
                throw new DNSParseException();
            if ( p == length )
                break;
            // Separator dots
            dots.add(p-1);
        }
        System.out.println("Dots: "+dots);
    }

    // <letter> [ [ <ldh-str> ] <let-dig> ]
    // Modified to allow start digit.
    // Ends at "." or end of string.
    // so it does not need to backoff for "-" in the last letter.

    // End of label happens in two ways - find a "." or end of string.
    private int label(int p) {
        int end = length;
        int start = p;
        boolean charIsHyphen = false;
        while (p < end) {
            char ch = charAt(p);
            //System.out.println("Char = "+Character.toString(ch));
            if ( ch == '.' ) {
                if ( charIsHyphen )
                    // From last round.
                    throw new DNSParseException("Bad last character of subdomain: '"+Character.toString(ch)+"'");
                p++;
                break;
            }
            charIsHyphen = ( ch == HYPHEN );

            if ( ! letter_digit(ch) && ! charIsHyphen )
                throw new DNSParseException("Bad character: '"+Character.toString(ch)+"'");
            if ( p == start && charIsHyphen )
                throw new DNSParseException("Bad first character of subdomain: '"+Character.toString(ch)+"'");
            p++;
        }
        if ( p != end && start+1 == p )
            throw new DNSParseException("Zero length subdomain");
        return p;
    }

    private static boolean let_dig_hyp(char ch) { return letter_digit_hyphen(ch); }

    // ----

    private static boolean letter_digit(char ch) {
        return letter(ch) || digit(ch);
    }

    private static boolean letter_digit_hyphen(char ch) {
        return letter(ch) || digit(ch) || ch == '-';
    }

    private static boolean letter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    private static boolean digit(char ch) {
        return (ch >= '0' && ch <= '9');
    }

//    private static void let_dig_hyp() {}
//    private static void ldh_str() {}
}
