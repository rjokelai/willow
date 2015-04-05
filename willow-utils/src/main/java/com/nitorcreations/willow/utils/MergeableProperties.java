package com.nitorcreations.willow.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.text.StrSubstitutor;

public class MergeableProperties extends Properties {
  public static final Pattern ARRAY_PROPERTY_REGEX = Pattern.compile("(.*?)\\[\\d*?\\](\\}?)$");
  public static final Pattern ARRAY_REFERENCE_REGEX = Pattern.compile("(\\$\\{)?(.*?)\\[last\\](.*)$");
  public static final Pattern SCRIPT_REGEX = Pattern.compile("(.*?)(\\<script\\>(.*?)\\<\\/script\\>)", Pattern.DOTALL + Pattern.MULTILINE);
  public static final String URL_PREFIX_CLASSPATH = "classpath:";
  public static final String INCLUDE_PROPERTY = "include.properties";
  private Logger log = Logger.getLogger(getClass().getName());
  private final String[] prefixes;
  private static final long serialVersionUID = -2166886363149152785L;
  private LinkedHashMap<String, String> table = new LinkedHashMap<>();
  private final HashMap<String, Integer> arrayIndexes = new HashMap<>();
  ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
  private final boolean allowScripts;

  protected MergeableProperties(Properties defaults, LinkedHashMap<String, String> values, boolean allowScripts, String... prefixes) {
    super(defaults);
    table.putAll(values);
    this.prefixes = prefixes;
    this.allowScripts = allowScripts;
  }

  protected MergeableProperties(Properties defaults, LinkedHashMap<String, String> values, String... prefixes) {
    this(defaults, values, true, prefixes);
  }

  public MergeableProperties(boolean allowScripts) {
    super();
    defaults = new Properties();
    prefixes = new String[] { "classpath:" };
    this.allowScripts = allowScripts;
  }

  public MergeableProperties() {
    this(true);
  }

  public MergeableProperties(boolean allowScipts, String... prefixes) {
    super();
    defaults = new Properties();
    this.prefixes = prefixes;
    this.allowScripts = allowScipts;
  }
  public MergeableProperties(String... prefixes) {
    this(true, prefixes);
  }

  public Properties merge(String name) {
    merge0(name);
    postMerge();
    return this;
  }

  public Properties merge(Properties prev, String name) {
    if (prev != null) {
      if (prev instanceof MergeableProperties) {
        putAll((MergeableProperties)prev);
      } else {
        putAll(prev);
      }
    }
    merge0(name);
    postMerge();
    return this;
  }

  private void postMerge() {
    boolean changed = true;
    while (changed) {
      changed = false;
      LinkedHashMap<String, String> finalTable = new LinkedHashMap<>();
      StrSubstitutor sub = new StrSubstitutor(table, "${", "}", '\\');
      for (Entry<String, String> next : table.entrySet()) {
        String origKey = next.getKey();
        String origValue = next.getValue();
        String key = sub.replace(origKey);
        String value = sub.replace(origValue);
        finalTable.put(key, value);
        changed = changed || !origKey.equals(key) || !origValue.equals(value);
      }
      table = finalTable;
    }
  }

  private String evaluate(String replace, boolean allowEval) {
    Matcher m = SCRIPT_REGEX.matcher(replace);
    StringBuffer ret = new StringBuffer();
    int end = 0;
    engine.put("self", this);
    while (m.find()) {
      ret.append(m.group(1));
      try {
        if (allowEval) {
          ret.append(engine.eval(m.group(3).toString()));
        } else {
          ret.append(m.group(3).toString());
        }
      } catch (ScriptException e) {
        ret.append(m.group(2));
        LogRecord rec = new LogRecord(Level.INFO, "Failed to execute javascript");
        rec.setThrown(e);
        log.log(rec);
      }
      end = m.end();
    }
    ret.append(replace.substring(end));
    return ret.toString();
  }

  public void deObfuscate(PropertySource source, String obfuscatedPrefix) {
    if (obfuscatedPrefix == null)
      return;
    LinkedHashMap<String, String> finalTable = new LinkedHashMap<>();
    for (Entry<String, String> next : table.entrySet()) {
      String value = next.getValue();
      if (value.startsWith(obfuscatedPrefix)) {
        value = source.getProperty(value.substring(obfuscatedPrefix.length()));
      }
      if (value == null) {
        value = next.getValue();
      }
      finalTable.put(next.getKey(), value);
    }
    table = finalTable;
  }
  public MergeableProperties getPrefixed(String prefix) {
    MergeableProperties ret = new MergeableProperties();
    for (Entry<String, String> next : table.entrySet()) {
      if (next.getKey().startsWith(prefix)) {
        String key = next.getKey().substring(prefix.length());
        while (key.startsWith(".")) {
          key = key.substring(1);
        }
        ret.put(key, next.getValue());
      }
    }
    return ret;
  }
  private InputStream getUrlInputStream(String url) throws IOException {
    InputStream in = null;
    if (url.startsWith(URL_PREFIX_CLASSPATH)) {
      in = getClass().getClassLoader().getResourceAsStream(url.substring(URL_PREFIX_CLASSPATH.length()));
      if (in == null) {
        throw new IOException("Resource " + url + " not found");
      }
    } else {
      URL toFetch = new URL(url);
      URLConnection conn = toFetch.openConnection();
      conn.connect();
      in = conn.getInputStream();
    }
    return in;
  }

