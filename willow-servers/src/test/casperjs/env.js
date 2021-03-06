var fs = require('fs');

// Test root given on command line as --home
exports.root = casper.cli.options.home

/**********************************************
  Some selectors for links
***********************************************/
exports.cpuLink        = { type: 'xpath', path: '//a[@id="cpu"]' };
exports.memLink        = { type: 'xpath', path: '//a[@id="mem"]' };
exports.netLink        = { type: 'xpath', path: '//a[@id="net"]' };
exports.diskLink       = { type: 'xpath', path: '//a[@id="diskio"]' };
exports.connLink       = { type: 'xpath', path: '//a[@id="tcpinfo"]' };
exports.cpuDiv         = { type: 'xpath', path: '//div[@data-metric="cpu"]'  }
exports.memDiv         = { type: 'xpath', path: '//div[@data-metric="mem"]'  }
exports.netDiv         = { type: 'xpath', path: '//div[@data-metric="net"]'  }
exports.diskDiv        = { type: 'xpath', path: '//div[@data-metric="diskio"]'  }
exports.connDiv        = { type: 'xpath', path: '//div[@data-metric="tcpinfo"]'  }
exports.radiatorNavDiv = { type: 'xpath', path: '//div[@data-module="radiator-controller"]'  }
exports.hostLink       = { type: 'xpath', path: '//a[@data-type="host-radiator"]' };
exports.alertsLink     = 'svg.shape-bell';
exports.heapDiv        = '#mod-heap-graph-1';

exports.toHostLink = "a[data-type=to-radiator]";
exports.toCustomRadiatorLink = "svg[data-type=to-radiator]";
exports.customRadiatorDialog = {
  modal: ".ui-dialog.ui-widget",
  newNameField: "#custom-radiator-list-dialog input[name=radiator-id]",
  createNewButton: "#custom-radiator-list-dialog #create",
  existingRadiatorLink: function(name) {
    return "#custom-radiator-list-dialog li[data-radiator-id=" + name + "]";
  }
};

exports.selectTimeScale = function(index) {
  var select = document.querySelector('#timescale');
  select.selectedIndex = index;
  var event = document.createEvent('HTMLEvents');
  event.initEvent("change", true, true);
  select.dispatchEvent(event);
  return true;
};

exports.graph = function(name) {
  var graphModuleSelector = "div[data-module=" + name +"]";
  return {
    module: graphModuleSelector,
    openHostRadiatorLink: graphModuleSelector + " a[data-type=host-radiator]",
    openToPopup: graphModuleSelector + " svg[data-type=to-popup]",
    addToRadiator: graphModuleSelector + " svg[data-type=to-radiator]",
    removeFromRadiator: graphModuleSelector + "  svg[data-type=close]",
    reOrderGraph: graphModuleSelector + " svg[data-type=graph-drag]"
  }
};

exports.defaultTimeOut = 10000;
exports.init = function() {
  //casper.options.logLevel = 'debug';
  casper.options.viewportSize = { width: 1920, height: 1080 };
  casper.page = casper.mainPage = null; //this will force recreate phatomjs instance
  casper.start();
  casper.setHttpAuth('admin', 'admin');
  casper.viewport(1920, 1080);
};

exports.writeCoverage = function(cspr, name) {
  writeIfPresent.call(cspr, "target/js-coverage/test-" + name + ".json");
  for (var i = 0; i < cspr.popups.length; i++) {
    cspr.withPopup(cspr.popups[i], function() {
      writeIfPresent.call(this, "target/js-coverage/test-" + name + "-" + i + ".json");
    });
  }

  function writeIfPresent(fileName) {
    var coverage = this.evaluate(function() {
      return window.__coverage__;
    });
    if (coverage) {
      _writeCoverage(coverage, fileName);
    }
  }

  function _writeCoverage(coverage, filename) {
    fs.write(filename, JSON.stringify(coverage), 'w');
  }
};

exports.screencapFailure = function(name) {
  return function() {
    this.capture("failed-screenshot-" + name + ".png");
  }
};

exports.assertHorizonGraph = function(elementSelector) {
  casper.test.assertVisible(elementSelector, "horizon graph should be visible");
};

exports.waitForAndClick = function(selector, name, waitTimeout) {
  casper.waitUntilVisible(selector, function() {
    this.click(selector);
  }, exports.screencapFailure(name), waitTimeout);
};

//FIXME sami-airaksinen: how I clear localstorage?
exports.clearLocalStorage = function() {
  casper.evaluate(function() {
    localStorage.clear();
  }, {});
};

casper.on('open', function(location) {
  this.echo("page opened to: " + location, "INFO");
});

casper.on('popup.created', function(webpage) {
  this.echo("url popup created from: " + this.page.url, "INFO");
});

casper.on('popup.loaded', function(webpage) {
  this.echo("url popup loaded to: " + webpage.url, "INFO");
});

casper.on('error', function(err) {
  this.capture("failed-on-error-" + err + "-screenshot.png")
});