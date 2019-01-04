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

import org.apache.jena.iri.IRIFactory;
import org.apache.jena.iri.ViolationCodes;

public class IRIResolvers {
    
    public static IRIFactory iriFactory() { return iriFactoryInst; }
    
    // From jena RIOT  org.apache.jena.riot.system.IRIResolver
    private static final IRIFactory iriFactoryInst = new IRIFactory();
    static {
        // These two are from IRIFactory.iriImplementation() ...
        iriFactoryInst.useSpecificationIRI(true);
        iriFactoryInst.useSchemeSpecificRules("*", true);

        // Allow relative references for file: URLs.
        iriFactoryInst.setSameSchemeRelativeReferences("file");

        // Convert "SHOULD" to warning (default is "error").
        // iriFactory.shouldViolation(false,true);

        setErrorWarning(iriFactoryInst, ViolationCodes.UNREGISTERED_IANA_SCHEME, false, false);

        // Turn off?? (ignored in CheckerIRI.iriViolations anyway).
        // setErrorWarning(iriFactory, ViolationCodes.LOWERCASE_PREFERRED, false, false);
        // setErrorWarning(iriFactory, ViolationCodes.PERCENT_ENCODING_SHOULD_BE_UPPERCASE, false, false);
        // setErrorWarning(iriFactory, ViolationCodes.SCHEME_PATTERN_MATCH_FAILED, false, false);
        
        // NFC tests are not well understood by general developers and these cause confusion.
        // See JENA-864
        //iriFactory.setIsError(ViolationCodes.NOT_NFC, false);
        //iriFactory.setIsError(ViolationCodes.NOT_NFKC, false);
        //iriFactory.setIsWarning(ViolationCodes.NOT_NFC, false);
        //iriFactory.setIsWarning(ViolationCodes.NOT_NFKC, false);

        // ** Applies to various unicode blocks. 
        // setErrorWarning(iriFactory, ViolationCodes.COMPATIBILITY_CHARACTER, false, false);

        // This causes test failures.
        // The tests catch warnings and a warning is expected.
        // testing/RIOT/Lang/TurtleStd/turtle-eval-bad-02.ttl and 03 and TriG
        //   > as \u003C  and < \u003E 
        // Default is error=true, warning=false.
        // Test pass with error=false, warning=true.
        // setErrorWarning(iriFactory, ViolationCodes.UNWISE_CHARACTER, false, false);
        setErrorWarning(iriFactoryInst, ViolationCodes.UNDEFINED_UNICODE_CHARACTER, false, false);
    }
    
    /** Set the error/warning state of a violation code.
     * @param factory   IRIFactory
     * @param code      ViolationCodes constant
     * @param isError   Whether it is to be treated an error.
     * @param isWarning Whether it is to be treated a warning.
     */
    private static void setErrorWarning(IRIFactory factory, int code, boolean isError, boolean isWarning) {
        factory.setIsError(code, isError);
        factory.setIsWarning(code, isWarning);
    }

}
