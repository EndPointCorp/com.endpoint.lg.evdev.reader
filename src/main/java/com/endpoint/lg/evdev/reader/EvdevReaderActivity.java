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

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import interactivespaces.InteractiveSpacesException;

import com.endpoint.lg.support.evdev.InputEventTypes;

/**
 * This activity reads events from an input device and writes them to a route.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class EvdevReaderActivity extends BaseRoutableRosActivity implements
    EvdevReaderLoop.InputEventListener {

  /**
   * Configuration key for the device location.
   */
  private static final String CONFIGURATION_NAME_DEVICE_LOCATION = "lg.evdev.device.location";

  /**
   * Configuration key for EV_REL to EV_ABS conversion.
   */
  private static final String CONFIGURATION_NAME_REL_TO_ABS = "lg.evdev.device.relToAbs";

  private EvdevReaderLoop loop;
  private boolean relToAbs;

  /**
   * Handles an incoming event.
   * 
   * @param event
   *          the incoming event
   */
  public void onInputEvent(InputEvent event) {
    // convert from EV_REL to EV_ABS if configured
    if (relToAbs && event.getType() == InputEventTypes.EV_REL) {
      event.setType(InputEventTypes.EV_ABS);
    }

    sendOutputJsonBuilder("raw", event.getJsonBuilder());
  }

  /**
   * Handles errors in the reader loop.
   * 
   * @param error
   *          an exception describing the error condition
   */
  public void onError(Exception error) {
    throw new InteractiveSpacesException("Error in reader loop", error);
  }

  /**
   * Creates and starts a reader loop and a router.
   */
  @Override
  public void onActivitySetup() {
    relToAbs = getConfiguration().getPropertyBoolean(CONFIGURATION_NAME_REL_TO_ABS, false);

    try {
      loop =
          new EvdevReaderLoop(getConfiguration().getRequiredPropertyString(
              CONFIGURATION_NAME_DEVICE_LOCATION));
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
