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

import org.seaborne.rfc3986.IRIParseException;
import org.seaborne.rfc3986.ParseIPv6Address;

/**
<pre>
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
</pre>
    HEXDIG is '0' to '9 , 'A' to 'F' together with lower case, (RFC3986 - the normalized form is uppercase).  
 */

public class DevParseIPv6 {
    // We parse an IPv6 by
    //   look for repeated "(h16 ':')",
    //   look for another ":",
    //   look for repeated (h16 ':')
    //   look for a IPv4 address or another h16
    //   check the whole char sequence was parsed 
    //   check the numbers of h16 units does not exceed the grammar restrictions.
    
    // TODO
    // IPv4 255 check.
    
    public static void main(String...a) {
//        dwim("[::1]"); 
//        dwim("127.0.0.1");
//        dwim("localhost");
//        dwim("seaborne.org");
        
        // IPv6 "unspecified address"
        good6("[0001:0002:0003:0004:0005:0006:0007:0008]");
        good6("[123:5678::ABCD:89EF]");
        good6("[123:5678::ABCD]");
        good6("[99::]");
        
        good6("[::]");
        // IPv6 loopback address.
        good6("[::1]");
        
        good6("[98::15.16.17.18]");
        good6("[98::2.16.17.1]");
        good6("[::2.16.17.1]");
        good6("[1234:5678::123.123.123.123]");
        
        // Bad.
        bad6("[1234:5678]");
        bad6("[1234]");
        bad6("[0001:0002:0003:0004:0005:0006:0007]");
        bad6("[123Z:5678::1]");
        bad6("1234Z:5678::1");
        
        bad6("[1234:1.2.3.4]");
        bad6("[::1.1]");
        bad6("[::99:1.2.3.4.5]");
        bad6("[::99:.1.2.3]");
        bad6("[::99:1..2.3.4]");
        bad6("[::99:1.2.3..4]");
    }
    
    
    private static void bad6(String string) {
        try {
            int start = 1; 
            System.out.println("Input: "+string);
            ParseIPv6Address.checkIPv6(string, 0, string.length());
            System.out.println("    **** Expected parse failure");
        } catch (IRIParseException ex) {
            System.out.println("    Expected: Not a legal IPv6 address: "+ex.getMessage());
            System.out.println();
        }
    }

    // We parse as h16{0,6} "::" h16{0,6} (  
    // then check for 
    
    private static void good6(String string) {
        try {
            int start = 1; 
            System.out.println("Input: "+string);
            ParseIPv6Address.checkIPv6(string, 0, string.length());
            System.out.println();
        } catch (IRIParseException ex) {
            System.out.flush();
            ex.printStackTrace();
            System.out.println();
            System.out.flush();
        }
    }
    
    // IPv6address   =                            6( h16 ":" ) ls32
    //               /                       "::" 5( h16 ":" ) ls32
    //               / [               h16 ] "::" 4( h16 ":" ) ls32
    //               / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
    //               / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
    //               / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
    //               / [ *4( h16 ":" ) h16 ] "::"              ls32
    //               / [ *5( h16 ":" ) h16 ] "::"              h16
    //               / [ *6( h16 ":" ) h16 ] "::"
    // h16           = 1*4HEXDIG
    // ls32          = ( h16 ":" h16 ) / IPv4address
    
    // Without ls32
    // ls32 - reverse parse for NN.NN.NN.NN
    // after ::  (h16 ":") (h16|ls32) 

}
