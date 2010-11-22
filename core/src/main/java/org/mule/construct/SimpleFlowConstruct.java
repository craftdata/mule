/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.construct;

import org.mule.api.MuleContext;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.context.WorkManager;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.processor.MessageProcessorBuilder;
import org.mule.api.processor.MessageProcessorChainBuilder;
import org.mule.construct.processor.FlowConstructStatisticsMessageProcessor;
import org.mule.interceptor.LoggingInterceptor;
import org.mule.interceptor.ProcessingTimerInterceptor;
import org.mule.lifecycle.processor.ProcessIfStartedMessageProcessor;
import org.mule.processor.OptionalAsyncInterceptingMessageProcessor;
import org.mule.processor.chain.DefaultMessageProcessorChainBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Simple implementation of {@link AbstractFlowConstruct} that allows a list of
 * {@link MessageProcessor}s that will be used to process messages to be configured.
 * These MessageProcessors are chained together using the
 * {@link DefaultMessageProcessorChainBuilder}.
 * <p/>
 * If not message processors are configured then the source message is simply
 * returned.
 */
public class SimpleFlowConstruct extends AbstractFlowConstruct
{
    protected List<MessageProcessor> messageProcessors = Collections.emptyList();

    protected WorkManager workManager;

    public SimpleFlowConstruct(String name, MuleContext muleContext)
    {
        super(name, muleContext);
    }

    @Override
    protected void configureMessageProcessors(MessageProcessorChainBuilder builder)
    {
        if (threadingProfile == null)
        {
            threadingProfile = muleContext.getDefaultServiceThreadingProfile();
        }

        final MuleConfiguration config = muleContext.getConfiguration();
        final boolean containerMode = config.isContainerMode();
        final String threadPrefix = containerMode
                                                 ? String.format("[%s].flow.%s", config.getId(), getName())
                                                 : String.format("flow.%s", getName());

        builder.chain(new ProcessIfStartedMessageProcessor(this, getLifecycleState()));
        builder.chain(new ProcessingTimerInterceptor());
        builder.chain(new LoggingInterceptor());
        builder.chain(new FlowConstructStatisticsMessageProcessor());
        if (messageSource != null)
        {
            builder.chain(new OptionalAsyncInterceptingMessageProcessor(threadingProfile, threadPrefix,
                muleContext.getConfiguration().getShutdownTimeout()));
        }
        for (Object processor : messageProcessors)
        {
            if (processor instanceof MessageProcessor)
            {
                builder.chain((MessageProcessor) processor);
            }
            else if (processor instanceof MessageProcessorBuilder)
            {
                builder.chain((MessageProcessorBuilder) processor);
            }
            else
            {
                throw new IllegalArgumentException(
                    "MessageProcessorBuilder should only have MessageProcessor's or MessageProcessorBuilder's configured");
            }
        }
    }

    public void setThreadingProfile(ThreadingProfile threadingProfile)
    {
        this.threadingProfile = threadingProfile;
    }

    public void setMessageProcessors(List<MessageProcessor> messageProcessors)
    {
        this.messageProcessors = messageProcessors;
    }

    public List<MessageProcessor> getMessageProcessors()
    {
        return messageProcessors;
    }

    @Deprecated
    public void setEndpoint(InboundEndpoint endpoint)
    {
        this.messageSource = endpoint;
    }

    @Override
    public String getConstructType()
    {
        return "Flow";
    }
}
