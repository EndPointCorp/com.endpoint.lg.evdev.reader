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
import interactivespaces.util.data.json.JsonBuilder;
import interactivespaces.InteractiveSpacesException;

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
public class EvdevReaderActivity extends BaseRoutableRosActivity implements
    EvdevReaderLoop.InputEventListener, EvdevEventRouter.RosWriter {

  /**
   * Configuration key for the device location.
   */
  private static final String CONFIGURATION_NAME_DEVICE_LOCATION = "lg.evdev.device.location";

  /**
   * Configuration key for the device name.
   */
  private static final String CONFIGURATION_NAME_DEVICE_NAME = "lg.evdev.device.name";

  private EvdevReaderLoop loop;
  private EvdevEventRouter router;

  /**
   * Handles an incoming event.
   * 
   * @param event
   *          the incoming event
   */
  public void onInputEvent(InputEvent event) {
    publishEvent("raw", event.getJsonBuilder());
    router.handleEvent(event);
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
   * Forward messages from the EventRouter to Ros if the activity is active.
   * 
   * @param key
   *          the name of the route to publish on
   * @param json
   *          the message
   */
  public void publishEvent(String key, JsonBuilder json) {
    if (isActivated()) {
      sendOutputJsonBuilder(key, json);
    }
  }

  /**
   * Creates and starts a reader loop and a router.
   */
  @Override
  public void onActivitySetup() {
    try {
      loop =
          new EvdevReaderLoop(getConfiguration().getRequiredPropertyString(
              CONFIGURATION_NAME_DEVICE_LOCATION));
    } catch (Exception e) {
      throw new InteractiveSpacesException("Error while creating reader loop", e);
    }

    loop.addListener(this);
    getManagedCommands().submit(loop);

    router = new EvdevEventRouter(this, getManagedCommands());
    addManagedResource(router);
  }

  /**
   * Publishes the full EV_ABS state upon activation.
   * 
   * TODO: This is broken due to the isActivated() check in publishEvent().
   */
  @Override
  public void onActivityActivate() {
    router.syncAbs();
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
