/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.profiler.receiver.service;

import com.navercorp.pinpoint.profiler.context.active.ActiveTraceHistogram;
import com.navercorp.pinpoint.profiler.context.active.ActiveTraceHistogramUtils;
import com.navercorp.pinpoint.profiler.context.active.ActiveTraceRepository;
import com.navercorp.pinpoint.profiler.receiver.CommandSerializer;
import com.navercorp.pinpoint.profiler.receiver.ProfilerRequestCommandService;
import com.navercorp.pinpoint.profiler.receiver.ProfilerStreamCommandService;
import com.navercorp.pinpoint.rpc.packet.stream.StreamCode;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannel;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelContext;
import com.navercorp.pinpoint.rpc.stream.StreamChannelStateChangeEventHandler;
import com.navercorp.pinpoint.rpc.stream.StreamChannelStateCode;
import com.navercorp.pinpoint.thrift.dto.command.TCmdActiveThreadCountRes;
import com.navercorp.pinpoint.thrift.io.TCommandType;
import com.navercorp.pinpoint.thrift.util.SerializationUtils;
import org.apache.thrift.TBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Taejin Koo
 */
public class ActiveThreadCountService implements ProfilerRequestCommandService<TBase<?, ?>, TBase<?, ?>>, ProfilerStreamCommandService<TBase<?, ?>>, Closeable {

    private static final long DEFAULT_FLUSH_DELAY = 1000;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final StreamChannelStateChangeEventHandler stateChangeEventHandler = new ActiveThreadCountStreamChannelStateChangeEventHandler();

    private final Timer timer = new Timer("Pinpoint-ActiveThreadCountService-Timer", true);

    private final long flushDelay;

    private final List<ServerStreamChannel> streamChannelRepository = new CopyOnWriteArrayList<ServerStreamChannel>();

    private final ActiveTraceRepository activeTraceRepository;

    public ActiveThreadCountService(ActiveTraceRepository activeTraceRepository) {
        this(activeTraceRepository, DEFAULT_FLUSH_DELAY);
    }

    public ActiveThreadCountService(ActiveTraceRepository activeTraceRepository, long flushDelay) {
        if (activeTraceRepository == null) {
            throw new NullPointerException("activeTraceRepository");
        }
        this.activeTraceRepository = activeTraceRepository;
        this.flushDelay = flushDelay;
    }

    @Override
    public short getCommandServiceCode() {
        return TCommandType.ACTIVE_THREAD_COUNT.getCode();
    }

    @Override
    public TBase<?, ?> requestCommandService(TBase<?, ?> activeThreadCountObject) {
        if (activeThreadCountObject == null) {
            throw new NullPointerException("activeThreadCountObject must not be null.");
        }

        return getActiveThreadCountResponse();
    }

    @Override
    public StreamCode streamCommandService(TBase<?, ?> tBase, ServerStreamChannelContext streamChannelContext) {
        logger.info("streamCommandService object:{}, streamChannelContext:{}", tBase, streamChannelContext);
        streamChannelContext.getStreamChannel().addStateChangeEventHandler(stateChangeEventHandler);
        return StreamCode.OK;
    }

    private TCmdActiveThreadCountRes getActiveThreadCountResponse() {

        final long currentTime = System.currentTimeMillis();
        final ActiveTraceHistogram histogram = this.activeTraceRepository.getActiveTraceHistogram(currentTime);


        TCmdActiveThreadCountRes response = new TCmdActiveThreadCountRes();
        response.setHistogramSchemaType(histogram.getHistogramSchema().getTypeCode());

        final List<Integer> activeTraceCounts = ActiveTraceHistogramUtils.asList(histogram);
        response.setActiveThreadCount(activeTraceCounts);
        response.setTimeStamp(currentTime);

        return response;
    }

    private class ActiveThreadCountStreamChannelStateChangeEventHandler implements StreamChannelStateChangeEventHandler<ServerStreamChannel> {

        private final Object lock = new Object();

        private final AtomicReference<ActiveThreadCountTimerTask> currentTaskReference = new AtomicReference<ActiveThreadCountTimerTask>();

        @Override
        public void eventPerformed(ServerStreamChannel streamChannel, StreamChannelStateCode updatedStateCode) throws Exception {
            logger.info("eventPerformed. ServerStreamChannel:{}, StreamChannelStateCode:{}.", streamChannel, updatedStateCode);
            synchronized (lock) {
                switch (updatedStateCode) {
                    case CONNECTED:
                        streamChannelRepository.add(streamChannel);

                        ActiveThreadCountTimerTask activeThreadCountTimerTask = new ActiveThreadCountTimerTask();
                        boolean turnOn = currentTaskReference.compareAndSet(null, activeThreadCountTimerTask);
                        if (turnOn) {
                            logger.info("turn on ActiveThreadCountTimerTask.");

                            timer.scheduleAtFixedRate(activeThreadCountTimerTask, flushDelay, flushDelay);
                        }
                        break;
                    case CLOSED:
                    case ILLEGAL_STATE:
                        boolean removed = streamChannelRepository.remove(streamChannel);
                        // turnOff
                        if (removed && streamChannelRepository.isEmpty()) {
                            ActiveThreadCountTimerTask currentTask = currentTaskReference.get();
                            if (currentTask != null) {
                                currentTaskReference.compareAndSet(currentTask, null);
                                currentTask.cancel();

                                logger.info("turn off ActiveThreadCountTimerTask.");
                            }
                        }
                        break;
                }
            }
        }

        @Override
        public void exceptionCaught(ServerStreamChannel streamChannel, StreamChannelStateCode updatedStateCode, Throwable e) {
            logger.warn("exceptionCaught caused:{}. ServerStreamChannel:{}, StreamChannelStateCode:{}.", e.getMessage(), streamChannel, updatedStateCode, e);
        }

    }

    @Override
    public void close() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private class ActiveThreadCountTimerTask extends TimerTask {

        @Override
        public void run() {
            if (isDebug) {
                logger.debug("ActiveThreadCountTimerTask started. target-streams:{}", streamChannelRepository);
            }

            try {
                TCmdActiveThreadCountRes activeThreadCountResponse = getActiveThreadCountResponse();
                for (ServerStreamChannel serverStreamChannel : streamChannelRepository) {
                    byte[] payload = SerializationUtils.serialize(activeThreadCountResponse, CommandSerializer.SERIALIZER_FACTORY, null);
                    if (payload != null) {
                        serverStreamChannel.sendData(payload);
                    }
                }
            } catch (Exception e) {
                logger.warn("failed to execute ActiveThreadCountTimerTask.run method. message:{}", e.getMessage(), e);
            }
        }

    }

}