/* PureProbe test.js — 体检流程编排（前端侧）：组装节点名单、调桥接、收进度 */
(function () {
  var logs = [];

  function log(msg) {
    logs.unshift(new Date().toLocaleTimeString() + ' ' + msg);
    if (logs.length > 100) logs.pop();
    PureRender.renderLogs(logs);
  }

  function freshNodes(incremental, cacheHours) {
    var now = Date.now();
    var cutoff = cacheHours * 3600 * 1000;
    return PureState.nodes.filter(function (n) {
      if (!incremental) return true;
      // 增量：缓存内已测且未过期的跳过
      if (n.status && n.status !== 'unknown' && n.checkedAt && (now - n.checkedAt) < cutoff) return false;
      return true;
    }).map(function (n) { return n.name; });
  }

  window.PureTest = {
    isRunning: function () { return PureState.testing; },

    start: function () {
      if (PureState.testing) return;
      if (!PureState.nodes.length) { log('无节点，请先添加订阅'); return; }
      var names = freshNodes(PureState.settings.incremental, PureState.settings.cacheHours);
      if (!names.length) { log('增量模式下无待测节点（可清缓存或关闭增量）'); return; }
      PureState.testing = true;
      PureState.progress = { tested: 0, total: names.length, current: '' };
      PureState.save();
      PureRender.renderAll();
      log('开始体检 ' + names.length + ' 个节点，并发 ' + PureState.settings.concurrency);
      PureBridge.onProgress(function (p) {
        PureState.progress = p;
        PureRender.renderProgress();
      });
      PureBridge.onDone(function (r) {
        PureState.testing = false;
        PureState.save();
        PureRender.renderAll();
        log(r.ok ? ('体检完成，共测 ' + r.tested + ' 个') : ('体检失败: ' + (r.error || '未知')));
      });
      PureBridge.startTest(names, PureState.settings.concurrency, PureState.settings.timeoutSec, PureState.settings.incremental)
        .catch(function (e) {
          PureState.testing = false;
          PureRender.renderAll();
          log('启动失败: ' + e.message);
        });
    },

    stop: function () {
      if (!PureState.testing) return;
      PureBridge.stopTest().then(function () { log('已请求停止'); });
    },

    logs: function () { return logs; },
    log: log
  };
})();
