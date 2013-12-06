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

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import interactivespaces.InteractiveSpacesException;

import com.endpoint.lg.evdev.reader.EvdevReaderLoop;

import java.util.Map;

/**
 * This activity reads events from an input device and routes them as JSON.
 * <p>
 * Lifecycle:
 * <p>
 * READY: doing nothing
 * <p>
 * RUNNING: passively reading events from the device
 * <p>
 * ACTIVE: routing events
 *
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class EvdevReaderActivity extends BaseRoutableRosActivity implements EvdevReaderLoop.InputEventListener {

  /**
   * Configuration key for the device location.
   */
  private static final String CONFIGURATION_NAME_DEVICE_LOCATION = "lg.evdev.device.location";

  /**
   * The current EventReaderLoop instance.
   */
  private EvdevReaderLoop loop;

  /**
   * Handles an incoming event.
   * The keys "type", "code", and "value" are expected with numeric values.
   * A method of the InputEventListener interface.
   *
   * @param event a map of the event data
   */
  public void onInputEvent(Map<String, Object> event) {
    if (isActivated())
      sendOutputJson("event", event);
  }

  /**
   * Handles errors in the reader loop.
   * A method of the InputEventListener interface.
   *
   * @param error an exception describing the error condition
   */
  public void onError(Exception error) {
    throw new InteractiveSpacesException("Error in reader loop", error);
  }

  /**
   * Creates and starts a reader loop.
   */
  @Override
  public void onActivitySetup() {
    try {
      loop = new EvdevReaderLoop(
        getConfiguration().getRequiredPropertyString(CONFIGURATION_NAME_DEVICE_LOCATION)
      );
    } catch (Exception e) {
      throw new InteractiveSpacesException("Error while creating reader loop", e);
    }

    loop.addListener(this);

    getManagedCommands().submit(loop);
  }

  /**
   * Ensures that the reader loop is still running.
   *
   * @return the status of the reader loop
   */
  @Override
  public boolean onActivityCheckState() {
    return loop.isRunning();
  }
}
