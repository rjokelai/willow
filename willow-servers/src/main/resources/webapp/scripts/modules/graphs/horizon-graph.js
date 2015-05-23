Box.Application.addModule('horizon-graph', function(context) {
  'use strict';

  var moduleElem, moduleConf,
      d3, $,
      store, windowSvc, utils, metricsService, cubismGraphs,
      initDone = false,
      messageQueue = [];
  var kilobyte = 1024, megabyte = 1024*1024, gigabyte = 1024*1024*1024, teratybe = 1024*1024*1024*1024;
  var bytesToString = function (bytes) {
    var fmt = d3.format('.0f');
    if (bytes < kilobyte) {
      return fmt(bytes) + 'B/s';
    } else if (bytes < megabyte) {
      return fmt(bytes /kilobyte) + 'kB/s';
    } else if (bytes < gigabyte) {
      return fmt(bytes / megabyte) + 'MB/s';
    } else if (bytes < teratybe) {
      return fmt(bytes / gigabyte) + 'GB/s';
    } else {
      return fmt(bytes / teratybe) + 'TB/s';
    }
  };

  var defaultColors = ["#08519c", "#3182bd", "#6baed6", "#bdd7e7", "#bae4b3", "#74c476", "#31a354", "#006d2c"];
  var cpuColors = ["rgb(84,39,143)", "rgb(117,107,177)", "rgb(158,154,200)", "rgb(203,201,226)", "rgb(242,240,247)",
                   'rgb(235,235,128)','rgb(254,204,92)','rgb(253,141,60)','rgb(240,59,32)','rgb(189,0,38)'];
  var metricMap = {
    "cpu" : { "title" : "cpu: ", "format" : ".2f", "extent": [0, 100], colors : cpuColors, height: 50 },
    "mem" : { "title" : "mem: ", "format" : ".2f", "extent": [0, 100], colors : cpuColors, height: 50 },
    "net" : { "title" : "net: ", "format" : bytesToString, "extent": undefined, colors : defaultColors, height: 50 },
    "diskio" : { "title" : "io: ", "format" : bytesToString, "extent": undefined, colors : defaultColors, height: 50 },
    "tcpinfo" : { "title" : "conn: ", "format" : ".0f", "extent": undefined, colors : defaultColors, height: 50 }
  };

  function resetGraph() {
    var chartConfig = moduleConf.chart;
    var metricSetting = $(metricMap).attr(chartConfig.metric);

    // TODO: this should be done by reconfiguring, not destroying
    moduleElem.selectAll(".horizon").remove();

    var id = moduleElem.attr('id');
    cubismGraphs.onFocus(function(index) {
      moduleElem.selectAll(".horizon .value").style("right", index === null ? null : this.size() - index + "px");
    }, id);

    $(".horizon").unbind("mousedown");
    var chartData = metricsChart(chartConfig.metric, chartConfig.instanceTag);
    moduleElem.call(createHorizon, chartConfig.host, chartConfig.metric, chartData, metricSetting);
  }

  // graph destroy, put this on a button or such
  function removeGraph() {
    cubismGraphs.onFocus(null);
    moduleElem.select(".horizon").call(cubismGraphs.removeHorizonGraph());
    moduleElem.remove();
  }

  function storeConfiguration(config) {
    store.storeConfiguration(moduleConf.configurationId, config);
  }

  // creating a new metrics chart every time graph is reset will not remove the old metric
  // TODO: figure out how to reset metrics, otherwise they'll keep ticking = sending requests
  function metricsChart(type, instanceTag) {
    return cubismGraphs.createMetrics(function(start, stop, step, callback) {
      var metricDataSource = metricsService.metricsDataSource(type, instanceTag, start.getTime(), stop.getTime(), step);
      metricDataSource(function(data) {
        if (data instanceof Error) {
          callback(data);
        }
        var parsedData = data.map(function(d) { return d.value; });
        callback(null, parsedData);
      });
    }, String(type));
  }

  var createHorizon = function(parentElement, host, metric, chart, metricSettings) {
    var horizonGraphElements = parentElement.selectAll(".horizon-" + host) //FIXME remove, this is currently empty selection
        .data([chart])
        .enter().append("div").classed("horizon", true);

    horizonGraphElements.append("div").classed('horizon__icons', true);

    horizonGraphElements.call(appendHorizonGraph, host, metric, metricSettings);
    if (!moduleConf.disableTerminalButton) {
      horizonGraphElements.call(appendTerminalIcon, host);
    }
    if (!moduleConf.disableRadiatorShareButton) {
      horizonGraphElements.call(appendShareRadiatorIcon, host);
    }

    horizonGraphElements.call(appendPopupGraphIcon, host);
    horizonGraphElements.call(appendHostRadiatorLink, metricSettings.title, host);
  };

  function appendHorizonGraph(parentElement, host, metric, metricSettings) {
    return parentElement
      .attr("data-metric", metric)
      .attr("data-host", host)
      .call(configureHorizonGraph(metricSettings));
  }

  function configureHorizonGraph(metricSettings) {
    var horizon = cubismGraphs.createHorizonGraph()
      .height(metricSettings.height)
      .colors(metricSettings.colors)
      .extent(metricSettings.extent)
      .title(null);
    if (typeof metricSettings.format == "function") {
      horizon.format(metricSettings.format);
    } else {
      horizon.format(d3.format(metricSettings.format));
    }
    return horizon;
  }

  function appendTerminalIcon(parentElement, host) {
    return parentElement.select('.horizon__icons').append("svg").attr("viewBox", "0 0 100 100")
        .classed("icon shape-terminal terminal-" + host, true)
        .attr("data-type", "start-terminal")
        .attr("data-host", host)
        .append("use").attr("xlink:href", "#shape-terminal");
  }

  function appendShareRadiatorIcon(parentElement, host) {
    return parentElement.select('.horizon__icons').append("svg").attr("viewBox", "0 0 100 100")
        .classed("icon share-" + host, true)
        .attr("data-type", "to-radiator")
        .append("use").attr("xlink:href", "#shape-to-radiator");
  }

  function appendPopupGraphIcon(parentElement, host) {
    return parentElement.select('.horizon__icons').append("svg").attr("viewBox", "0 0 100 100")
        .classed("icon popup-" + host, true)
        .attr("data-type", "to-popup")
        .append("use").attr("xlink:href", "#shape-external-link");
  }

  function appendHostRadiatorLink(parentElement, title, host) {
    return parentElement.append("div").classed("host-link", true)
        .text(title).append("a")
        .attr("href", "radiator.html#host=" + host)
        .attr("data-host", host)
        .attr("data-type", "host-radiator").text(host);
  }

  function openGraphInPopup() {
    var radiatorName = utils.guid(),
        url = '/graph.html#name=' + radiatorName;

    moduleConf.removeAfterUse = true;
    store.customRadiators.appendConfiguration(radiatorName, moduleConf);
    delete moduleConf.removeAfterUse; // TODO: clone the object instead of adding and removing prop

    windowSvc.popup({
      url: url
    });
  }

  //FIXME should these manipulations be in index level?
  function setMetric(metric) {
    var config = moduleConf.chart;
    config.metric = metric;
    storeConfiguration(config);
    resetGraph();
  }

  function execMessage(msg) {
    switch (msg.name) {
      case 'metric-changed':
        module.setMetric(msg.data);
        break;
      case 'cubism-context-reset':
      case 'reload-graph-configuration':
        module.resetGraph();
        break;
      case 'time-range-deselected':
        cubismGraphs.start();
        break;
      case 'time-range-selected':
        cubismGraphs.stop();
        break;
    }
  }

  function openRadiatorDialog() {
    context.broadcast("open-radiator-list", moduleConf.chart);
  }

  var module = {
    init: function() {
      $          = context.getGlobal("jQuery");
      d3         = context.getGlobal("d3");

      windowSvc  = context.getService("window");
      utils      = context.getService("utils");
      metricsService = context.getService("metrics");
      cubismGraphs = context.getService("cubism-graphs");
      store      = context.getService("configuration-store");

      moduleElem = d3.select(context.getElement());
      moduleConf = context.getConfig() || {};
      moduleConf.configurationId = moduleConf.configurationIdPrefix + moduleElem.attr('id');

      initDone = true;
      messageQueue.forEach(execMessage);
    },

    destroy: function() {
      removeGraph();
      moduleElem = null;
    },

    onclick: function(event, element, elementType) {
      var host = element ? element.getAttribute("data-host") : null;
      var user = element ? (element.getAttribute("data-user") ? element.getAttribute("data-user") : "@admin") : "@admin";
      switch (elementType) {
        case 'start-terminal':
          windowSvc.openTerminalToHost(user, host);
          break;
        case 'to-radiator':
          openRadiatorDialog();
          break;
        case 'to-popup':
          openGraphInPopup();
          break;
        case 'close':
          break;
        case 'host-radiator':
          windowSvc.openRadiatorForHost(host);
          event.preventDefault();
          break;
      }
    },

    messages: ["metric-changed", "reload-graph-configuration", "cubism-context-reset", "time-range-deselected", "time-range-selected"],

    onmessage: function(name, data) {
      var msg = { name: name, data: data };

      if (!initDone) {
        messageQueue.push(msg);
        return;
      }

      execMessage(msg);
    },

    openGraphInPopup: openGraphInPopup,
    resetGraph: resetGraph,
    setMetric: setMetric
  };

  return module;
});
