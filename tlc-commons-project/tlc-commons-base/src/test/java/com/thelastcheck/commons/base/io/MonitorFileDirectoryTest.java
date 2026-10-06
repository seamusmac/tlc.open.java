/*******************************************************************************
 * Copyright (c) 2009-2015 The Last Check, LLC, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.thelastcheck.commons.base.io;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class MonitorFileDirectoryTest implements Observer {

    private static final Logger log = LoggerFactory.getLogger(MonitorFileDirectoryTest.class);

    private final AtomicInteger fileCount = new AtomicInteger();

    private void setup() throws IOException {
        createEmptyFile(Paths.get("target", "test1.tst1").toFile());
        createEmptyFile(Paths.get("target", "test1.tst2").toFile());
    }

    private void createEmptyFile(File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();
    }

    @Test
    public void testMonitor() throws Exception {
        setup();
        fileCount.set(0);
        MonitorFileDirectory monitor = new MonitorFileDirectory("target", "tst1", "tst2");
        monitor.addObserver(this);
        monitor.setStableTime(2);
        monitor.start();
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        monitor.stop();
        assertEquals(2, fileCount.get());
    }

    @Override
    public void update(Observable o, Object arg) {
        log.info("Processing file: " + arg);
        fileCount.incrementAndGet();
    }
}
