package com.nitorcreations.willow.servers;

import static java.lang.System.getProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jetty.servlet.DefaultServlet;

import com.google.inject.servlet.ServletModule;
import com.nitorcreations.willow.servlets.PropertyServlet;
import com.nitorcreations.willow.servlets.TestServlet;

public class ApplicationServletModule extends ServletModule {

  @Override
  protected void configureServlets() {
    String env =  getProperty("env", "dev");
    bind(MetricsServlet.class);
    bind(StatisticsServlet.class);
    bind(PropertyServlet.class).toInstance(new PropertyServlet());;
    bind(DefaultServlet.class).toInstance(new DefaultServlet());;
    bind(ExecutorService.class).toInstance(Executors.newCachedThreadPool());

    Map<String, String> defaultInit = new HashMap<>();
    defaultInit.put("dirAllowed", "false");
    defaultInit.put("gzip", "false");
    defaultInit.put("maxCacheSize","81920");
    defaultInit.put("maxCacheSize","81920");
    defaultInit.put("welcomeServlets", "true");

    if ("dev".equals(env)) {
      bind(ElasticsearchProxy.class);
      bind(TestServlet.class).toInstance(new TestServlet());;
      serve("/test/*").with(TestServlet.class);
      serve("/search/*").with(ElasticsearchProxy.class);
    }    
    serve("/metrics/*").with(MetricsServlet.class);
    serve("/properties/*").with(PropertyServlet.class);
    serve("/statistics/*").with(StatisticsServlet.class);
    serve("/rawterminal/*").with(RawTerminalServlet.class);
    serve("/*").with(DefaultServlet.class, defaultInit);
  }
}
