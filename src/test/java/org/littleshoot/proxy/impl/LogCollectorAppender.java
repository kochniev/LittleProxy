package org.littleshoot.proxy.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class LogCollectorAppender extends AppenderSkeleton {

  List<String> logs = new ArrayList<>();
  
  Set<String> threadNames = new HashSet<>();

  @Override
  protected void append(LoggingEvent loggingEvent) {
    logs.add((String)loggingEvent.getMessage());
    threadNames.add(loggingEvent.getThreadName());
  }


  public Set<String> getThreadNames() {
    return threadNames;
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
    threadNames.clear();
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }
}