  private void merge0(String name) {
    try (InputStream in = getUrlInputStream(name)) {
      if (in == null)
        throw new IOException();
      load(in);
    } catch (IOException e) {
      for (String nextPrefix : prefixes) {
        String url = nextPrefix + name;
        try (InputStream in1 = getUrlInputStream(url)) {
          load(in1);
        } catch (IOException e1) {
          this.log.log(Level.INFO, "Failed to render url: " + url);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<Entry<Object, Object>> entrySet() {
    @SuppressWarnings("rawtypes")
    Set ret = table.entrySet();
    return (Set<Entry<Object, Object>>) ret;
  }

  @Override
  public Object put(Object key, Object value) {
    return put(key, value, allowScripts);
  }
  public Object put(Object key, Object value, boolean allowEval) {
    String k = resolveIndexes((String) key);
    String v = resolveIndexes((String) value);
    StrSubstitutor sub = new StrSubstitutor(table, "@", "@", '\\');
    k = sub.replace(k);
    v = evaluate(sub.replace(v), allowEval);
    String prev = table.get(k);
    if (prev != null && "true".equalsIgnoreCase(table.get(k + ".readonly"))) {
      return prev;
    }
    if (INCLUDE_PROPERTY.equals(k)) {
      //Don't allow include if eval is disallowed
      if (allowEval) {
        merge0(v);
      }
      return null;
    }
    if (prev != null && table.get(k + ".appendchar") != null) {
      return table.put(k, prev + table.get(k + ".appendchar") + v);
    } else {
      return table.put(k, v);
    }
  }

  @Override
  public Object setProperty(String key, String value) {
    return put(key, value);
  }
  public Object setProperty(String key, String value, boolean allowEval) {
    return put(key, value, allowEval);
  }

  protected String resolveIndexes(String original) {
    String ret = original;
    Matcher m = ARRAY_REFERENCE_REGEX.matcher(ret);
    while (m.matches()) {
      String arrKey = m.group(2);
      Integer lastIndex = arrayIndexes.get(arrKey);
      String prefix = "";
      if (m.group(1) != null) {
        prefix = m.group(1);
      }
      if (lastIndex != null) {
        ret = prefix + arrKey + "[" + lastIndex + "]" + m.group(3);
        m = ARRAY_REFERENCE_REGEX.matcher(ret);
      } else {
        break;
      }
    }
    m = ARRAY_PROPERTY_REGEX.matcher(ret);
    if (m.matches()) {
      String arrKey = m.group(1);
      int i = 0;
      if (arrayIndexes.get(arrKey) != null) {
        i = arrayIndexes.get(arrKey).intValue() + 1;
      }
      while (table.containsKey(arrKey + "[" + i + "]")) {
        i++;
      }
      arrayIndexes.put(arrKey, Integer.valueOf(i));
      ret = arrKey + "[" + i + "]";
      if (m.group(2) != null) {
        ret = ret + m.group(2);
      }
    }
    return ret;
  }

  @Override
  public Enumeration<Object> keys() {
    return new ObjectIteratorEnumertion(table.keySet().iterator());
  }

  @Override
  public Object get(Object key) {
    return table.get(key);
  }

  @Override
  public String getProperty(String key) {
    String oval = table.get(key);
    return ((oval == null) && (defaults != null)) ? defaults.getProperty(key) : oval;
  }

  public List<String> getArrayProperty(String key, String suffix) {
    int i = 0;
    if (suffix == null)
      suffix = "";
    ArrayList<String> ret = new ArrayList<>();
    String next = getProperty(key + "[" + i + "]" + suffix);
    while (next != null) {
      ret.add(next);
      next = getProperty(key + "[" + ++i + "]" + suffix);
    }
    return ret;
  }

  public List<String> getArrayProperty(String key) {
    return getArrayProperty(key, null);
  }

  public void putAll(MergeableProperties toMerge) {
    boolean allowScripts = this.allowScripts && toMerge.allowScripts;
    for (Entry<String, String> next : toMerge.table.entrySet()) {
      put(next.getKey(), next.getValue(), allowScripts);
    }
  }

  public Set<Entry<String, String>> backingEntrySet() {
    return table.entrySet();
  }

  public Map<String, String> backingTable() {
    return table;
  }

  @Override
  public Object remove(Object key) {
    return table.remove((String) key);
  }

  @Override
  public String toString() {
    return table.toString();
  }

  @Override
  public Enumeration<?> propertyNames() {
    return new ObjectIteratorEnumertion(table.keySet().iterator());
  }

  @Override
  public Set<String> stringPropertyNames() {
    return table.keySet();
  }

  @Override
  public synchronized int size() {
    return table.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return table.isEmpty() && defaults.isEmpty();
  }

  @Override
  public synchronized Enumeration<Object> elements() {
    return null;
  }

  @Override
  public synchronized boolean contains(Object value) {
    return table.containsValue(value) || defaults.containsValue(value);
  }

  @Override
  public synchronized boolean containsKey(Object key) {
    return table.containsKey(key) || defaults.containsKey(key);
  }
  @Override
  public synchronized void clear() {
    table.clear();
  }

  @Override
  public synchronized Object clone() {
    return new MergeableProperties(defaults, table, allowScripts, prefixes);
  }

  @Override
  public Set<Object> keySet() {
    return new LinkedHashSet<Object>(table.keySet());
  }

  public Collection<Object> values() {
    return new LinkedHashSet<Object>(table.values());
  }
}
