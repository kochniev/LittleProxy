package org.littleshoot.proxy.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class LogCollectorAppender extends AppenderSkeleton {

  List<String> logs = new ArrayList<>();

  @Override
  protected void append(LoggingEvent loggingEvent) {
    logs.add((String)loggingEvent.getMessage());
  }
  
  
  public Set<String> getLogsContains(String contains) {
    return logs.stream().filter((s) -> s.contains(contains)).collect(Collectors.toSet());
  }

  @Override
  public void close() {
    clear();
  }

  public void clear() {
    logs.clear();
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }
}
