Box.Application.addModule('childcpu-graph', function(context) {
  'use strict';

  var nv, d3, host, windowSvc, metrics, store, utils;

  var moduleElement, moduleConf, detailsStart, detailsStop, detailsStep, isTimescaleLongerThanDay;
  var chart, chartData;

  var onmessage = function(parsedData) {
    if (!parsedData.length || !parsedData[0].values.length) { return; }
    utils.mergePoints(chartData, parsedData);
    chart.update();
  };

  function xTicks(d) {
    var date = new Date(d);
    return isTimescaleLongerThanDay ? utils.dayTimeFormat(date) : utils.timeFormat(date);
  }

  function calculateStep(timescale) {
    var steps = $(moduleElement[0]).width() / 2;
    detailsStep = parseInt((timescale * 1000) / steps);
  }

  function createChildCpuGraph(data) {
    chartData = data;
    nv.addGraph(function() {
      chart = nv.models.lineChart();
      chart.margin({right: 25});
      chart.xAxis.tickFormat(xTicks);
      chart.yAxis.tickFormat(d3.format('%'));
      moduleElement.select(".graph")
        .datum(data)
        .transition().duration(500)
        .call(chart);

      utils.configureSocket({
            start: detailsStart,
            stop: detailsStop,
            step: detailsStep,
            metricKey: moduleConf.chart.type,
            tags: [ "host_" + host ]
          }, onmessage);

      return chart;
    });
  }

  function reset() {
    isTimescaleLongerThanDay = windowSvc.getTimescale() > 86400;
    moduleElement.selectAll(".nv-graph").remove();
    moduleElement
      .append("div").classed('nv-graph', true)
      .append("svg").classed('graph', true);
    metrics.metricsDataSource("childcpu", "host_" + host, detailsStart, detailsStop, detailsStep)(createChildCpuGraph);
  }

  function removeGraph() {
    moduleElement.selectAll(".nv-graph").remove();
  }

  function openGraphInPopup() {
    var radiatorName = utils.guid(),
        url = '/radiator.html#name=' + radiatorName;

    moduleConf.removeAfterUse = true;
    store.customRadiators.appendConfiguration(radiatorName, moduleConf);
    delete moduleConf.removeAfterUse;

    windowSvc.popup({
      url: url,
      height: window.innerHeight * 0.75,
      width: window.innerWidth * 0.75
    });
  }

  return {
    init: function() {
      d3        = context.getGlobal("d3");
      nv        = context.getGlobal("nv");
      windowSvc = context.getService("window");
      metrics   = context.getService("metrics");
      store     = context.getService("configuration-store");
      utils     = context.getService("utils");

      moduleElement = d3.select(context.getElement());
      moduleConf = context.getConfig() || {};
      moduleConf.chart = moduleConf.chart || {
        type: 'childcpu',
        host: windowSvc.getHashVariable("host")
      };
      host = moduleConf.chart.host;
      detailsStop  = parseInt(new Date().getTime());
      var timescale = windowSvc.getTimescale();
      detailsStart = parseInt(detailsStop - (1000 * timescale));
      calculateStep(timescale);

      var graphIconsElem = moduleElement.append("div").classed("nv-graph__icons", true);
      graphIconsElem.call(utils.appendHostRadiatorLink, moduleConf.chart.type, host);
      graphIconsElem.call(utils.appendShareRadiatorIcon, host);
      graphIconsElem.call(utils.appendPopupGraphIcon, host);
      graphIconsElem.call(utils.appendRemovalButton, moduleElement.attr('id'));
      graphIconsElem.call(utils.appendDraggableHandleIcon);

      reset();
    },

    destroy: function() {
      moduleElement.remove();
      moduleElement = null;
    },

    behaviors: [ "legend-click" ],

    messages: [ "time-range-updated", "timescale-changed" ],

    onmessage: function(name, data) {
      switch (name) {
        case 'time-range-updated':
          detailsStart = data.start;
          detailsStop = data.stop;
          reset();
          break;
        case 'timescale-changed':
          detailsStop = new Date().getTime();
          detailsStart = detailsStop - (data * 1000);
          calculateStep(data);
          reset();
          break;
      }
    },

    onclick: function(event, element, elementType) {
      switch (elementType) {
        case 'to-popup':
          openGraphInPopup();
          break;
        case 'to-radiator':
          context.broadcast("open-radiator-list", moduleConf.chart);
          break;
      }
    }
  };
});
