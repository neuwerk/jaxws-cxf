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
package org.apache.cxf.transport.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.transport.http.AbstractHTTPDestination;



public abstract class AbstractHTTPServlet extends HttpServlet {
    
    private static final long serialVersionUID = -8357252743467075117L;

    /**
     * List of well-known HTTP 1.1 verbs, with POST and GET being the most used verbs at the top 
     */
    private static final List<String> KNOWN_HTTP_VERBS = 
        Arrays.asList(new String[]{"POST", "GET", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE"});
    
    private static final String STATIC_RESOURCES_PARAMETER = "static-resources-list";
    private static final String STATIC_WELCOME_FILE_PARAMETER = "static-welcome-file";
    
    private static final String REDIRECTS_PARAMETER = "redirects-list";
    private static final String REDIRECT_SERVLET_NAME_PARAMETER = "redirect-servlet-name";
    private static final String REDIRECT_SERVLET_PATH_PARAMETER = "redirect-servlet-path";
    private static final String REDIRECT_ATTRIBUTES_PARAMETER = "redirect-attributes";
    private static final String REDIRECT_QUERY_CHECK_PARAMETER = "redirect-query-check";
    
    private static final Map<String, String> STATIC_CONTENT_TYPES;
    
    static {
        STATIC_CONTENT_TYPES = new HashMap<String, String>();
        STATIC_CONTENT_TYPES.put("html", "text/html");
        STATIC_CONTENT_TYPES.put("txt", "text/plain");
        STATIC_CONTENT_TYPES.put("css", "text/css");
        STATIC_CONTENT_TYPES.put("pdf", "application/pdf");
        STATIC_CONTENT_TYPES.put("xsd", "application/xml");
        // TODO : add more types if needed
    }
    
    private List<Pattern> staticResourcesList;
    private String staticWelcomeFile;
    private List<Pattern> redirectList; 
    private String dispatcherServletPath;
    private String dispatcherServletName;
    private Map<String, String> redirectAttributes;
    private boolean redirectQueryCheck;
    
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);

        staticResourcesList = parseListSequence(servletConfig.getInitParameter(STATIC_RESOURCES_PARAMETER));
        staticWelcomeFile = servletConfig.getInitParameter(STATIC_WELCOME_FILE_PARAMETER);
        
        redirectList = parseListSequence(servletConfig.getInitParameter(REDIRECTS_PARAMETER));
        redirectQueryCheck = Boolean.valueOf(servletConfig.getInitParameter(REDIRECT_QUERY_CHECK_PARAMETER));
        dispatcherServletName = servletConfig.getInitParameter(REDIRECT_SERVLET_NAME_PARAMETER);
        dispatcherServletPath = servletConfig.getInitParameter(REDIRECT_SERVLET_PATH_PARAMETER);
        
        redirectAttributes = parseMapSequence(servletConfig.getInitParameter(REDIRECT_ATTRIBUTES_PARAMETER));
            
        
    }
    
    protected static List<Pattern> parseListSequence(String values) {
        if (values != null) {
            List<Pattern> list = new LinkedList<Pattern>();
            String[] pathValues = values.split(" ");
            for (String value : pathValues) {
                String theValue = value.trim();
                if (theValue.length() > 0) {
                    list.add(Pattern.compile(theValue));
                }
            }
            return list;
        } else {
            return null;
        }
    }
    
