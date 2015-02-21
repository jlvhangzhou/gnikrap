/*
 * Gnikrap is a simple scripting environment for the Lego Mindstrom EV3
 * Copyright (C) 2014 Jean BENECH
 * 
 * Gnikrap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Gnikrap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Gnikrap.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gnikrap.script;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gnikrap.script.ev3api.SimpleEV3Brick;
import org.gnikrap.script.ev3api.SimpleEV3Keyboard.SimpleEV3Button;
import org.gnikrap.script.ev3api.xsensors.XSensor;
import org.gnikrap.script.ev3api.xsensors.XSensorManager;
import org.gnikrap.script.ev3api.xsensors.XSensorValue;
import org.gnikrap.utils.LoggerUtils;
import org.gnikrap.utils.ScriptApi;

/**
 * Enable to provide main entry point to access ev3 device to the script engine.
 */
public final class EV3ScriptContext {
  private static final Logger LOGGER = LoggerUtils.getLogger(EV3ScriptContext.class);

  private boolean running;
  private final SimpleEV3Button escape;
  private final SimpleEV3Brick ev3;

  // XSensors
  private final XSensorManager xsensor;
  private int xSensorActive = 0;

  // Configuration
  private final Configuration configuration = new Configuration();
  private int confIsRunningWait = 0;
  private boolean confIsRunningCheckEscapeKey = true;
  private int confWaitingTimeBeforeHardKill = 5000;
  private final ScriptExecutionManager ctx;

  public EV3ScriptContext(SimpleEV3Brick ev3, ScriptExecutionManager ctx, XSensorManager xsensor) {
    this.ev3 = ev3;
    this.ctx = ctx;
    this.xsensor = xsensor;
    if (ev3 != null) {
      escape = ev3.getKeyboard().getEscape();
    } else {
      escape = null;
    }
  }

  @ScriptApi
  public Configuration getConfiguration() {
    return configuration;
  }

  void start() {
    running = true;
  }

  void stop() {
    running = false;
  }

  @ScriptApi
  public void notify(String message) {
    try {
      ctx.sendBackMessage(EV3MessageBuilder.buildInfoUserMessage(message));
    } catch (IOException ioe) { // Should never happens
      LOGGER.log(Level.WARNING, "Exception ignored", ioe);
    }
  }

  /**
   * This method manage the script stop and also make the script thread friendly with the other threads.
   * 
   * @return true if the script can continue running, false if the script should stop.
   */
  @ScriptApi
  public boolean isOk() {
    if (running) {
      // Thread friendly
      if (confIsRunningWait == 0) {
        if (xSensorActive == 0) {
          Thread.yield();
        } else {
          // Wait a bit in order to give time to the XSensor message processing
          xSensorActive--;
          sleep(10);
        }
      } else {
        sleep(confIsRunningWait);
      }
      // Check button escape
      if (confIsRunningCheckEscapeKey && (escape != null) && escape.isDown()) {
        stop();
      }
    }
    return running;
  }

  /**
   * @return the object that enable to pilot the EV3 brick
   */
  @ScriptApi
  public SimpleEV3Brick getBrick() {
    return ev3;
  }

  // /**
  // * Return the object that enable to read external sensors values.
  // */
  // public XSensorManager getXSensors() {
  // return xsensor;
  // }

  /**
   * @return the XSensor with the given name.
   */
  @ScriptApi
  public XSensor getXSensor(String name) {
    return xsensor.getSensor(name);
  }

  /**
   * Stop the script.
   * <p>
   * Not a good implementation (Implementation with exception not good as they can be catch by the script)
   */
  // @ScriptApi
  public void exit() {
    ctx.stopScript();
  }

  /**
   * Set the value and change the settings in order to allocate a minimum of time for the XSensors events processing
   */
  void setXSensorFutureValue(String name, Future<XSensorValue> value) {
    xSensorActive = 10; // Maximum wait 10 * 10ms (xSensor streaming is one message/sensor each 50ms <=> 20 msgs/s => Were should be ok the 2 next messages)
    getXSensor(name).setFutureValue(value);
  }

  /**
   * Sleep for the given number of seconds
   */
  @ScriptApi
  public void sleepInS(float s) {
    sleep((long) (s * 1000));
  }

  /**
   * Sleep for the given number of milliseconds
   */
  @ScriptApi
  public void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  void releaseResources() {
    if (ev3 != null) {
      ev3.releaseResources();
    }
  }

  public class Configuration {

    /**
     * @param waitTime the waiting time in seconds, 0 means no wait.
     */
    @ScriptApi
    public Configuration setIsOkWait(int timeInMs) {
      if (timeInMs < 1) { // Avoid negative numbers
        confIsRunningWait = 0;
      } else {
        confIsRunningWait = timeInMs;
      }

      return this;
    }

    @ScriptApi
    public int getIsOkWait() {
      return confIsRunningWait;
    }

    @ScriptApi
    public Configuration setIsOkCheckEscapeKey(boolean checkEscapeKey) {
      confIsRunningCheckEscapeKey = checkEscapeKey;

      return this;
    }

    @ScriptApi
    public boolean isIsOkCheckEscapeKey() {
      return confIsRunningCheckEscapeKey;
    }

    /**
     * @param time waiting time before hard kill of the script, between [500, 30000]
     */
    @ScriptApi
    public Configuration setWaitingTimeBeforeHardKill(int timeIsMs) {
      if (timeIsMs < 1000) { // Wait at least 1 second
        confWaitingTimeBeforeHardKill = 1000;
      } else if (timeIsMs < 60000) { // Wait maximum 1 minute
        confWaitingTimeBeforeHardKill = timeIsMs;
      } else {
        confWaitingTimeBeforeHardKill = 60000;
      }

      return this;
    }

    @ScriptApi
    public int getWaitingTimeBeforeHardKill() {
      return confWaitingTimeBeforeHardKill;
    }
  }
}
