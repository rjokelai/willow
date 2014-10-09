package com.nitorcreations.willow.deployer;

import static com.nitorcreations.willow.deployer.PropertyKeys.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.ptql.ProcessQuery;
import org.hyperic.sigar.ptql.ProcessQueryFactory;

import com.nitorcreations.willow.messages.WebSocketTransmitter;


public abstract class AbstractLauncher implements LaunchMethod {
	protected final String PROCESS_IDENTIFIER = new BigInteger(130, new SecureRandom()).toString(32);
	protected final Set<String> launchArgs = new LinkedHashSet<String>();
	protected Properties launchProperties;
	protected URI statUri;
	protected WebSocketTransmitter transmitter = null;
	protected Process child;
	protected AtomicInteger returnValue= new AtomicInteger(-1);
	protected Map<String, String> extraEnv = new HashMap<>();
	protected File workingDir;
	protected String keyPrefix;
	protected AtomicBoolean running = new AtomicBoolean(true);
	protected AbstractStreamPumper stdout, stderr;
	private LaunchMethod postStop;
	private String name;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private int restarts=0;
	
	public long getProcessId() {
		Sigar sigar = new Sigar();
		long stopTrying = System.currentTimeMillis() + 1000 * 60;
		while (System.currentTimeMillis() < stopTrying) {
			try {
				ProcessQuery q = ProcessQueryFactory.getInstance().getQuery("Env." + ENV_KEY_DEPLOYER_IDENTIFIER + ".eq=" + PROCESS_IDENTIFIER);
				return q.findProcess(sigar);
			} catch (Throwable e) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
				}
			}
		}
		throw new RuntimeException("Failed to resolve pid");
	}

	@Override
	public Integer call() {
		return launch(extraEnv, getLaunchArgs());
	}
	@Override
	public void setProperties(Properties properties) {
		this.setProperties(properties, PROPERTY_KEY_PREFIX_LAUNCH);
	}
	protected Integer launch(String ... args) {
		return launch(new HashMap<String, String>(), args);
	}
	protected Integer launch(Map<String, String> extraEnv, String ... args) {
		while (running.get() && !Thread.interrupted()) {
			restarts++;
			String autoRestartDefault = "false";
			if (PROPERTY_KEY_PREFIX_LAUNCH.equals(keyPrefix)) {
				autoRestartDefault = "true";
			}
			name = launchProperties.getProperty(PROPERTY_KEY_DEPLOYER_NAME)
					+ "." + launchProperties.getProperty(PROPERTY_KEY_DEPLOYER_LAUNCH_INDEX);
			boolean autoRestart = Boolean.valueOf(launchProperties.getProperty(keyPrefix + PROPERTY_KEY_AUTORESTART, autoRestartDefault));
			running.set(autoRestart);
			Logger log = Logger.getLogger(name);
			ProcessBuilder pb = new ProcessBuilder(args);
			pb.environment().putAll(System.getenv());
			pb.environment().putAll(extraEnv);
			pb.environment().put(ENV_KEY_DEPLOYER_IDENTIFIER, PROCESS_IDENTIFIER);
			pb.environment().put(ENV_DEPLOYER_NAME, name);
			pb.directory(workingDir);
			log.info(String.format("Starting %s%n", pb.command().toString()));
			try {
				child = pb.start();
			} catch (IOException e) {
				LogRecord rec = new LogRecord(Level.WARNING, "Failed to start  process");
				rec.setThrown(e);
				log.log(rec);
			}
			try {
				if (transmitter != null) {
					stdout = new StreamLinePumper(child.getInputStream(), transmitter, "STDOUT");
					stderr = new StreamLinePumper(child.getErrorStream(), transmitter, "STDERR");
				} else {
					stdout = new LoggingStreamPumper(child.getInputStream(), Level.FINE, name);
					stderr = new LoggingStreamPumper(child.getErrorStream(), Level.INFO, name);
				}
				new Thread(stdout, name + "-child-stdout-pumper").start();
				new Thread(stderr, name + "-child-sdrerr-pumper").start();
				returnValue.set(child.waitFor());
			} catch (InterruptedException e) {
				LogRecord rec = new LogRecord(Level.WARNING, "Failed to start process stream pumpers");
				rec.setThrown(e);
				log.log(rec);
			} finally {
				if (keyPrefix.equals(PROPERTY_KEY_PREFIX_LAUNCH)) {
					String postStopStr = launchProperties.getProperty(PROPERTY_KEY_PREFIX_POST_STOP + PROPERTY_KEY_METHOD);
					if (postStopStr != null) {
						postStop = LaunchMethod.TYPE.valueOf(postStopStr).getLauncher();
						postStop.setProperties(launchProperties, PROPERTY_KEY_PREFIX_POST_STOP);
						long timeout = Long.valueOf(launchProperties.getProperty(PROPERTY_KEY_PREFIX_POST_STOP + PROPERTY_KEY_TIMEOUT, "5"));
						Future<Integer> ret = executor.submit(postStop);
						try {
							log.info("Post stop returned" + ret.get(timeout, TimeUnit.SECONDS));
						} catch (InterruptedException | ExecutionException
								| TimeoutException e) {
							log.info("Post stop failed: " + e.getMessage());
						}
					}
				}
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		return returnValue.get();
	}
	@Override
	public void stopRelaunching() {
		running.set(false);
	}
	@Override
	public int destroyChild() throws InterruptedException {
		if (child != null) {
			child.destroy();
			child.waitFor();
		}
		if (stderr != null) {
			stderr.stop();
		}
		if (stdout != null) {
			stdout.stop();
		}
		executor.shutdown();
		return getReturnValue();
	}
	public synchronized int getReturnValue() {
		return returnValue.get();
	}
	protected void addLauncherArgs(Properties properties, String prefix) {
		int i=0;
		String next = properties.getProperty(prefix + "[" + i + "]");
		while (next != null) {
			launchArgs.add(next);
			next = properties.getProperty(prefix + "[" + ++i  + "]");
		}
	}
	protected String[] getLaunchArgs() {
		return launchArgs.toArray(new String[launchArgs.size()]);
	}

	public void setProperties(Properties properties, String keyPrefix) {
		this.keyPrefix = keyPrefix;
		launchProperties = properties;
		try {
			String statisticsUri = properties.getProperty(PROPERTY_KEY_STATISTICS_URI);
			if (statisticsUri != null) {
				statUri = new URI(statisticsUri);
			}
		} catch (URISyntaxException e) {
		}
		try {
			if (statUri != null) {
				long flushInterval = Long.parseLong(properties.getProperty("statistics.flushinterval", "2000"));
				transmitter = WebSocketTransmitter.getSingleton(flushInterval, statUri.toString());
			}
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to initialize launcher", e);
		}
		String extraEnvKeys = properties.getProperty(keyPrefix + PROPERTY_KEY_EXTRA_ENV_KEYS);
		if (extraEnvKeys != null) {
			for (String nextKey : extraEnvKeys.split(",")) {
				extraEnv.put(nextKey.trim(), properties.getProperty((nextKey.trim())));
			}
		}
		workingDir = new File(properties.getProperty(keyPrefix + PROPERTY_KEY_LAUNCH_WORKDIR,
				properties.getProperty(PROPERTY_KEY_WORKDIR, ".")));
		
	}
	@Override
	public int restarts() {
		return restarts;
	}
}
