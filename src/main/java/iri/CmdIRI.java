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

package iri;

import org.seaborne.rfc3986.IRI3986;
import org.seaborne.rfc3986.IRIParseException;
import org.seaborne.rfc3986.RFC3986;

public class CmdIRI {
    public static void main(String... args) {
        if ( args.length == 0 ) {
            System.err.println("No iri string");
            System.exit(1);
        }
        for (String iriStr : args ) {
            if ( iriStr.startsWith("<") && iriStr.endsWith(">") )
                iriStr = iriStr.substring(1, iriStr.length()-1);
           try {
                IRI3986 iri = RFC3986.create(iriStr);
                IRI3986 iri1 = iri.normalize();

                System.out.println(iriStr);
                System.out.println("      ==> "+iri) ;
                if ( ! iri.equals(iri1) )
                    System.out.println("      ==> "+iri1) ;
                if ( ! iri.isAbsolute() )
                    System.out.println("Relative: "+!iri.isAbsolute()) ;

                iri.checkSchemeSpecificRules();

            } catch (IRIParseException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }
}
