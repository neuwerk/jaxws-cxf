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

package org.apache.cxf.systest.ws.wssec11;


import java.net.MalformedURLException;
import java.net.URL;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.ws.wssec11.server.Server11;
import org.apache.cxf.systest.ws.wssec11.server.Server12;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;


import wssec.wssec11.IPingService;
import wssec.wssec11.PingService11;


/**
 *
 */
public class WSSecurity11Common extends AbstractBusClientServerTestBase {
       
    private static final String INPUT = "foo";

    public void runClientServer(
        String[] argv, boolean unrestrictedPoliciesInstalled, boolean wssecurity12
    ) {
        
        Bus bus = null;
        if (unrestrictedPoliciesInstalled) {
            bus = new SpringBusFactory().createBus("org/apache/cxf/systest/ws/wssec11/client/client.xml");
        } else {
            bus = new SpringBusFactory().createBus(
                    "org/apache/cxf/systest/ws/wssec11/client/client_restricted.xml");
        }
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);

        String portNumber = null;
        if (wssecurity12) {
            portNumber = Server12.PORT;
        } else {
            portNumber = Server11.PORT;
        }
        
        URL wsdlLocation = null;
        for (String portPrefix : argv) {
            PingService11 svc = null; 
            wsdlLocation = getWsdlLocation(portPrefix, portNumber); 
            svc = new PingService11(wsdlLocation);
            final IPingService port = 
                svc.getPort(
                    new QName(
                        "http://WSSec/wssec11",
                        portPrefix + "_IPingService"
                    ),
                    IPingService.class
                );
            
            final String output = port.echo(INPUT);
            assertEquals(INPUT, output);
        }
        
        bus.shutdown(true);
    }
    
 
    
    private static URL getWsdlLocation(String portPrefix, String portNumber) {
        try {
            return new URL("http://localhost:" + portNumber + "/" + portPrefix + "PingService?wsdl");
        } catch (MalformedURLException mue) {
            return null;
        }
    }

    
    public static boolean checkUnrestrictedPoliciesInstalled() {
        boolean unrestrictedPoliciesInstalled = false;
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
            unrestrictedPoliciesInstalled = true;
        } catch (Exception e) {
            return unrestrictedPoliciesInstalled;
        }
        return unrestrictedPoliciesInstalled;
    }
    
    public static boolean isIBMJDK16() {
        String fullVersion = System.getProperty("java.fullversion");
        if (fullVersion == null) {
            //Maybe one of the non IBM JDKs dont set this property, but
            //the IBM one definitely does
            return false;
        }
        if (fullVersion.indexOf("IBM") == -1) {
            return false;
        }

        String javaVersion = System.getProperty("java.version");
        double javaVersionNum = 0.0;
        if (javaVersion.length() > 3) {
            javaVersionNum = new Double(javaVersion.substring(0, 3)).doubleValue();
        } else {
            javaVersionNum = new Double(javaVersion).doubleValue();
        }
        if (javaVersionNum < 1.6) {
            return false;
        }
        
        return true;
    }
}
