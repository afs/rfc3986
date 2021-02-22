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

/** Setup and configuration of the IRI3986 parser package. */
public class SystemIRI3986 {

    public enum Compliance { STRICT, NOT_STRICT }

    public static void setErrorHandler(ErrorHandler errHandler) {
        errorHandler = errHandler;
    }

    ///* package*/ static Compliance SCHEME_SPECIFIC          = Compliance.STRICT;
    /* package*/ public static Compliance URN_SCHEME        = Compliance.STRICT;
    /* package*/ public static Compliance FILE_SCHEME       = Compliance.STRICT;

    // Default!
    private static ErrorHandler errorHandler = s -> { throw new IRIParseException(s);};

    static void parseError(int posn, String s) {
        if ( posn >= 0 )
            s = "[Posn "+posn+"] "+s;
        error(s);
    }

    static void parseError(String s) {
        error(s);
    }

    static void parseWarning(int posn, String s) {
        if ( posn >= 0 )
            s = "[Posn "+posn+"] "+s;
        warning(s);
    }

    static void parseWarning(String s) {
        warning(s);
    }

    static void schemeError(char[] scheme, String s) {
        schemeError(String.copyValueOf(scheme), s);
    }

    static void schemeError(String scheme, String s) {
        error(scheme+" URI scheme -- "+s);
    }

    static void schemeWarning(char[] scheme, String s) {
        schemeError(String.copyValueOf(scheme), s);
    }

    static void schemeWarning(String scheme, String s) {
        warning(scheme+" URI scheme -- "+s);
    }

    private static void error(String s) {
        errorHandler.error(s);
    }

    private static void warning(String s) {
        errorHandler.warning(s);
    }
}