    protected static Map<String, String> parseMapSequence(String sequence) {
        if (sequence != null) {
            sequence = sequence.trim();
            Map<String, String> map = new HashMap<String, String>();
            String[] pairs = sequence.split(" ");
            for (String pair : pairs) {
                String[] value = pair.split("=");
                if (value.length == 2) {
                    map.put(value[0].trim(), value[1].trim());
                } else {
                    map.put(pair.trim(), "");
                }
            }
            return map;
        } else {
            return Collections.emptyMap();
        }
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException {
        handleRequest(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException {
        handleRequest(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        handleRequest(request, response);
    }
    
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        handleRequest(request, response);
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        handleRequest(request, response);
    }
    
    /**
     * {@inheritDoc}
     * 
     * javax.http.servlet.HttpServlet does not let to override the code which deals with
     * unrecognized HTTP verbs such as PATCH (being standardized), WebDav ones, etc.
     * Thus we let CXF servlets process unrecognized HTTP verbs directly, otherwise we delegate
     * to HttpService  
     */
    @Override
    public void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException {
        
        HttpServletRequest      request;
        HttpServletResponse     response;
        
        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            throw new ServletException("Unrecognized HTTP request or response object");
        }
        
        String method = request.getMethod();
        if (KNOWN_HTTP_VERBS.contains(method)) {
            super.service(request, response);
        } else {
            handleRequest(request, response);
        }
    }
    
    protected void handleRequest(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException {
        if ((dispatcherServletPath != null || dispatcherServletName != null)
            && (redirectList != null && matchPath(redirectList, request)
                || redirectList == null)) {
            // if no redirectList is provided then this servlet is redirecting only
            redirect(request, response, request.getPathInfo());
            return;
        }
        
        if (staticResourcesList != null 
            && matchPath(staticResourcesList, request)
            || staticWelcomeFile != null 
                && (StringUtils.isEmpty(request.getPathInfo()) || request.getPathInfo().equals("/"))) {
            serveStaticContent(request, response, 
                               staticWelcomeFile != null ? staticWelcomeFile : request.getPathInfo());
            return;
        }
        invoke(request, response);
    }
    
    private boolean matchPath(List<Pattern> values, HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }
        if (redirectQueryCheck) {
            String queryString = request.getQueryString();
            if (queryString != null && queryString.length() > 0) {
                path += "?" + queryString; 
            }
        }
        for (Pattern pattern : values) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }
    
    protected void serveStaticContent(HttpServletRequest request, 
                                      HttpServletResponse response,
                                      String pathInfo) throws ServletException {
        InputStream is = super.getServletContext().getResourceAsStream(pathInfo);
        if (is == null) {
            throw new ServletException("Static resource " + pathInfo + " is not available");
        }
        try {
            int ind = pathInfo.lastIndexOf(".");
            if (ind != -1 && ind < pathInfo.length()) {
                String type = STATIC_CONTENT_TYPES.get(pathInfo.substring(ind + 1));
                if (type != null) {
                    response.setContentType(type);
                }
            }
            
            ServletOutputStream os = response.getOutputStream();
            IOUtils.copy(is, os);
            os.flush();
        } catch (IOException ex) {
            throw new ServletException("Static resource " + pathInfo 
                                       + " can not be written to the output stream");
        }
        
    }
    
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String pathInfo) 
        throws ServletException {
        
        String theServletPath = dispatcherServletPath == null ? "/" : dispatcherServletPath;
        
        ServletContext sc = super.getServletContext();
        RequestDispatcher rd = dispatcherServletName != null 
            ? sc.getNamedDispatcher(dispatcherServletName) 
            : sc.getRequestDispatcher(theServletPath + pathInfo);
        if (rd == null) {
            String errorMessage = "No RequestDispatcher can be created for path " + pathInfo;
            if (dispatcherServletName != null) {
                errorMessage += ", dispatcher name: " + dispatcherServletName;
            }
            throw new ServletException(errorMessage);
        }
        try {
            for (Map.Entry<String, String> entry : redirectAttributes.entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            HttpServletRequestFilter servletRequest = 
                new HttpServletRequestFilter(request, pathInfo, theServletPath);
            rd.forward(servletRequest, response);
        } catch (Throwable ex) {
            throw new ServletException("RequestDispatcher for path " + pathInfo + " has failed");
        }   
    }
    
    protected abstract void invoke(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException;
    
    private static class HttpServletRequestFilter extends HttpServletRequestWrapper {
        
        private String pathInfo;
        private String servletPath;
        
        public HttpServletRequestFilter(HttpServletRequest request, 
                                        String pathInfo,
                                        String servletPath) {
            super(request);
            this.pathInfo = pathInfo;
            this.servletPath = servletPath;
        }
        
        @Override
        public String getServletPath() {
            return servletPath;
        }
        
        @Override
        public String getPathInfo() {
            return pathInfo; 
        }
        
        @Override
        public String getRequestURI() {
            String contextPath = getContextPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            return contextPath + servletPath + pathInfo;
        }
        
        @Override
        public String getParameter(String name) {
            if (AbstractHTTPDestination.SERVICE_REDIRECTION.equals(name)) {
                return "true";
            }
            return super.getParameter(name);
        }
    }

}
