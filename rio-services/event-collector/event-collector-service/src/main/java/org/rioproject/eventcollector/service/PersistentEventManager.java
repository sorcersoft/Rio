/*
 * Copyright to the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.eventcollector.service;

import net.jini.core.event.RemoteEvent;
import org.rioproject.event.RemoteServiceEvent;

import java.io.*;
import java.rmi.MarshalledObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@code EventManager} that keeps an in-memory collection of events, and writes them out to disk as well.
 *
 * @author Dennis Reedy
 */
@SuppressWarnings("unused")
public class PersistentEventManager extends TransientEventManager {
    private File persistentEventDirectory;
    private static final BlockingQueue<RemoteServiceEvent> eventWriteQ = new LinkedBlockingQueue<RemoteServiceEvent>();
    private final DateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss.SSS");
    private static final Logger logger = Logger.getLogger(PersistentEventManager.class.getName());

    @Override
    public void initialize(EventCollectorContext context) throws Exception {
        super.initialize(context);
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(System.getProperty("user.home")).append(File.separator).append(".rio");
        pathBuilder.append(File.separator).append("events");
        File defaultPersistentDirectoryRoot = new File(pathBuilder.toString());
        persistentEventDirectory = new File(context.getPersistentDirectoryRoot(), "collection");
        if(!persistentEventDirectory.exists()) {
            if(persistentEventDirectory.mkdirs() && logger.isLoggable(Level.INFO)) {
                logger.fine(String.format("Created %s", persistentEventDirectory.getPath()));
            }
        }
        List<RemoteServiceEvent> persistedEvents = getPersistedEvents();
        if(!persistedEvents.isEmpty()) {
            addRemoteEvents(persistedEvents);
        }
        logger.info(String.format("Persistent event directory: %s, have %d persisted events",
                                  persistentEventDirectory.getPath(), getNumberOfCollectedEvents()));
        if(logger.isLoggable(Level.FINEST)) {
            StringBuilder builder = new StringBuilder();
            for(RemoteEvent event : getEvents()) {
                if(builder.length()>0)
                    builder.append("\n");
                builder.append(event.toString());
            }
            logger.finest(builder.toString());
        }
        getExecutorService().submit(new EventWriter());
    }

    @Override
    public void postNotify(RemoteServiceEvent event) {
        super.postNotify(event);
        eventWriteQ.offer(event);
    }

    @Override
    public int delete(Collection<RemoteServiceEvent> events) {
        for(RemoteServiceEvent event : events) {
            File file = new File(persistentEventDirectory, createEventFileName(event));
            if(file.exists()) {
                if(file.delete()) {
                    if(logger.isLoggable(Level.FINE))
                        logger.fine(String.format("Deleted %s", file.getName()));
                } else {
                    logger.warning(String.format("Could not delete %s", file.getName()));
                }
            } else {
                logger.warning(String.format("Could not delete %s, it does not exist", file.getName()));
            }
        }
        return super.delete(events);
    }

    /*
     * Added for testing support
     */
    File getPersistentEventDirectory() {
        return persistentEventDirectory;
    }

    @SuppressWarnings("unchecked")
    private List<RemoteServiceEvent> getPersistedEvents() {
        List<RemoteServiceEvent> events = new LinkedList<RemoteServiceEvent>();
        File[] files = persistentEventDirectory.listFiles();
        if(files==null) {
            logger.warning(String.format("%s returned null file array", persistentEventDirectory.getPath()));
            return events;
        }
        for(File file : files) {
            ObjectInputStream inputStream = null;
            try {
                inputStream = new ObjectInputStream(new FileInputStream(file));
                MarshalledObject<RemoteServiceEvent> mo = (MarshalledObject<RemoteServiceEvent>)inputStream.readObject();
                events.add(mo.get());
            } catch (Exception e) {
                logger.log(Level.SEVERE,
                           String.format("Could not read serialized event [%s] from disk", file.getPath()),
                           e);
            } finally {
                if(inputStream!=null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Trying to close OIS", e);
                    }
                }
            }
        }
        return events;
    }

    private String createEventFileName(RemoteServiceEvent event) {
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(event.getSequenceNumber()).append("-").append(event.getClass().getName()).append("-");
        nameBuilder.append(dateFormatter.format(event.getDate())).append(".evt");
        return nameBuilder.toString();
    }

    class EventWriter implements Runnable {

        public void run() {
            while (true) {
                RemoteServiceEvent event;
                try {
                    event = eventWriteQ.take();
                } catch (InterruptedException e) {
                    logger.fine("EventWriter breaking out of main loop");
                    break;
                }
                File file = new File(persistentEventDirectory, createEventFileName(event));
                if(logger.isLoggable(Level.FINE))
                    logger.fine(String.format("Writing %s to %s", event, file.getPath()));
                ObjectOutputStream outputStream = null;
                try {
                    outputStream = new ObjectOutputStream(new FileOutputStream(file));
                    outputStream.writeObject(new MarshalledObject<RemoteServiceEvent>(event));
                    if(logger.isLoggable(Level.FINE))
                        logger.fine(String.format("Wrote %d bytes to %s", file.length(), file.getPath()));
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Could not write to disk", e);
                } finally {
                    if(outputStream!=null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Trying to close OOS", e);
                        }
                    }
                }
            }
        }
    }
}