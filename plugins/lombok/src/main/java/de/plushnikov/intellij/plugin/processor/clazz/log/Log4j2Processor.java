package de.plushnikov.intellij.plugin.processor.clazz.log;

import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class Log4j2Processor extends AbstractLogProcessor {

  private static final String LOGGER_TYPE = "org.apache.logging.log4j.Logger";
  private static final String LOGGER_CATEGORY = "%s.class";
  private static final String LOGGER_INITIALIZER = "org.apache.logging.log4j.LogManager.getLogger(%s)";

  public Log4j2Processor(@NotNull ConfigDiscovery configDiscovery) {
    super(configDiscovery, Log4j2.class, LOGGER_TYPE, LOGGER_INITIALIZER, LOGGER_CATEGORY);
  }
}
