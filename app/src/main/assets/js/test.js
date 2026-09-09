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
      // 干净节点才享受缓存跳过；不通/污染节点每轮都重测（bug#15：全dead缓存导致永远测不了）
      if (n.status === 'clean' && n.checkedAt && (now - n.checkedAt) < cutoff) return false;
      return true;
    }).map(function (n) { return n.name; });
  }

  window.PureTest = {
    isRunning: function () { return PureState.testing; },

    start: function () {
      if (PureState.testing) return;
      if (!PureState.nodes.length) {
        PureRender.renderAll();
        PureTest.log('⚠️ 没有节点：先在上方添加订阅（需网络可直连机场）');
        return;
      }
      var names = freshNodes(PureState.settings.incremental, PureState.settings.cacheHours);
      if (!names.length) { log('增量模式下无待测节点（可清缓存或关闭增量）'); return; }
      PureState.testing = true;
      PureState.progress = { tested: 0, total: names.length, current: '' };
      PureState.save();
      PureRender.renderAll();
      log('开始体检 ' + names.length + ' 个节点，并发 ' + PureState.settings.concurrency);
      PureBridge.onProgress(function (p) {
        PureState.progress = p;
        // 实时合并单节点结果（进度回调带 node 字段）
        if (p.node && p.node.name) {
          var old = PureState.nodeByName(p.node.name);
          if (old) {
            old.status = p.node.status;
            old.latency = p.node.latency != null ? p.node.latency : null;
            old.exitIp = p.node.exitIp || null;
            old.riskLevel = p.node.riskLevel || null;
            old.checkedAt = p.node.checkedAt || Date.now();
          } else {
            PureState.nodes.push(p.node);
          }
        }
        PureRender.renderProgress();
        PureRender.renderStats();
      });
      PureBridge.onDone(function (r) {
        PureState.testing = false;
        PureState.save();
        // 完成后从原生重拉全量结果（防止漏合并），失败也刷
        PureBridge.getNodes().then(function (nodes) {
          if (nodes && nodes.length) PureState.nodes = nodes;
          PureRender.renderAll();
          log(r.ok ? ('体检完成，共测 ' + r.tested + ' 个') : ('体检失败: ' + (r.error || '未知')));
        }).catch(function () {
          PureRender.renderAll();
          log(r.ok ? ('体检完成，共测 ' + r.tested + ' 个') : ('体检失败: ' + (r.error || '未知')));
        });
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
