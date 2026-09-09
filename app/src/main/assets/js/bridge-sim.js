/* PureProbe bridge-sim.js — 浏览器模拟桥接（真机存在 window.AndroidPure 则跳过）
 * 与 bridge.js / JavaBridge 三处方法名必须一一对应（PROJECT_RULES） */
(function () {
  if (window.AndroidPure) return; // 真机环境，不用模拟

  var SIM_NODES = [
    { name: 'HK-01 IEPL', type: 'trojan', server: 'hk1.example.com' },
    { name: 'HK-02 IEPL', type: 'trojan', server: 'hk2.example.com' },
    { name: 'JP-01 BGP', type: 'vmess', server: 'jp1.example.com' },
    { name: 'SG-01', type: 'ss', server: 'sg1.example.com' },
    { name: 'US-01', type: 'vless', server: 'us1.example.com' },
    { name: 'TW-01', type: 'trojan', server: 'tw1.example.com' }
  ];
  // 模拟污染剧本：0死 1污染 2干净 3高风险 4干净 5不通
  var SIM_OUTCOME = ['dead', 'polluted', 'clean', 'risky', 'clean', 'dead'];

  var sim = {
    getSubs: function () {
      return JSON.stringify(window.PureState ? window.PureState.subs : []);
    },
    addSubscription: function (url) {
      var subs = window.PureState ? window.PureState.subs : [];
      var resp;
      if (!/^https?:\/\//.test(url)) {
        resp = { ok: false, error: 'URL 格式不对' };
      } else if (subs.some(function (s) { return s.url === url; })) {
        // 与真机一致：已存在 = 自动重拉（模拟直接成功）
        resp = { ok: true, count: SIM_NODES.length, nodes: SIM_NODES };
      } else {
        var sub = { id: 'sub-' + Date.now(), url: url, name: '模拟订阅', nodeCount: SIM_NODES.length, addedAt: Date.now() };
        subs.push(sub);
        resp = { ok: true, count: SIM_NODES.length, nodes: SIM_NODES };
      }
      // 与真机异步路径一致：走 onPureSubReady 回调（传对象，norm() 兼容两条路径）
      setTimeout(function () { window.onPureSubReady && window.onPureSubReady(resp); }, 100);
      return JSON.stringify({ ok: true, async: true });
    },
    removeSubscription: function (id) {
      var subs = window.PureState ? window.PureState.subs : [];
      window.PureState.subs = subs.filter(function (s) { return s.id !== id; });
      return JSON.stringify({ ok: true });
    },
    refreshSubscription: function (id) {
      var resp = { ok: true, count: SIM_NODES.length, nodes: SIM_NODES };
      setTimeout(function () { window.onPureSubReady && window.onPureSubReady(resp); }, 100);
      return JSON.stringify({ ok: true, async: true });
    },
    getNodes: function () {
      return JSON.stringify(window.PureState ? window.PureState.nodes : []);
    },
    startTest: function (nodeNamesJson, concurrency, timeoutSec, incremental) {
      var names = JSON.parse(nodeNamesJson);
      var nodes = window.PureState.nodes;
      var i = 0;
      var timer = setInterval(function () {
        if (i >= names.length || (window.PureState && !window.PureState.testing)) {
          clearInterval(timer);
          window.onPureTestDone && window.onPureTestDone(JSON.stringify({ ok: true, tested: i }));
          return;
        }
        var n = window.PureState.nodeByName(names[i]);
        if (n) {
          n.status = SIM_OUTCOME[i % SIM_OUTCOME.length];
          n.latency = n.status === 'clean' ? 100 + i * 37 : null;
          n.exitIp = n.status === 'dead' ? null : '104.28.' + (i + 1) + '.' + (10 + i);
          n.riskLevel = n.status === 'risky' ? 'high' : (n.status === 'clean' ? 'low' : null);
          n.checkedAt = Date.now();
        }
        i++;
        window.onPureProgress && window.onPureProgress(JSON.stringify({
          tested: i, total: names.length, current: n ? n.name : ''
        }));
      }, 700); // 模拟每节点 0.7s
      return JSON.stringify({ ok: true });
    },
    stopTest: function () {
      if (window.PureState) window.PureState.testing = false;
      return JSON.stringify({ ok: true });
    },
    clearCache: function () {
      var nodes = window.PureState ? window.PureState.nodes : [];
      nodes.forEach(function (n) { n.status = 'unknown'; n.latency = null; n.exitIp = null; n.riskLevel = null; n.checkedAt = null; });
      return JSON.stringify({ ok: true });
    },
    saveSettings: function (json) {
      if (window.PureState) Object.assign(window.PureState.settings, JSON.parse(json));
      return JSON.stringify({ ok: true });
    },
    getSettings: function () {
      return JSON.stringify(window.PureState ? window.PureState.settings : {});
    }
  };

  window.AndroidPure = sim;
  window.AndroidPure.__sim = true; // 标记模拟模式，init.js 用于显示
  if (window.__dbg) __dbg('bridge-sim 已加载（浏览器模拟模式）');
})();
