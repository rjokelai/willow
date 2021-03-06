package com.nitorcreations.willow.logging.jul;

import java.net.URISyntaxException;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import com.nitorcreations.willow.messages.LogMessage;
import com.nitorcreations.willow.messages.WebSocketTransmitter;

public class WebSocketLogHandler extends Handler {
  WebSocketTransmitter transmitter;

  public WebSocketLogHandler() {
    super();
    configure();
  }

  @Override
  public void publish(LogRecord record) {
    transmitter.queue(new LogMessage(record));
  }

  @Override
  public void flush() {
    //NOOP
  }

  @Override
  public void close() throws SecurityException {
    transmitter.stop();
  }

  private void configure() {
    LogManager manager = LogManager.getLogManager();
    String cname = getClass().getName();
    String uri = manager.getProperty(cname + ".uri");
    if (uri == null) {
      throw new RuntimeException("No uri configured for " + cname);
    }
    String flushInterval = manager.getProperty(cname + ".flushinterval");
    if (flushInterval == null) {
      flushInterval = "2000";
    }
    try {
      transmitter = WebSocketTransmitter.getSingleton(Integer.parseInt(flushInterval), uri);
      transmitter.start();
    } catch (NumberFormatException | URISyntaxException e) {
      throw new RuntimeException("Failed to configure " + cname, e);
    }
  }
}
