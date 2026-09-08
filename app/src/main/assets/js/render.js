/* PureProbe render.js — 全部 DOM 渲染。禁止未转义 innerHTML（用 esc()） */
(function () {
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  var STATUS_LABEL = { clean: '✅ 干净', polluted: '⚠️ 污染', dead: '⛔ 不通', risky: '🚫 高风险', unknown: '— 未测' };
  var STATUS_CLASS = { clean: 'st-clean', polluted: 'st-polluted', dead: 'st-dead', risky: 'st-risky', unknown: 'st-unknown' };
  var STATUS_ORDER = { clean: 0, polluted: 1, risky: 2, dead: 3, unknown: 4 };

  function el(id) { return document.getElementById(id); }

  window.PureRender = {
    esc: esc,

    renderSubs: function () {
      var box = el('sub-list');
      var subs = PureState.subs;
      if (!subs.length) { box.innerHTML = '<div class="hint">还没有订阅</div>'; return; }
      box.innerHTML = subs.map(function (s) {
        return '<div class="sub-item" data-id="' + esc(s.id) + '">' +
          '<div class="sub-main"><b>' + esc(s.name) + '</b>' +
          '<span class="hint"> ' + (s.nodeCount || '?') + ' 节点</span></div>' +
          '<button class="btn tiny btn-ref-sub" data-id="' + esc(s.id) + '">刷新</button>' +
          '<button class="btn tiny btn-del-sub" data-id="' + esc(s.id) + '">删除</button>' +
          '</div>';
      }).join('');
    },

    renderStats: function () {
      var s = PureState.summary();
      el('stat-total').textContent = s.total;
      el('stat-tested').textContent = s.tested;
      el('stat-clean').textContent = s.clean;
      el('stat-bad').textContent = s.bad;
    },

    renderProgress: function () {
      var p = PureState.progress;
      var wrap = el('progress-wrap');
      if (PureState.testing) wrap.classList.remove('hidden'); else wrap.classList.add('hidden');
      var pct = p.total ? Math.round(p.tested / p.total * 100) : 0;
      el('progress-bar').style.width = pct + '%';
      el('progress-text').textContent = PureState.testing
        ? ('测试中 ' + p.tested + '/' + p.total + ' · ' + (p.current || '…'))
        : (p.total ? '完成 ' + p.tested + '/' + p.total : '待开始');
      el('btn-start').classList.toggle('hidden', PureState.testing);
      el('btn-stop').classList.toggle('hidden', !PureState.testing);
    },

    nodeRow: function (n, rank) {
      var lat = n.latency != null ? n.latency + 'ms' : '—';
      var ip = n.exitIp || '—';
      var rankStr = rank != null ? '<span class="rank">' + rank + '</span>' : '';
      return '<div class="node-row ' + STATUS_CLASS[n.status || 'unknown'] + '">' +
        rankStr +
        '<div class="node-main"><b>' + esc(n.name) + '</b>' +
        '<span class="hint">' + esc(n.type || '') + ' · ' + esc(ip) + ' · ' + lat + '</span></div>' +
        '<span class="node-status">' + (STATUS_LABEL[n.status || 'unknown']) + '</span>' +
        '</div>';
    },

    renderRank: function () {
      var box = el('rank-list');
      var list = PureState.nodes.slice().sort(function (a, b) {
        var oa = STATUS_ORDER[a.status || 'unknown'], ob = STATUS_ORDER[b.status || 'unknown'];
        if (oa !== ob) return oa - ob;
        return (a.latency || 9999) - (b.latency || 9999);
      });
      if (!list.length) { box.innerHTML = '<div class="hint">添加订阅并体检后这里出排行榜</div>'; return; }
      box.innerHTML = list.slice(0, 50).map(function (n, i) { return PureRender.nodeRow(n, i + 1); }).join('');
    },

    renderNodeList: function (filter) {
      var box = el('node-list');
      var list = PureState.nodes.filter(function (n) {
        return filter === 'all' || (n.status || 'unknown') === filter;
      });
      el('node-count').textContent = '(' + list.length + ')';
      if (!list.length) { box.innerHTML = '<div class="hint">无匹配节点</div>'; return; }
      box.innerHTML = list.map(function (n) { return PureRender.nodeRow(n, null); }).join('');
    },

    renderLogs: function (logs) {
      el('log-list').innerHTML = logs.length
        ? logs.map(function (l) { return '<div class="log-line">' + esc(l) + '</div>'; }).join('')
        : '<div class="hint">无日志</div>';
    },

    renderAll: function () {
      this.renderSubs();
      this.renderStats();
      this.renderProgress();
      this.renderRank();
      this.renderNodeList(el('filter-status').value);
    }
  };
})();
