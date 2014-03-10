/*
 * Copyright (C) 2014 Google Inc.
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

import interactivespaces.util.concurrency.ManagedCommands;
import interactivespaces.util.concurrency.Updateable;
import interactivespaces.util.concurrency.UpdateableLoop;
import interactivespaces.util.data.json.JsonBuilder;
import interactivespaces.util.resource.ManagedResource;

import com.endpoint.lg.support.evdev.InputEventHandler;
import com.endpoint.lg.support.evdev.InputEventHandlers;
import com.endpoint.lg.support.evdev.InputKeyEvent;
import com.endpoint.lg.support.evdev.InputAbsState;
import com.endpoint.lg.support.evdev.InputRelState;
import com.endpoint.lg.support.evdev.InputEventTypes;
import com.endpoint.lg.support.evdev.InputEvent;

/**
 * A router for aggregating and publishing categorized input events. Key/button
 * events are published instantly. Axis events are published on a timed loop.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class EventRouter extends InputEventHandlers implements ManagedResource {
  /**
   * An interface for writing events out to the activity's routes.
   */
  public interface RosWriter {
    public void publishEvent(String channel, JsonBuilder json);
  }

  /**
   * Each axis is serialized and published at this frequency (in Hz).
   */
  public static final double REFRESH_RATE = 60.0;

  private RosWriter writer;
  private ManagedCommands commands;

  private UpdateableLoop absLoop;
  private InputAbsState absState;

  private UpdateableLoop relLoop;
  private InputRelState relState;

  /**
   * Spins up the router.
   * 
   * @param writer
   *          Ros writer for publishing events
   */
  public EventRouter(final RosWriter writer, ManagedCommands commands) {
    this.writer = writer;
    this.commands = commands;

    absState = new InputAbsState();

    Updateable absWriter = new Updateable() {
      public void update() {
        if (absState.isDirty()) {
          writer.publishEvent("abs", absState.getNonZeroAsJsonBuilder());
          absState.clear();
        }
      }
    };

    absLoop = new UpdateableLoop(absWriter);
    absLoop.setFrameRate(REFRESH_RATE);

    relState = new InputRelState();

    Updateable relWriter = new Updateable() {
      public void update() {
        if (relState.isDirty()) {
          writer.publishEvent("rel", relState.getDirtyAsJsonBuilder());
          relState.clear();
        }
      }
    };

    relLoop = new UpdateableLoop(relWriter);
    relLoop.setFrameRate(REFRESH_RATE);

    registerHandler(InputEventTypes.EV_KEY, InputEventHandlers.ALL_CODES, new InputEventHandler() {
      public void handleEvent(InputEvent event) {
        writer.publishEvent("key", InputKeyEvent.serialize(event));
      }
    });

    registerHandler(InputEventTypes.EV_ABS, InputEventHandlers.ALL_CODES, new InputEventHandler() {
      public void handleEvent(InputEvent event) {
        absState.update(event);
      }
    });

    registerHandler(InputEventTypes.EV_REL, InputEventHandlers.ALL_CODES, new InputEventHandler() {
      public void handleEvent(InputEvent event) {
        relState.update(event);
      }
    });
  }

  /**
   * Publish the complete EV_ABS state.
   */
  public void syncAbs() {
    writer.publishEvent("abs", absState.getNonZeroAsJsonBuilder());
    absState.clear();
  }

  @Override
  public void shutdown() {
    absLoop.cancel();
    relLoop.cancel();
  }

  @Override
  public void startup() {
    commands.submit(absLoop);
    commands.submit(relLoop);
  }
}
