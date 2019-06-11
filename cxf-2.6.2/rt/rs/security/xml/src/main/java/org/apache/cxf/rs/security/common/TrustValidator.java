/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.common;

import java.security.PublicKey;
import java.security.cert.X509Certificate;

import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.handler.RequestData;
import org.apache.ws.security.validate.Credential;
import org.apache.ws.security.validate.SignatureTrustValidator;

public class TrustValidator {
    public void validateTrust(Crypto crypto, X509Certificate cert, PublicKey publicKey) 
        throws WSSecurityException {
        SignatureTrustValidator validator = new SignatureTrustValidator();
        RequestData data = new RequestData();
        data.setSigCrypto(crypto);
        
        Credential trustCredential = new Credential();
        trustCredential.setPublicKey(publicKey);
        trustCredential.setCertificates(new X509Certificate[]{cert});
        validator.validate(trustCredential, data);
    }
}
