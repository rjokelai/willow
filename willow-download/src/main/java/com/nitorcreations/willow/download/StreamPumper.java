package com.nitorcreations.willow.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StreamPumper implements Runnable {
  private final InputStream in;
  private final OutputStream out;

  public StreamPumper(InputStream in, OutputStream out) {
    this.out = out;
    this.in = in;
  }

  @Override
  public void run() {
    byte[] buffer = new byte[4 * 1024];
    try {
      int read;
      while ((read = in.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      }
    } catch (IOException e) {
      Logger.getAnonymousLogger().log(Level.INFO, "Stream pumping exception", e);
    }
  }
}
