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

package org.apache.cxf.systest.ws.coverage_checker;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.systest.ws.coverage_checker.server.Server;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.BeforeClass;

/**
 * A set of tests for the DefaultCryptoCoverageChecker.
 */
public class DefaultCryptoCoverageCheckerTest extends AbstractBusClientServerTestBase {
    public static final String PORT = allocatePort(Server.class);

    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");

    private boolean unrestrictedPoliciesInstalled = checkUnrestrictedPoliciesInstalled();
    
    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
            "Server failed to launch",
            // run the server in the same process
            // set this to false to fork
            launchServer(Server.class, true)
        );
    }
    
    @org.junit.AfterClass
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }
    
    @org.junit.Test
    public void testSignedBodyTimestamp() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        port.doubleIt(25);
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedBodyOnly() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedTimestampOnly() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedBodyTimestampSoap12() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://www.w3.org/2003/05/soap-envelope}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        port.doubleIt(25);
        
        bus.shutdown(true);
    }
   
    @org.junit.Test
    public void testSignedBodyOnlySoap12() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://www.w3.org/2003/05/soap-envelope}Body;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedTimestampOnlySoap12() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedEncryptedBody() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItSignedEncryptedBodyPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature Encrypt");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("encryptionPropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/bob.properties");
        outProps.put("user", "alice");
        outProps.put("encryptionUser", "bob");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        outProps.put("encryptionParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        port.doubleIt(25);
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedNotEncryptedBody() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItSignedEncryptedBodyPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature Encrypt");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("encryptionPropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/bob.properties");
        outProps.put("user", "alice");
        outProps.put("encryptionUser", "bob");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        outProps.put("encryptionParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        bus.getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not encrypting the SOAP Body");
        } catch (Exception ex) {
            // expected
        }
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testWSAddressing() throws Exception {
        if (!unrestrictedPoliciesInstalled) {
            return;
        }

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = DefaultCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = DefaultCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItWSAPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.wssec10.client.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        WSS4JOutInterceptor wss4jOutInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(wss4jOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the WS-Addressing headers");
        } catch (Exception ex) {
            // expected
        }
        
        // Now sign the WS-Addressing headers
        bus.getOutInterceptors().remove(wss4jOutInterceptor);
        
        outProps.put("signatureParts",
                "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;"
                + "{}{http://www.w3.org/2005/08/addressing}ReplyTo;");
        
        wss4jOutInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(wss4jOutInterceptor);
        
        port.doubleIt(25);
        
        bus.shutdown(true);
    }
    
    private boolean checkUnrestrictedPoliciesInstalled() {
        try {
            byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

            SecretKey key192 = new SecretKeySpec(
                new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17},
                            "AES");
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, key192);
            c.doFinal(data);
            return true;
        } catch (Exception e) {
            //
        }
        return false;
    }
    
}
