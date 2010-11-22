/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.tcp.protocols;

import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;
import org.mule.tck.DynamicPortTestCase;

import java.util.HashMap;
import java.util.Map;

public class MuleMessageLengthTestCase extends DynamicPortTestCase 
{

    protected static String TEST_MESSAGE = "Test TCP Request";

    protected String getConfigResources()
    {
        return "tcp-mplength-test.xml";
    }

    public void testSend() throws Exception
    {
        MuleClient client = new MuleClient(muleContext);
        Map props = new HashMap();
        MuleMessage result = client.send("clientEndpoint", TEST_MESSAGE, props);
        assertEquals(TEST_MESSAGE + " Received", result.getPayloadAsString());
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 1;
    }

}
