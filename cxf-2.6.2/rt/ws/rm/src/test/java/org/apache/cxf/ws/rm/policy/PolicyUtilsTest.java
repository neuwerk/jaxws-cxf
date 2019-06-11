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

package org.apache.cxf.ws.rm.policy;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.cxf.message.Message;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.policy.builder.jaxb.JaxbAssertion;
import org.apache.cxf.ws.rm.RM10Constants;
import org.apache.cxf.ws.rmp.v200502.RMAssertion;
import org.apache.cxf.ws.rmp.v200502.RMAssertion.AcknowledgementInterval;
import org.apache.cxf.ws.rmp.v200502.RMAssertion.BaseRetransmissionInterval;
import org.apache.cxf.ws.rmp.v200502.RMAssertion.ExponentialBackoff;
import org.apache.cxf.ws.rmp.v200502.RMAssertion.InactivityTimeout;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 */
public class PolicyUtilsTest extends Assert {

    private IMocksControl control;
    
    @Before
    public void setUp() {
        control = EasyMock.createNiceControl();
    }
    
    @Test
    public void testRMAssertionEquals() {
        RMAssertion a = new RMAssertion();
        assertTrue(RM10PolicyUtils.equals(a, a));
        
        RMAssertion b = new RMAssertion();
        assertTrue(RM10PolicyUtils.equals(a, b));
        
        InactivityTimeout iat = new RMAssertion.InactivityTimeout();
        iat.setMilliseconds(new Long(10));
        a.setInactivityTimeout(iat);
        assertTrue(!RM10PolicyUtils.equals(a, b));
        b.setInactivityTimeout(iat);
        assertTrue(RM10PolicyUtils.equals(a, b));
        
        ExponentialBackoff eb = new RMAssertion.ExponentialBackoff();
        a.setExponentialBackoff(eb);
        assertTrue(!RM10PolicyUtils.equals(a, b));
        b.setExponentialBackoff(eb);
        assertTrue(RM10PolicyUtils.equals(a, b));    
    }
    
    @Test
    public void testIntersect() {
        RMAssertion a = new RMAssertion();
        RMAssertion b = new RMAssertion();
        assertSame(a, RM10PolicyUtils.intersect(a, b));
        
        InactivityTimeout aiat = new RMAssertion.InactivityTimeout();
        aiat.setMilliseconds(new Long(3600000));
        a.setInactivityTimeout(aiat);
        InactivityTimeout biat = new RMAssertion.InactivityTimeout();
        biat.setMilliseconds(new Long(7200000));
        b.setInactivityTimeout(biat);
        
        RMAssertion c = RM10PolicyUtils.intersect(a, b);
        assertEquals(7200000L, c.getInactivityTimeout().getMilliseconds().longValue());
        assertNull(c.getBaseRetransmissionInterval());
        assertNull(c.getAcknowledgementInterval());
        assertNull(c.getExponentialBackoff());
        
        BaseRetransmissionInterval abri = new RMAssertion.BaseRetransmissionInterval();
        abri.setMilliseconds(new Long(10000));
        a.setBaseRetransmissionInterval(abri);
        BaseRetransmissionInterval bbri = new RMAssertion.BaseRetransmissionInterval();
        bbri.setMilliseconds(new Long(20000));
        b.setBaseRetransmissionInterval(bbri);
        
        c = RM10PolicyUtils.intersect(a, b);
        assertEquals(7200000L, c.getInactivityTimeout().getMilliseconds().longValue());
        assertEquals(20000L, c.getBaseRetransmissionInterval().getMilliseconds().longValue());
        assertNull(c.getAcknowledgementInterval());
        assertNull(c.getExponentialBackoff());
       
        AcknowledgementInterval aai = new RMAssertion.AcknowledgementInterval();
        aai.setMilliseconds(new Long(2000));
        a.setAcknowledgementInterval(aai);
        
        c = RM10PolicyUtils.intersect(a, b);
        assertEquals(7200000L, c.getInactivityTimeout().getMilliseconds().longValue());
        assertEquals(20000L, c.getBaseRetransmissionInterval().getMilliseconds().longValue());
        assertEquals(2000L, c.getAcknowledgementInterval().getMilliseconds().longValue());
        assertNull(c.getExponentialBackoff());
        
        b.setExponentialBackoff(new RMAssertion.ExponentialBackoff());
        c = RM10PolicyUtils.intersect(a, b);
        assertEquals(7200000L, c.getInactivityTimeout().getMilliseconds().longValue());
        assertEquals(20000L, c.getBaseRetransmissionInterval().getMilliseconds().longValue());
        assertEquals(2000L, c.getAcknowledgementInterval().getMilliseconds().longValue());
        assertNotNull(c.getExponentialBackoff());    
    }
    
    @Test
    public void testGetRMAssertion() {
        RMAssertion a = new RMAssertion();
        BaseRetransmissionInterval abri = new RMAssertion.BaseRetransmissionInterval();
        abri.setMilliseconds(new Long(3000));
        a.setBaseRetransmissionInterval(abri);
        a.setExponentialBackoff(new RMAssertion.ExponentialBackoff());
        
        Message message = control.createMock(Message.class);
        EasyMock.expect(message.get(AssertionInfoMap.class)).andReturn(null);
        control.replay();
        assertSame(a, RM10PolicyUtils.getRMAssertion(a, message));
        control.verify();
        
        control.reset();
        AssertionInfoMap aim = control.createMock(AssertionInfoMap.class);
        EasyMock.expect(message.get(AssertionInfoMap.class)).andReturn(aim);
        Collection<AssertionInfo> ais = new ArrayList<AssertionInfo>();
        EasyMock.expect(aim.get(RM10Constants.RMASSERTION_QNAME)).andReturn(ais);
        control.replay();
        assertSame(a, RM10PolicyUtils.getRMAssertion(a, message));
        control.verify();
        
        control.reset();
        RMAssertion b = new RMAssertion();
        BaseRetransmissionInterval bbri = new RMAssertion.BaseRetransmissionInterval();
        bbri.setMilliseconds(new Long(2000));
        b.setBaseRetransmissionInterval(bbri);
        JaxbAssertion<RMAssertion> assertion = new JaxbAssertion<RMAssertion>();
        assertion.setName(RM10Constants.RMASSERTION_QNAME);
        assertion.setData(b);
        AssertionInfo ai = new AssertionInfo(assertion);
        ais.add(ai);
        EasyMock.expect(message.get(AssertionInfoMap.class)).andReturn(aim);
        EasyMock.expect(aim.get(RM10Constants.RMASSERTION_QNAME)).andReturn(ais);
        control.replay();
        RMAssertion c = RM10PolicyUtils.getRMAssertion(a, message);
        assertNull(c.getAcknowledgementInterval());
        assertNull(c.getInactivityTimeout());
        assertEquals(2000L, c.getBaseRetransmissionInterval().getMilliseconds().longValue());
        assertNotNull(c.getExponentialBackoff());   
        control.verify();
    }
    
}
