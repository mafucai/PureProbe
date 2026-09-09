/* PureProbe bridge.js — JS 侧统一桥接封装。方法名与 bridge-sim.js / JavaBridge 一一对应 */
window.PureBridge = {
  call: function (method) {
    var args = Array.prototype.slice.call(arguments, 1);
    if (!window.AndroidPure || typeof window.AndroidPure[method] !== 'function') {
      return Promise.reject(new Error('桥接方法不存在: ' + method));
    }
    try {
      var raw = window.AndroidPure[method].apply(window.AndroidPure, args);
      var data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      return Promise.resolve(data);
    } catch (e) {
      if (window.__dbg) __dbg('bridge.' + method + ': ' + e.message, true);
      return Promise.reject(e);
    }
  },
  // 便捷方法（与 JavaBridge/bridge-sim 方法名对应）
  getSubs: function () { return this.call('getSubs'); },
  addSubscription: function (url) { return this.call('addSubscription', url); },
  removeSubscription: function (id) { return this.call('removeSubscription', id); },
  refreshSubscription: function (id) { return this.call('refreshSubscription', id); },
  getNodes: function () { return this.call('getNodes'); },
  startTest: function (nodeNames, concurrency, timeoutSec, incremental) {
    return this.call('startTest', JSON.stringify(nodeNames), concurrency, timeoutSec, incremental);
  },
  stopTest: function () { return this.call('stopTest'); },
  clearCache: function () { return this.call('clearCache'); },
  saveSettings: function (settings) { return this.call('saveSettings', JSON.stringify(settings)); },
  getSettings: function () { return this.call('getSettings'); },

  // 原生回调入口（Java 调 window.onPureProgress / onPureTestDone / onPureSubReady，传 JSON 字符串）
  _progressCb: null, _doneCb: null, _subReadyCb: null,
  onProgress: function (cb) { this._progressCb = cb; },
  onDone: function (cb) { this._doneCb = cb; },
  onSubReady: function (cb) { this._subReadyCb = cb; }
};

window.onPureProgress = function (jsonOrObj) {
  try { PureBridge._progressCb && PureBridge._progressCb(norm(jsonOrObj)); }
  catch (e) { __dbg('onPureProgress: ' + e.message, true); }
};
window.onPureTestDone = function (jsonOrObj) {
  try { PureBridge._doneCb && PureBridge._doneCb(norm(jsonOrObj)); }
  catch (e) { __dbg('onPureTestDone: ' + e.message, true); }
};
window.onPureSubReady = function (jsonOrObj) {
  try { PureBridge._subReadyCb && PureBridge._subReadyCb(norm(jsonOrObj)); }
  catch (e) { __dbg('onPureSubReady: ' + e.message, true); }
};
// evaluateJavascript 传来的可能是对象也可能是 JSON 字符串（历史路径不一致），统一兼容
function norm(v) {
  if (typeof v === 'string') { try { return JSON.parse(v); } catch (e) { return { ok: false, error: v }; } }
  return v;
}
