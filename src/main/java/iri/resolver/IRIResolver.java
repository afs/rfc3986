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

package iri.resolver;


import org.seaborne.rfc3986.IRI3986;
import org.seaborne.rfc3986.IRIParseException;
import org.seaborne.rfc3986.RFC3986;

/** IRI handling */
public interface IRIResolver
{
    /**
     * The base of this IRIResolver.
     */
    public default String getBaseIRIasString() {
        IRI3986 iri = getBaseIRI();
        if (iri == null)
            return null;
        return iri.toString();
    }

    /**
     * The base of this IRIResolver.
     */
    public IRI3986 getBaseIRI();

    /**
     * Resolve a relative URI against the base of this IRIResolver
     * or normalize an absolute URI.
     *
     * @param iri
     * @return the resolved IRI
     * @throws IRIParseException
     *             If resulting URI would not be legal, absolute IRI
     */
    public IRI3986 resolve(IRI3986 iri);

    /**
     * Resolve a relative URI against the base of this IRIResolver
     * or normalize an absolute URI.
     *
     * @param iriStr
     * @return the resolved IRI
     * @throws IRIParseException
     *             If resulting URI would not be legal, absolute IRI
     */
    public default IRI3986 resolve(String iriStr) {
        try {
            return resolve(RFC3986.create(iriStr));
        } catch (IRIParseException ex) {
            //throw new RiotException(ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Create a URI, resolving relative IRIs,
     * normalize an absolute URI,
     * but do not throw exception on a bad IRI.
     *
     * @param uriStr
     * @return the resolved IRI or null
     */
    public default IRI3986 resolveSilent(String uriStr) {
        try {
            return resolve(uriStr);
        } catch (IRIParseException ex) { return null; }
    }


    /** Resolving relative IRI, return a string */
    public default String resolveToString(String uriStr) {
        return resolve(uriStr).toString();
    }
}
