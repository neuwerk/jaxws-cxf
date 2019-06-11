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

package org.apache.cxf.systest.jaxrs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.model.AbstractResourceInfo;
import org.apache.cxf.jaxrs.model.wadl.WadlGenerator;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.transport.http.HTTPConduit;

import org.junit.BeforeClass;
import org.junit.Test;

public class JAXRSClientServerResourceCreatedSpringProviderTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookServerResourceCreatedSpringProviders.PORT;

    @BeforeClass
    public static void startServers() throws Exception {
        AbstractResourceInfo.clearAllMaps();
        assertTrue("server did not launch correctly",
                   launchServer(BookServerResourceCreatedSpringProviders.class, true));
        createStaticBus();
    }
    
    @Test
    public void testBasePetStoreWithoutTrailingSlash() throws Exception {
        
        String endpointAddress = "http://localhost:" + PORT + "/webapp/resources";
        WebClient client = WebClient.create(endpointAddress);
        HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
        conduit.getClient().setReceiveTimeout(1000000);
        conduit.getClient().setConnectionTimeout(1000000);
        String value = client.accept("text/plain").get(String.class);
        assertEquals(PetStore.CLOSED, value);
    }

    @Test
    public void testBasePetStore() throws Exception {
        
        String endpointAddress = "http://localhost:" + PORT + "/webapp/resources/"; 
        WebClient client = WebClient.create(endpointAddress);
        HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
        conduit.getClient().setReceiveTimeout(1000000);
        conduit.getClient().setConnectionTimeout(1000000);
        String value = client.accept("text/plain").get(String.class);
        assertEquals(PetStore.CLOSED, value);
    }
    
    @Test
    public void testMultipleRootsWadl() throws Exception {
        List<Element> resourceEls = getWadlResourcesInfo("http://localhost:" + PORT + "/webapp/resources",
                                                         "http://localhost:" + PORT + "/webapp/resources", 2);
        String path1 = resourceEls.get(0).getAttribute("path");
        int bookStoreInd = path1.contains("/bookstore") ? 0 : 1;
        int petStoreInd = bookStoreInd == 0 ? 1 : 0;
        checkBookStoreInfo(resourceEls.get(bookStoreInd));
        checkPetStoreInfo(resourceEls.get(petStoreInd));
    }
    
    @Test
    public void testBookStoreWadl() throws Exception {
        List<Element> resourceEls = getWadlResourcesInfo("http://localhost:" + PORT + "/webapp/resources",
            "http://localhost:" + PORT + "/webapp/resources/bookstore", 1);
        checkBookStoreInfo(resourceEls.get(0));
    }
    
    @Test
    public void testPetStoreWadl() throws Exception {
        List<Element> resourceEls = getWadlResourcesInfo("http://localhost:" + PORT + "/webapp/resources",
            "http://localhost:" + PORT + "/webapp/resources/petstore", 1);
        checkPetStoreInfo(resourceEls.get(0));
    }
    
    @Test
    public void testWadlPublishedEndpointUrl() throws Exception {
        String requestURI = "http://localhost:" + PORT + "/webapp/resources2";
        WebClient client = WebClient.create(requestURI + "?_wadl&_type=xml");
        Document doc = DOMUtils.readXml(new InputStreamReader(client.get(InputStream.class), "UTF-8"));
        Element root = doc.getDocumentElement();
        assertEquals(WadlGenerator.WADL_NS, root.getNamespaceURI());
        assertEquals("application", root.getLocalName());
        List<Element> resourcesEls = DOMUtils.getChildrenWithName(root, 
                                                                  WadlGenerator.WADL_NS, "resources");
        assertEquals(1, resourcesEls.size());
        Element resourcesEl =  resourcesEls.get(0);
        assertEquals("http://proxy", resourcesEl.getAttribute("base"));
        
    }
    
    
    private void checkBookStoreInfo(Element resource) {
        assertEquals("/bookstore", resource.getAttribute("path"));
    }
    
    private void checkPetStoreInfo(Element resource) {
        assertEquals("/", resource.getAttribute("path"));
    }
    
    private List<Element> getWadlResourcesInfo(String baseURI, String requestURI, int size) throws Exception {
        WebClient client = WebClient.create(requestURI + "?_wadl&_type=xml");
        Document doc = DOMUtils.readXml(new InputStreamReader(client.get(InputStream.class), "UTF-8"));
        Element root = doc.getDocumentElement();
        assertEquals(WadlGenerator.WADL_NS, root.getNamespaceURI());
        assertEquals("application", root.getLocalName());
        List<Element> resourcesEls = DOMUtils.getChildrenWithName(root, 
                                                                  WadlGenerator.WADL_NS, "resources");
        assertEquals(1, resourcesEls.size());
        Element resourcesEl =  resourcesEls.get(0);
        assertEquals(baseURI, resourcesEl.getAttribute("base"));
        List<Element> resourceEls = 
            DOMUtils.getChildrenWithName(resourcesEl, 
                                         WadlGenerator.WADL_NS, "resource");
        assertEquals(size, resourceEls.size());
        return resourceEls;
    }
    
    @Test
    public void testGetBook123() throws Exception {
        
        String endpointAddress =
            "http://localhost:" + PORT + "/webapp/resources/bookstore/books/123"; 
        URL url = new URL(endpointAddress);
        URLConnection connect = url.openConnection();
        connect.addRequestProperty("Accept", "application/json");
        connect.addRequestProperty("Content-Language", "badgerFishLanguage");
        InputStream in = connect.getInputStream();
        assertNotNull(in);           

        //Ensure BadgerFish output as this should have replaced the standard JSONProvider
        InputStream expected = getClass()
            .getResourceAsStream("resources/expected_get_book123badgerfish.txt");

        assertEquals("BadgerFish output not correct", 
                     getStringFromInputStream(expected).trim(),
                     getStringFromInputStream(in).trim());
    }
    
    @Test
    public void testGetBookNotFound() throws Exception {
        
        String endpointAddress =
            "http://localhost:" + PORT + "/webapp/resources/bookstore/books/12345"; 
        URL url = new URL(endpointAddress);
        HttpURLConnection connect = (HttpURLConnection)url.openConnection();
        connect.addRequestProperty("Accept", "text/plain,application/xml");
        assertEquals(500, connect.getResponseCode());
        InputStream in = connect.getErrorStream();
        assertNotNull(in);           

        InputStream expected = getClass()
            .getResourceAsStream("resources/expected_get_book_notfound_mapped.txt");

        assertEquals("Exception is not mapped correctly", 
                     getStringFromInputStream(expected).trim(),
                     getStringFromInputStream(in).trim());
    }
    
    @Test
    public void testGetBookNotExistent() throws Exception {
        
        String endpointAddress =
            "http://localhost:" + PORT + "/webapp/resources/bookstore/nonexistent"; 
        URL url = new URL(endpointAddress);
        HttpURLConnection connect = (HttpURLConnection)url.openConnection();
        connect.addRequestProperty("Accept", "application/xml");
        assertEquals(405, connect.getResponseCode());
        InputStream in = connect.getErrorStream();
        assertNotNull(in);           

        assertEquals("Exception is not mapped correctly", 
                     "StringTextWriter - Nonexistent method",
                     getStringFromInputStream(in).trim());
    }
    
    @Test
    public void testPostPetStatus() throws Exception {
        
        String endpointAddress =
            "http://localhost:" + PORT + "/webapp/resources/petstore/pets";

        URL url = new URL(endpointAddress);   
        HttpURLConnection httpUrlConnection = (HttpURLConnection)url.openConnection();  
             
        httpUrlConnection.setUseCaches(false);   
        httpUrlConnection.setDefaultUseCaches(false);   
        httpUrlConnection.setDoOutput(true);   
        httpUrlConnection.setDoInput(true);   
        httpUrlConnection.setRequestMethod("POST");   
        httpUrlConnection.setRequestProperty("Accept",   "text/xml");   
        httpUrlConnection.setRequestProperty("Content-type",   "application/x-www-form-urlencoded");   
        httpUrlConnection.setRequestProperty("Connection",   "close");   

        OutputStream outputstream = httpUrlConnection.getOutputStream();
        File inputFile = new File(getClass().getResource("resources/singleValPostBody.txt").toURI());         
         
        byte[] tmp = new byte[4096];
        int i = 0;
        InputStream is = new FileInputStream(inputFile);
        try {
            while ((i = is.read(tmp)) >= 0) {
                outputstream.write(tmp, 0, i);
            }
        } finally {
            is.close();
        }

        outputstream.flush();

        int responseCode = httpUrlConnection.getResponseCode();   
        assertEquals(200, responseCode); 
        assertEquals("Wrong status returned", "open", getStringFromInputStream(httpUrlConnection
            .getInputStream()));  
        httpUrlConnection.disconnect();
    }
    
    @Test
    public void testPostPetStatus2() throws Exception {
        
        
        Socket s = new Socket("localhost", Integer.parseInt(PORT));
        IOUtils.copyAndCloseInput(getClass().getResource("resources/formRequest.txt").openStream(), 
                                  s.getOutputStream());

        s.getOutputStream().flush();
        try {
            assertTrue("Wrong status returned", getStringFromInputStream(s.getInputStream())
                       .contains("open"));  
        } finally {
            s.close();
        }
    }
    
    private String getStringFromInputStream(InputStream in) throws Exception {
        return IOUtils.toString(in);
    }

}
