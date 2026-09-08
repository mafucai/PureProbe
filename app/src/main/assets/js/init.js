/* PureProbe init.js — 入口：加载状态、绑定事件、首次渲染 */
(function () {
  function el(id) { return document.getElementById(id); }
  function msg(id, text, ok) {
    var box = el(id);
    box.textContent = text;
    box.className = 'msg ' + (ok ? 'ok' : 'err');
    setTimeout(function () { box.textContent = ''; }, 4000);
  }

  function switchTab(name) {
    document.querySelectorAll('.tab').forEach(function (t) {
      t.classList.toggle('active', t.dataset.tab === name);
    });
    document.querySelectorAll('.tab-panel').forEach(function (p) {
      p.classList.toggle('active', p.id === 'tab-' + name);
    });
  }

  function bind() {
    // Tab 切换
    document.querySelectorAll('.tab').forEach(function (t) {
      t.addEventListener('click', function () { switchTab(t.dataset.tab); });
    });

    // 订阅
    el('btn-add-sub').addEventListener('click', function () {
      var url = el('sub-url-input').value.trim();
      if (!url) { msg('sub-msg', '请先粘贴订阅链接', false); return; }
      PureBridge.addSubscription(url).then(function (r) {
        if (r.ok) {
          PureState.nodes = r.nodes || PureState.nodes;
          PureState.save();
          PureRender.renderAll();
          el('sub-url-input').value = '';
          PureTest.log('订阅已添加，解析到 ' + (r.nodes ? r.nodes.length : 0) + ' 个节点');
        } else {
          msg('sub-msg', r.error || '添加失败', false);
        }
      }).catch(function (e) { msg('sub-msg', '桥接失败: ' + e.message, false); });
    });

    el('btn-refresh-sub').addEventListener('click', function () {
      PureBridge.getSubs().then(function (subs) {
        PureState.subs = subs || [];
        PureRender.renderSubs();
        PureTest.log('订阅列表已刷新，共 ' + PureState.subs.length + ' 个');
      });
    });

    el('sub-list').addEventListener('click', function (ev) {
      var btn = ev.target.closest('.btn-del-sub');
      if (!btn) return;
      var id = btn.dataset.id;
      PureBridge.removeSubscription(id).then(function (r) {
        if (r.ok) {
          PureState.subs = PureState.subs.filter(function (s) { return s.id !== id; });
          PureState.save();
          PureRender.renderAll();
          PureTest.log('订阅已删除');
        }
      });
    });

    // 体检
    el('btn-start').addEventListener('click', function () { PureTest.start(); });
    el('btn-stop').addEventListener('click', function () { PureTest.stop(); });

    // 节点列表
    el('filter-status').addEventListener('change', function () {
      PureRender.renderNodeList(this.value);
    });

    el('btn-export').addEventListener('click', function () {
      var clean = PureState.nodes.filter(function (n) { return n.status === 'clean'; })
        .sort(function (a, b) { return (a.latency || 9999) - (b.latency || 9999); });
      if (!clean.length) { PureTest.log('没有干净节点可导出'); return; }
      var text = clean.map(function (n, i) {
        return (i + 1) + '. ' + n.name + '  ' + (n.latency || '-') + 'ms  ' + (n.exitIp || '');
      }).join('\n');
      var done = function () { PureTest.log('已导出 ' + clean.length + ' 个干净节点'); };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, function () { PureTest.log('复制失败，请手动选择'); });
      } else { done(); }
    });

    // 设置
    el('btn-save-settings').addEventListener('click', function () {
      var c = parseInt(el('set-concurrency').value, 10) || 8;
      var t = parseInt(el('set-timeout').value, 10) || 5;
      var h = parseInt(el('set-cache-hrs').value, 10) || 24;
      PureState.settings = {
        concurrency: Math.min(16, Math.max(1, c)),
        timeoutSec: Math.min(15, Math.max(2, t)),
        cacheHours: Math.min(168, Math.max(1, h)),
        incremental: el('set-incremental').checked
      };
      PureBridge.saveSettings(PureState.settings).then(function (r) {
        if (r.ok) {
          PureState.save();
          PureRender.renderAll();
          msg('set-msg', '设置已保存', true);
        }
      });
    });

    el('btn-clear-cache').addEventListener('click', function () {
      PureBridge.clearCache().then(function (r) {
        if (r.ok) {
          PureState.save();
          PureRender.renderAll();
          msg('set-msg', '缓存已清除', true);
          PureTest.log('缓存已清除');
        }
      });
    });
  }

  function loadSettingsForm() {
    el('set-concurrency').value = PureState.settings.concurrency;
    el('set-timeout').value = PureState.settings.timeoutSec;
    el('set-cache-hrs').value = PureState.settings.cacheHours;
    el('set-incremental').checked = !!PureState.settings.incremental;
  }

  document.addEventListener('DOMContentLoaded', function () {
    PureState.load();
    loadSettingsForm();
    bind();
    // 首次从桥接同步（真机时桥接侧有持久数据）
    PureBridge.getSubs().then(function (subs) {
      if (subs && subs.length) { PureState.subs = subs; }
      return PureBridge.getNodes();
    }).then(function (nodes) {
      if (nodes && nodes.length) { PureState.nodes = nodes; }
      PureRender.renderAll();
      PureTest.log('PureProbe 就绪' + (window.AndroidPure && !window.AndroidPure.__sim ? '（真机模式）' : '（浏览器模拟模式）'));
    }).catch(function (e) {
      PureRender.renderAll();
      __dbg('初始化桥接失败: ' + e.message, true);
    });
  });
})();
