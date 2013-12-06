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

import interactivespaces.util.concurrency.CancellableLoop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.List;
import com.google.common.collect.Maps;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

/**
 * A Runnable for reading events from an evdev device.
 * Emits onInputEvent when an event is read.
 * Emits onError when an error occurs post-startup.
 *
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class EvdevReaderLoop extends CancellableLoop {

  /**
   * The size of a raw event struct.
   */
  public static final int EVENT_SZ = 24;

  /**
   * Byte offset of a raw event struct's <code>type</code> attribute.
   */
  public static final int OFFSET_TYPE  = 16;

  /**
   * Byte offset of a raw event struct's <code>code</code> attribute.
   */
  public static final int OFFSET_CODE  = 18;

  /**
   * Byte offset of a raw event struct's <code>value</code> attribute.
   */
  public static final int OFFSET_VALUE = 20;

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
   * A buffer for parsed event data.
   */
  private Map<String, Object> event;

  /**
   * The list of attached event listeners.
   */
  private final List<InputEventListener> listeners = Lists.newCopyOnWriteArrayList();

  /**
   * Adds an event listener.
   *
   * @param listener an instance implementing the InputEventListener interface
   */
  public void addListener(InputEventListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes an event listener.
   *
   * @param listener an instance implementing the InputEventListener interface
   */
  public void removeListener(InputEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Grabs an <code>int</code> out of a <code>ByteBuffer</code>.
   * Flips endian-ness for compatibility.
   *
   * @param raw   a ByteBuffer
   * @param start the starting index of the desired int
   * @return      an int pulled from the buffer
   */
  private static int sliceToInt(ByteBuffer buffer, int start) {
    return (int)(
        (buffer.get(start)   & 0xFF)
      | (buffer.get(start+1) & 0xFF) << 8
      | (buffer.get(start+2) & 0xFF) << 16
      | (buffer.get(start+3) & 0xFF) << 24
      );
  }

  /**
   * Grabs a <code>short</code> out of a <code>ByteBuffer</code>.
   * Flips endian-ness for compatibility.
   *
   * @param raw   a ByteBuffer
   * @param start the starting index of the desired short
   * @return      a short pulled from the buffer
   */
  private static short sliceToShort(ByteBuffer buffer, int start) {
    return (short)(
      (buffer.get(start)   & 0xFF)
    | (buffer.get(start+1) & 0xFF) << 8
    );
  }

  /**
   * Creates an EventReaderLoop instance with the given device location.
   *
   * @param location the filesystem location of a readable input device
   * @throws Exception
   */
  public EvdevReaderLoop(String location) throws Exception {
    eventBuffer = ByteBuffer.allocate(EVENT_SZ);
    event = Maps.newHashMap();

    File deviceNode = new File(location);

    // validate existence and permission before creating the stream
    if (!deviceNode.canRead())
      throw new Exception("Can not read from file: " + location);

    try {
      deviceStream = new FileInputStream(deviceNode);
    } catch (FileNotFoundException e) {
      throw new Exception("File not found: " + location);
    }

    deviceChannel = deviceStream.getChannel();
  }

  /**
   * Reads an event from the device and injects them into the event map.
   *
   * @throws InterruptedException
   */
  @Override
  protected void loop() throws InterruptedException {
    eventBuffer.clear();

    try {
      while (eventBuffer.hasRemaining())
        deviceChannel.read(eventBuffer);
    } catch (IOException e) {
      handleError(new Exception("Caught an exception while reading from the device channel"));
    }

    eventBuffer.flip();

    event.put( "type",  sliceToShort ( eventBuffer, OFFSET_TYPE  ));
    event.put( "code",  sliceToShort ( eventBuffer, OFFSET_CODE  ));
    event.put( "value", sliceToInt   ( eventBuffer, OFFSET_VALUE ));

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
   * Handles an uncaught exception in the loop by routing it to the error handler.
   *
   * @param exception an uncaught exception
   */
  @Override
  protected void handleException(Exception exception) {
    handleError(exception);
  }

  /**
   * Handles errors by notifying listeners and cancelling the loop.
   *
   * @param error an exception describing the error condition
   */
  private void handleError(Exception error) {
    for (InputEventListener listener : listeners) {
      listener.onError(error);
    }

    cancel();
  }

  /**
   * An interface for input event listeners.
   */
  public interface InputEventListener {
    void onInputEvent(Map<String, Object> event);
    void onError(Exception error);
  }
}
