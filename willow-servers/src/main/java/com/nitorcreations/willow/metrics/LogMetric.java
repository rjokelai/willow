package com.nitorcreations.willow.metrics;

import java.util.Collection;

import javax.inject.Named;

import com.nitorcreations.willow.messages.LogMessage;
import com.nitorcreations.willow.messages.metrics.MetricConfig;

@Named("/log")
public class LogMetric extends FullMessageMetric<LogMessage, Collection<LogMessage>> {

  @Override
  protected Collection<LogMessage> processData(long start, long stop, int step, MetricConfig conf) {
    return rawData.values();
  }

}
