/* PureProbe state.js — 全局状态，无 DOM 依赖 */
window.PureState = {
  subs: [],            // [{id, url, name, nodeCount, addedAt}]
  nodes: [],           // [{name, type, server, status, latency, exitIp, riskLevel, checkedAt}]
  settings: {
    concurrency: 8,
    timeoutSec: 5,
    cacheHours: 24,
    incremental: true
  },
  testing: false,
  progress: { tested: 0, total: 0, current: '' },

  load() {
    try {
      var raw = localStorage.getItem('pureprobe_state');
      if (raw) {
        var d = JSON.parse(raw);
        if (d.subs) this.subs = d.subs;
        if (d.nodes) this.nodes = d.nodes;
        if (d.settings) Object.assign(this.settings, d.settings);
      }
    } catch (e) { if (window.__dbg) __dbg('state.load: ' + e.message, true); }
  },
  save() {
    try {
      localStorage.setItem('pureprobe_state', JSON.stringify({
        subs: this.subs,
        nodes: this.nodes,
        settings: this.settings
      }));
    } catch (e) { if (window.__dbg) __dbg('state.save: ' + e.message, true); }
  },
  activeSub() {
    return this.subs.length ? this.subs[this.subs.length - 1] : null;
  },
  nodeByName(name) {
    for (var i = 0; i < this.nodes.length; i++) if (this.nodes[i].name === name) return this.nodes[i];
    return null;
  },
  summary() {
    var s = { total: this.nodes.length, tested: 0, clean: 0, bad: 0 };
    this.nodes.forEach(function (n) {
      if (n.status && n.status !== 'unknown') {
        s.tested++;
        if (n.status === 'clean') s.clean++; else s.bad++;
      }
    });
    return s;
  }
};
