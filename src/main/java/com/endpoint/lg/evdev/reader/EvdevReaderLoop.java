/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.endpoint.lg.evdev.reader;

import com.endpoint.lg.support.evdev.InputEvent;

import interactivespaces.util.concurrency.CancellableLoop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

/**
 * A Runnable for reading events from an evdev device. Emits onInputEvent when
 * an event is read. Emits onError when an error occurs post-startup.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class EvdevReaderLoop extends CancellableLoop {

  /**
   * A stream from which to create a channel.
   */
  private FileInputStream deviceStream;

  /**
   * A channel from which to read raw event data.
   */
  private FileChannel deviceChannel;

  /**
   * A buffer for raw event data.
   */
  private ByteBuffer eventBuffer;

  /**
   * The list of attached event listeners.
   */
  private final List<InputEventListener> listeners = Lists.newCopyOnWriteArrayList();

  /**
   * Adds an event listener.
   * 
   * @param listener
   *          an instance implementing the InputEventListener interface
   */
  public void addListener(InputEventListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes an event listener.
   * 
   * @param listener
   *          an instance implementing the InputEventListener interface
   */
  public void removeListener(InputEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Creates an EventReaderLoop instance with the given device location.
   * 
   * @param location
   *          the filesystem location of a readable input device
   * @throws Exception
   */
  public EvdevReaderLoop(String location) throws Exception {
    eventBuffer = ByteBuffer.allocate(InputEvent.EVENT_SZ);

    File deviceNode = new File(location);

    // validate existence and permission before creating the stream
    if (!deviceNode.canRead())
      throw new Exception("Can not read from file: " + location);

    try {

      deviceStream = new FileInputStream(deviceNode);
      deviceChannel = deviceStream.getChannel();

    } catch (Exception e) {

      cleanup();
      throw e;
    }
  }

  /**
   * Reads an event from the device and injects them into the event map.
   * 
   * @throws InterruptedException
   */
  @Override
  protected void loop() throws InterruptedException {
    InputEvent event;

    eventBuffer.clear();

    try {
      while (eventBuffer.hasRemaining())
        deviceChannel.read(eventBuffer);
    } catch (IOException e) {
      // ignore i/o closed by interrupt exceptions
      if (e.getClass() != java.nio.channels.ClosedByInterruptException.class)
        handleException(e);
      else
        return;
    }

    eventBuffer.flip();

    event = new InputEvent(eventBuffer);

    for (InputEventListener listener : listeners) {
      listener.onInputEvent(event);
    }
  }

  /**
   * Tears down the thread's file handles.
   */
  @Override
  protected void cleanup() {
    Closeables.closeQuietly(deviceChannel);
    Closeables.closeQuietly(deviceStream);
  }

  /**
   * Handles an uncaught exception in the loop by routing it to the error
   * handler.
   * 
   * @param exception
   *          an uncaught exception
   */
  @Override
  protected void handleException(Exception exception) {
    cancel();

    for (InputEventListener listener : listeners) {
      listener.onError(exception);
    }
  }

  /**
   * An interface for input event listeners.
   */
  public interface InputEventListener {
    void onInputEvent(InputEvent event);

    void onError(Exception error);
  }
}
