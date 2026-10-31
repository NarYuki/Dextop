// Exercise the shipped terminal page's touch selection and clipboard wiring without a PTY.
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { runInNewContext } = require('node:vm');
const assert = require('node:assert/strict');
const elements = new Map();
function element(id) {
  if (!elements.has(id)) {
    const classes = new Set();
    elements.set(id, {
      style: {}, listeners: {},
      classList: {
        contains: x => classes.has(x), remove: x => classes.delete(x),
        toggle(x, enabled = !classes.has(x)) { enabled ? classes.add(x) : classes.delete(x); },
      },
      setAttribute() {}, setPointerCapture() {},
      addEventListener(name, callback) { this.listeners[name] = callback; },
    });
  }
  return elements.get(id);
}
let terminal, selection, copied, pasted, mode, all = false;
class Terminal {
  constructor() {
    terminal = this; this.cols = 10; this.rows = 4;
    this.buffer = { active: { viewportY: 5 } }; this.modes = {};
    this.element = { querySelector: () => ({ getBoundingClientRect: () => ({ left: 0, top: 0, width: 100, height: 80, bottom: 80 }) }) };
  }
  loadAddon() {} open() {} focus() {} onData() {} onBinary() {} onTitleChange() {} write() {}
  select(col, row, length) { selection = { col, row, length }; }
  selectAll() { all = true; }
  getSelection() { return '日本語\nselected text'; }
  paste(text) { pasted = text; }
  scrollLines(n) { this.buffer.active.viewportY += n; }
}
const context = {
  Terminal, FitAddon: { FitAddon: class { fit() {} } },
  document: { getElementById: element, querySelectorAll: () => [] },
  ResizeObserver: class { observe() {} },
  Dextop: { fontSize: () => 14, ready() {}, resize() {}, selecting(x) { mode = x; }, copy(x) { copied = x; }, paste() { context.dextopPasteText('a\nb'); } },
  setInterval: () => 1, clearInterval() {}, window: null,
};
context.window = context;
const html = readFileSync(join(__dirname, '../android/app/src/main/assets/terminal/index.html'), 'utf8');
runInNewContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], context);
element('select').onclick();
assert.equal(mode, true);
const event = (x, y) => ({ isPrimary: true, button: 0, pointerId: 1, clientX: x, clientY: y, preventDefault() {} });
element('selection').listeners.pointerdown(event(25, 10));
element('selection').listeners.pointermove(event(45, 50));
assert.deepEqual(selection, { col: 2, row: 5, length: 23 });
element('selection').listeners.pointerup();
// Reverse drag must select the same range, including both endpoint cells.
element('selection').listeners.pointerdown(event(45, 50));
element('selection').listeners.pointermove(event(25, 10));
assert.deepEqual(selection, { col: 2, row: 5, length: 23 });
element('selection').listeners.pointercancel();
element('copy').onclick();
assert.equal(copied, '日本語\nselected text');
element('paste').onclick();
assert.equal(mode, false);
assert.equal(pasted, 'a\nb');
element('all').onclick();
assert.equal(all, true);
assert.equal(mode, true);
console.log('Terminal selection/clipboard checks passed');
