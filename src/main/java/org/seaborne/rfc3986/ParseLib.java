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

/** Operations related to parsing IRIs */
/*package*/ class ParseLib {

    // Unicode - not a character
    /*package*/ static final char EOF = 0xFFFF;

    /** Test whether a character is in a charcater range (both ends inclusive) */
    public static boolean range(char ch, int start, int finish) {
        return ch >= start && ch <= finish;
    }

    /**
     * <tt>HEXDIG =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"</tt>
     * but also lower case (non-normalized form). See RFC 3986 sec 6.2.2.1
     */
    public static boolean isHexDigit(char ch) {
        return range(ch, '0', '9' ) || range(ch, 'A', 'F' ) || range(ch, 'a', 'f' )  ;
    }

    public static int hexValue(char ch) {
        if ( range(ch, '0', '9' ) ) return ch-'0';
        if ( range(ch, 'A', 'F' ) ) return ch-'A'+10;
        if ( range(ch, 'a', 'f' ) ) return ch-'a'+10;
        return -1;
    }

    private static int CASE_DIFF = 'a'-'A';     // 0x20.
    /* Check whether the character and the next character match the expected characters.
     * ASCII only.
     * chars should be lower case.
     */
    /*package*/ static boolean containsAtIgnoreCase(CharSequence string, int x, char[] chars) {
        // Avoid creating any objects.
        int n = string.length();
        if ( x+chars.length-1 >= n )
            return false;
        for ( int i = 0 ; i < chars.length ; i++ ) {
            char ch = string.charAt(x+i);
            char chx = chars[i];
            if ( ch == chx )
                continue;
            if ( range(ch, 'a', 'z' ) && ( ch-chx == CASE_DIFF ) )
                continue;
            return false;
        }
        return true;
    }

    /** Check whether the character and the next character match the expected characters. */
    public static boolean peekFor(CharSequence string, int x, char x1, char x2) {
        int n = string.length();
        if ( x+1 >= n )
            return false;
        char ch1 = string.charAt(x);
        char ch2 = string.charAt(x+1);
        return ch1 == x1 && ch2 == x2;
    }

    public static char charAt(CharSequence string, int x) {
        if ( x >= string.length() )
            return EOF;
        return string.charAt(x);
    }

    /** Return a display string for a character suitable for error messages. */
    public static String displayChar(char ch) {
        return String.format("%c (0x%04X)", ch, (int)ch);
    }

    // Copied from jena-base to make this package dependency-free.
    /** Hex digits : upper case **/
    final private static char[] hexDigitsUC = {
        '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' ,
        '9' , 'A' , 'B' , 'C' , 'D' , 'E' , 'F' } ;

    /* package */ static void encodeAsHex(StringBuilder buff, char marker, char ch) {
        if ( ch < 256 ) {
            buff.append(marker);
            int lo = ch & 0xF;
            int hi = (ch >> 4) & 0xF;
            buff.append(hexDigitsUC[hi]);
            buff.append(hexDigitsUC[lo]);
            return;
        }
        int n4 = ch & 0xF;
        int n3 = (ch >> 4) & 0xF;
        int n2 = (ch >> 8) & 0xF;
        int n1 = (ch >> 12) & 0xF;
        buff.append(marker);
        buff.append(hexDigitsUC[n1]);
        buff.append(hexDigitsUC[n2]);
        buff.append(marker);
        buff.append(hexDigitsUC[n3]);
        buff.append(hexDigitsUC[n4]);
    }
}
