// The Detent dial for the web. This file ports the dial from the launcher.
// All sizes are design units. The phone screen is 412 units wide and 892 units high.
'use strict';

const W = 412, HD = 892, AX = 226, CY0 = 470, GAP = 54;
const CLOCK_TOP = 84, SEARCH_Y = 796;
const TAU = Math.PI * 2;
const FONT = '"JetBrains Mono", ui-monospace, monospace';

const mod = (a, n) => ((a % n) + n) % n;
const c01 = v => Math.min(1, Math.max(0, v));
const inOutCubic = t => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
const approach = (v, t, dt, f) => Math.abs(t - v) < .003 ? t : v + (t - v) * (1 - Math.pow(f, dt / 16));

export const LAYOUTS = [
  { name: 'soft · radial · double', r: 330, m: 'double', tpa: 4 },
  { name: 'round · radial · double', r: 200, m: 'double', tpa: 5 },
  { name: 'soft · radial · ticks', r: 330, m: 'ticks', tpa: 4 },
  { name: 'flat · radial · dots', r: 560, m: 'dots', tpa: 6 },
  { name: 'flat · radial · nodes', r: 560, m: 'nodes', tpa: 4 },
];

const rgb = c => [(c >> 16) & 255, (c >> 8) & 255, c & 255];
const T = (name, group, light, bg, fg, accent, wall, hand = 0x504945, wallLeft = null) =>
  ({ name, group, light, bg: rgb(bg), fg: rgb(fg), accent: rgb(accent), wall, hand: rgb(hand), wallLeft });
const om = (name, light, bg, fg, accent) => T(name, 'omarchy', light, bg, fg, accent, `walls/${name}.webp`);

export const THEMES = [
  T('dusk', 'phone', false, 0x1d2021, 0xebdbb2, 0xfe8019, null, 0x504945),
  T('ember', 'phone', false, 0x000000, 0xebe5dc, 0xdf6124, null, 0x3a3733),
  T('signal', 'phone', false, 0x000000, 0xffffff, 0xff453a, null, 0x3a3a3a),
  T('paper', 'phone', true, 0xf1ede4, 0x1c1a17, 0xc2410c, null, 0xcbc3b4),
  om('catppuccin', false, 0x1e1e2e, 0xcdd6f4, 0x89b4fa),
  om('catppuccin-latte', true, 0xeff1f5, 0x4c4f69, 0x1e66f5),
  om('ethereal', false, 0x060b1e, 0xffcead, 0x7d82d9),
  om('everforest', false, 0x2d353b, 0xd3c6aa, 0x7fbbb3),
  om('flexoki-light', true, 0xfffcf0, 0x100f0f, 0x205ea6),
  om('gruvbox', false, 0x282828, 0xd4be98, 0x7daea3),
  om('hackerman', false, 0x0b0c16, 0xddf7ff, 0x82fb9c),
  om('kanagawa', false, 0x1f1f28, 0xdcd7ba, 0xdcd7ba),
  T('last-horizon', 'omarchy', false, 0x0c0b0c, 0xfafcfb, 0xb59790, 'walls/last-horizon.webp', 0x504945,
    'walls/last-horizon-left.webp'),
  om('lumon', false, 0x16242d, 0xd6e2ee, 0x8bc9eb),
  om('lupine', true, 0xfafafa, 0x212121, 0x3264eb),
  om('matte-black', false, 0x121212, 0xbebebe, 0xe68e0d),
  om('miasma', false, 0x222222, 0xc2c2b0, 0x78824b),
  om('nord', false, 0x2e3440, 0xd8dee9, 0x81a1c1),
  om('osaka-jade', false, 0x111c18, 0xc1c497, 0x509475),
  om('retro-82', false, 0x05182e, 0xf6dcac, 0xfaa968),
  om('ristretto', false, 0x2c2525, 0xe6d9db, 0xf38d70),
  om('rose-pine', true, 0xfaf4ed, 0x575279, 0x56949f),
  om('solitude', false, 0x101315, 0xcacccc, 0x798186),
  om('starship', false, 0x1c1c1e, 0xf5f5f7, 0xff9f0a),
  om('tokyo-night', false, 0x1a1b26, 0xa9b1d6, 0x7aa2f7),
  T('vantablack', 'omarchy', false, 0x000000, 0xffffff, 0x8d8d8d, null, 0x505050),
  om('white', true, 0xffffff, 0x000000, 0x6e6e6e),
];

const mix = (a, b, t) => a.map((v, i) => Math.round(v * t + b[i] * (1 - t)));
export const css = (c, a = 1) => a === 1 ? `rgb(${c[0]},${c[1]},${c[2]})` : `rgba(${c[0]},${c[1]},${c[2]},${a})`;

function luminance(c) {
  const l = c.map(v => { v /= 255; return v <= .03928 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4); });
  return .2126 * l[0] + .7152 * l[1] + .0722 * l[2];
}

export function palette(t) {
  return {
    light: t.light, bg: t.bg, fg: t.fg, accent: t.accent, hand: t.hand,
    onAccent: luminance(t.accent) > .33 ? [17, 17, 17] : [255, 255, 255],
    muted: mix(t.fg, t.bg, .5), muted2: mix(t.fg, t.bg, .68),
    sheet: mix(t.fg, t.bg, .05), card: mix(t.fg, t.bg, .1),
  };
}

export const APPS = ['calculator', 'calendar', 'camera', 'chrome', 'clock', 'contacts', 'drive', 'files', 'gmail',
  'keep', 'maps', 'messages', 'music', 'phone', 'photos', 'play store', 'settings', 'weather', 'youtube'];

const QUICK = 'camera';

const DAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
const H24 = (() => {
  try {
    const hc = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hourCycle;
    return hc === 'h23' || hc === 'h24';
  } catch { return true; }
})();
const pad2 = v => String(v).padStart(2, '0');
const clockText = (d = new Date()) => (H24 ? pad2(d.getHours()) : String(d.getHours() % 12 || 12)) + ':' + pad2(d.getMinutes());
const dateText = (d = new Date()) => `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`;

// Images. Each wallpaper loads once and every dial redraws when it arrives.
const images = new Map();
const onImage = new Set();
function image(src) {
  let e = images.get(src);
  if (!e) {
    e = { img: new Image(), ok: false };
    e.img.onload = () => { e.ok = true; onImage.forEach(f => f()); };
    e.img.src = src;
    images.set(src, e);
  }
  return e.ok ? e.img : null;
}

// The dot-hand art is white dots on black. The dots take the hand colour and the black takes the background.
const tints = new Map();
function tinted(t, img) {
  let c = tints.get(t.name);
  if (c) return c;
  c = document.createElement('canvas');
  c.width = img.naturalWidth;
  c.height = img.naturalHeight;
  const g = c.getContext('2d');
  g.drawImage(img, 0, 0);
  const d = g.getImageData(0, 0, c.width, c.height);
  const p = d.data;
  for (let i = 0; i < p.length; i += 4) {
    const v = p[i] / 255;
    p[i] = t.bg[0] + (t.hand[0] - t.bg[0]) * v;
    p[i + 1] = t.bg[1] + (t.hand[1] - t.bg[1]) * v;
    p[i + 2] = t.bg[2] + (t.hand[2] - t.bg[2]) * v;
  }
  g.putImageData(d, 0, 0);
  tints.set(t.name, c);
  return c;
}

const walls = new Map();
function wallLayer(t, left, cw, ch) {
  const key = `${t.name}|${left}|${cw}x${ch}`;
  const hit = walls.get(key);
  if (hit) return hit;
  let img = image(t.wall ? (left && t.wallLeft ? t.wallLeft : t.wall) : 'hands.webp');
  if (!img) return null;
  if (!t.wall) img = tinted(t, img);
  const c = document.createElement('canvas');
  c.width = cw;
  c.height = ch;
  const g = c.getContext('2d');
  const iw = img.naturalWidth || img.width, ih = img.naturalHeight || img.height;
  const s = Math.max(cw / iw, ch / ih);
  g.save();
  if (!t.wall && left) { g.translate(cw, 0); g.scale(-1, 1); }
  g.drawImage(img, (cw - iw * s) / 2, (ch - ih * s) / 2, iw * s, ih * s);
  g.restore();
  if (t.wall) {
    const b = t.bg;
    let gr = g.createLinearGradient(0, 0, 0, ch);
    gr.addColorStop(0, css(b, .6)); gr.addColorStop(.32, css(b, .15));
    gr.addColorStop(.7, css(b, .2)); gr.addColorStop(1, css(b, .75));
    g.fillStyle = gr;
    g.fillRect(0, 0, cw, ch);
    // The side with the dial is darker, so the labels stay readable.
    gr = g.createLinearGradient(0, 0, cw, 0);
    gr.addColorStop(0, css(b, left ? .86 : .2)); gr.addColorStop(.5, css(b, .5)); gr.addColorStop(1, css(b, left ? .2 : .86));
    g.fillStyle = gr;
    g.fillRect(0, 0, cw, ch);
  }
  if (walls.size > 60) walls.clear();
  walls.set(key, c);
  return c;
}

// Dial geometry for one rim radius.
function geo(r, cy, ax = AX) {
  const step = GAP / r;
  return { r, cy, cx: ax + r, step, span: Math.min(6.5, 1.55 / step) };
}
const fitScale = chars => Math.max(1, 150 / (chars * 9));
const labelScale = (a, chars) => { const cl = Math.max(0, 1 - a); return Math.min(1 + .45 * cl * cl, fitScale(chars)); };
const labelRadius = (g, m) => g.r - (m === 'ticks' ? 28 : m === 'double' ? 34 : 22);
const CH = 9.15;

function labelPoseAt(d, chars, s, g, r, left) {
  const ang = d * g.step;
  const x0 = g.cx - r * Math.cos(ang), y = g.cy + r * Math.sin(ang);
  const w = chars * CH * s;
  const rd = left ? r + 8 : r - w - 8;
  const bx = g.cx - rd * Math.cos(ang), by = g.cy + rd * Math.sin(ang);
  return { x: left ? W - x0 : x0, y, rot: left ? ang : -ang, tx: left ? -1 : 0, bx: left ? W - bx : bx, by };
}

// The open animation in ms: grow, hold, wait, shrink.
const OPEN = [420, 700, 0, 380];

export class Dial {
  /**
   * @param canvas the canvas to draw on. Its width sets the scale.
   * @param o.layout layout index, o.theme theme index, o.still true for a preview that takes no input.
   */
  constructor(canvas, o = {}) {
    this.c = canvas;
    this.g = canvas.getContext('2d');
    this.frame = this.frame.bind(this);
    this.still = !!o.still;
    this.onChange = o.onChange || (() => {});
    this.names = APPS;
    this.unread = new Set(['gmail', 'messages']);
    this.pos = this.target = APPS.indexOf('gmail');
    this.lastIdx = Math.round(this.pos);
    this.vel = 0;
    this.spin = 'idle';
    this.pawl = 0;
    this.left = !!o.left;
    this.scrub = null;
    this.letter = '';
    this.drag = null;
    this.open = null;
    this.a = { labels: 1, clock: 1, search: 1, scrub: 0, wall: 1 };
    this.wall = null;
    this.prevWall = null;
    this.layoutIndex = o.layout ?? 0;
    this.radius = this.rT = LAYOUTS[this.layoutIndex].r;
    this.setTheme(o.theme ?? 0);
    this.clockShown = clockText();
    this.resize();
    new ResizeObserver(() => this.resize()).observe(canvas);
    onImage.add(() => this.redraw());
    if (!this.still) this.listen();
  }

  get layout() { return LAYOUTS[this.layoutIndex]; }
  get cy() { return CY0; }

  resize() {
    const r = this.c.getBoundingClientRect();
    if (!r.width) return;
    const dpr = Math.min(3, window.devicePixelRatio || 1);
    const w = Math.round(r.width * dpr), h = Math.round(r.width * HD / W * dpr);
    if (w === this.c.width && h === this.c.height) return;
    this.c.width = w;
    this.c.height = h;
    this.k = w / W;
    this.redraw();
  }

  redraw() { if (this.raf) return; this.draw(performance.now()); }

  setTheme(i) {
    this.themeIndex = i;
    this.theme = THEMES[i];
    this.pal = palette(this.theme);
    this.run();
  }

  setLayout(i) {
    if (i === this.layoutIndex) return;
    this.layoutIndex = i;
    this.rT = this.layout.r;
    this.run();
  }

  setSide(left) { this.left = left; this.run(); }

  run() {
    if (this.still) { this.redraw(); return; }
    if (this.raf) return;
    this.last = performance.now();
    this.raf = requestAnimationFrame(this.frame);
  }

  frame(now) {
    this.raf = 0;
    const dt = Math.min(48, Math.max(1, now - this.last));
    const busy = this.step(dt, now);
    this.draw(now);
    if (busy) this.run();
    this.last = now;
  }

  step(dt, now) {
    let p = this.pos;
    if (this.spin === 'fling') {
      p += this.vel * dt;
      this.vel *= Math.pow(.9965, dt);
      if (Math.abs(this.vel) < .004) {
        this.target = Math.round(p + Math.sign(this.vel) * .3);
        this.spin = 'settle';
      }
    } else if (this.spin === 'settle') {
      const d = this.target - p;
      p += d * (1 - Math.pow(.78, dt / 16));
      if (Math.abs(d) < .002) { p = this.target; this.spin = 'idle'; }
    }
    this.pos = p;
    let morph = false;
    if (Math.abs(this.radius - this.rT) > .3) {
      this.radius += (this.rT - this.radius) * (1 - Math.pow(.84, dt / 16));
      morph = true;
    } else this.radius = this.rT;
    this.pawl *= Math.pow(.8, dt / 16);
    const idx = Math.round(p);
    if (idx !== this.lastIdx) {
      this.lastIdx = idx;
      this.pawl = 1;
    }
    const a = this.a, o = this.open;
    const tg = {
      labels: o ? 0 : 1,
      clock: this.scrub ? .25 : 1,
      search: this.scrub || o ? 0 : 1,
      scrub: this.scrub ? 1 : 0,
      wall: 1,
    };
    let fading = false;
    for (const key in tg) {
      a[key] = approach(a[key], tg[key], dt, key === 'wall' ? .9 : .8);
      if (a[key] !== tg[key]) fading = true;
    }
    if (o) {
      o.t += dt;
      const [i, h, d, out] = OPEN;
      if (o.t >= i + h + d + out) this.open = null;
    }
    const done = this.spin === 'idle' && this.pawl < .01 && !morph && !fading && !this.open;
    if (done) this.pawl = 0;
    return !done;
  }

  openProgress() {
    const o = this.open;
    if (!o) return 0;
    const [i, h, d, out] = OPEN;
    let t = o.t;
    if (t < i) return inOutCubic(t / i);
    t -= i + h + d;
    if (t < 0) return 1;
    return t < out ? 1 - inOutCubic(t / out) : 0;
  }

  /** Redraws when the minute changes. */
  tick() {
    if (clockText() === this.clockShown) return;
    this.clockShown = clockText();
    this.redraw();
  }

  // Gestures. All coordinates are design units.

  mirX(x) { return this.left ? W - x : x; }

  zoneAt(x, y) {
    if (y < 200 || y > HD - 102) return 'empty';
    const g = geo(this.radius, this.cy);
    const dx = g.cx - this.mirX(x), dy = y - this.cy;
    const dist = Math.hypot(dx, dy);
    if (dist <= g.r - 200 || dist >= g.r + 40) return 'empty';
    const k = this.pos + Math.atan2(dy, dx) / g.step;
    if (Math.abs(k - this.pos) > g.span) return 'empty';
    return Math.round(k) === Math.round(this.pos) ? 'selected' : 'dial';
  }

  point(e) {
    const r = this.c.getBoundingClientRect();
    return [(e.clientX - r.left) / r.width * W, (e.clientY - r.top) / r.height * HD];
  }

  listen() {
    const c = this.c;
    c.addEventListener('pointerdown', e => {
      if (e.button !== 0) return;
      c.setPointerCapture(e.pointerId);
      this.onDown(...this.point(e));
    });
    c.addEventListener('pointermove', e => { if (this.drag) this.onMove(...this.point(e)); });
    c.addEventListener('pointerup', e => this.onUp(...this.point(e)));
    c.addEventListener('pointercancel', () => this.onCancel());
    c.addEventListener('wheel', e => {
      e.preventDefault();
      if (this.open || this.drag) return;
      this.wheelAcc = (this.wheelAcc || 0) + (e.deltaMode === 1 ? e.deltaY * 40 : e.deltaY);
      clearTimeout(this.wheelTimer);
      this.wheelTimer = setTimeout(() => { this.wheelAcc = 0; }, 220);
      while (Math.abs(this.wheelAcc) >= 50) {
        const s = Math.sign(this.wheelAcc);
        this.stepBy(s);
        this.wheelAcc -= 50 * s;
      }
    }, { passive: false });
    c.addEventListener('keydown', e => {
      if (this.open) return;
      if (e.key === 'j' || e.key === 'ArrowDown') this.stepBy(1);
      else if (e.key === 'k' || e.key === 'ArrowUp') this.stepBy(-1);
      else if (e.key === 'l' || e.key === 'Enter' || e.key === ' ') this.launchSelected();
      else return;
      e.preventDefault();
    });
  }

  onDown(x, y) {
    if (this.open) return;
    // The quick button and the voice button take their own taps.
    if (this.onQuick(x, y)) { this.btnDown = 'quick'; return; }
    if (this.onVoice(x, y)) { this.btnDown = 'voice'; return; }
    const t = performance.now();
    const d = {
      x0: x, y0: y, y, t0: t, moved: false, scrub: false, samples: [[t, this.pos]],
      caught: this.spin === 'fling' || (this.spin === 'settle' && Math.abs(this.target - this.pos) > .3),
    };
    const zone = this.zoneAt(x, y);
    this.drag = d;
    this.spin = 'drag';
    this.vel = 0;
    if (zone !== 'empty') {
      this.press = setTimeout(() => { if (this.drag === d && !d.moved) this.startScrub(); }, 450);
    }
    this.run();
  }

  onMove(x, y) {
    if (this.btnDown) return;
    const d = this.drag;
    if (!d) return;
    if (!d.moved && (Math.abs(y - d.y0) > 6 || Math.abs(x - d.x0) > 6)) {
      d.moved = true;
      clearTimeout(this.press);
    }
    this.pos -= (y - d.y) / GAP;
    d.y = y;
    const t = performance.now();
    d.samples.push([t, this.pos]);
    while (d.samples.length > 2 && t - d.samples[0][0] > 90) d.samples.shift();
    this.run();
  }

  onUp(x, y) {
    clearTimeout(this.press);
    if (this.btnDown) {
      const b = this.btnDown;
      this.btnDown = null;
      if (b === 'quick' && this.onQuick(x, y)) this.openQuick();
      if (b === 'voice' && this.onVoice(x, y)) this.onChange({ voice: true });
      return;
    }
    const d = this.drag;
    if (!d) return;
    this.drag = null;
    if (d.scrub) { this.endScrub(); return; }
    const t = performance.now();
    this.target = Math.round(this.pos);
    if (!d.moved && t - d.t0 < 450) {
      this.spin = 'settle';
      if (!d.caught) this.tapAt(x, y);
      this.run();
      return;
    }
    const [t0, p0] = d.samples[0];
    this.vel = Math.max(-.06, Math.min(.06, (this.pos - p0) / Math.max(16, t - t0)));
    this.spin = Math.abs(this.vel) > .006 ? 'fling' : 'settle';
    this.run();
  }

  onCancel() {
    clearTimeout(this.press);
    this.btnDown = null;
    const d = this.drag;
    if (!d) return;
    this.drag = null;
    if (d.scrub) { this.endScrub(); return; }
    this.target = Math.round(this.pos);
    this.spin = 'settle';
    this.run();
  }

  stepBy(n) {
    const base = this.spin === 'settle' ? this.target : Math.round(this.pos);
    this.target = base + n;
    this.spin = 'settle';
    this.run();
  }

  startScrub() {
    const n = this.names.length;
    const letters = [...new Set(this.names.map(a => a[0]))];
    const cur = this.names[mod(Math.round(this.pos), n)];
    this.scrubBase = Math.round(this.pos);
    const li = letters.indexOf(cur[0]);
    this.pos = this.target = this.lastIdx = li;
    this.spin = 'drag';
    this.drag.scrub = true;
    this.scrub = letters;
    this.run();
  }

  endScrub() {
    const letters = this.scrub;
    if (!letters) return;
    const letter = letters[mod(Math.round(this.pos), letters.length)];
    this.scrub = null;
    const n = this.names.length;
    const idx = this.names.findIndex(a => a[0] === letter);
    let d = idx - mod(this.scrubBase, n);
    if (d > n / 2) d -= n;
    if (d < -n / 2) d += n;
    this.pos = this.lastIdx = this.scrubBase;
    this.target = this.scrubBase + d;
    this.spin = 'settle';
    this.run();
  }

  tapAt(x, y) {
    const cur = Math.round(this.pos);
    if (this.zoneAt(x, y) === 'empty') return;
    const g = geo(this.radius, this.cy);
    const k = Math.round(this.pos + Math.atan2(y - this.cy, g.cx - this.mirX(x)) / g.step);
    if (k === cur) this.launchSelected();
    else this.launchAt(k);
  }

  launchSelected() { this.openApp(Math.round(this.target)); }

  launchAt(k) {
    this.target = k;
    this.spin = 'settle';
    this.openApp(k);
  }

  openApp(k) {
    const name = this.names[mod(k, this.names.length)];
    this.unread.delete(name);
    const g = geo(this.radius, this.cy);
    const p = labelPoseAt(k - this.pos, name.length, 1, g, labelRadius(g, this.layout.m), this.left);
    this.open = { name, t: 0, rx: p.x, ry: p.y };
    this.onChange({ open: name });
    this.run();
  }

  // The quick button sits in the bottom corner on the dial side. In the demo it holds the camera.

  quickBox() {
    this.font(13, 400, 0);
    const w = 14 + 14 + 10 + this.g.measureText(QUICK).width + 18;
    return { x: this.left ? 24 : W - 24 - w, y: SEARCH_Y, w, h: 44 };
  }

  onQuick(x, y) {
    if (this.a.search < .5) return false;
    const b = this.quickBox();
    return x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h;
  }

  openQuick() {
    const b = this.quickBox();
    // The sheet grows from the label.
    this.open = { name: QUICK, t: 0, rx: b.x + 38, ry: b.y + b.h / 2 };
    this.onChange({ open: QUICK });
    this.run();
  }

  // The voice button sits between search and the quick button. The demo has no microphone.

  searchBox() {
    this.font(13, 400, 0);
    const w = 14 + 14 + 10 + this.g.measureText('search').width + 18;
    return { x: this.left ? W - 24 - w : 24, y: SEARCH_Y, w, h: 44 };
  }

  voiceBox() {
    const s = this.searchBox();
    return { x: this.left ? s.x - 10 - 44 : s.x + s.w + 10, y: SEARCH_Y, w: 44, h: 44 };
  }

  onVoice(x, y) {
    if (this.a.search < .5) return false;
    const b = this.voiceBox();
    return x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h;
  }

  // Drawing.

  font(size, weight, spacing) {
    const g = this.g;
    g.font = `${weight} ${size}px ${FONT}`;
    if ('letterSpacing' in g) g.letterSpacing = `${spacing * size}px`;
  }

  draw(now) {
    const g = this.g, k = this.k, pal = this.pal;
    if (!k) return;
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.globalAlpha = 1;
    g.fillStyle = css(pal.bg);
    g.fillRect(0, 0, this.c.width, this.c.height);
    const wl = wallLayer(this.theme, this.left, this.c.width, this.c.height);
    if (wl && wl !== this.wall) {
      // A new wallpaper fades in over the old one. A resize swaps it at once.
      const fade = !this.still && this.wall && this.wall.width === wl.width;
      this.prevWall = this.wall;
      this.wall = wl;
      if (fade) { this.a.wall = 0; this.run(); }
    }
    if (this.prevWall && this.a.wall < 1 && this.prevWall.width === this.c.width) g.drawImage(this.prevWall, 0, 0);
    if (this.wall) {
      g.globalAlpha = this.still ? 1 : this.a.wall;
      g.drawImage(this.wall, 0, 0);
      g.globalAlpha = 1;
    }
    g.setTransform(k, 0, 0, k, 0, 0);
    this.drawClock();
    this.drawMarks();
    this.drawLabels();
    this.drawNotch();
    this.drawScrubLetter();
    this.drawSearch();
    this.drawVoice();
    this.drawQuick();
    this.drawOpen();
    this.drawStatus();
  }

  text(s, x, y, size, weight, color, alpha, spacing, align = 'left', base = 'middle') {
    const g = this.g;
    this.font(size, weight, spacing);
    g.textAlign = align;
    g.textBaseline = base;
    g.globalAlpha = alpha;
    g.fillStyle = css(color);
    g.fillText(s, x, y);
    g.globalAlpha = 1;
  }

  drawStatus() {
    const pal = this.pal, g = this.g;
    this.text(clockText(), 22, 24, 12, 500, pal.fg, .9, 0);
    g.globalAlpha = .9;
    g.strokeStyle = css(pal.fg);
    g.lineWidth = 1.2;
    g.beginPath();
    g.roundRect(W - 46, 19, 20, 10, 2.5);
    g.stroke();
    g.fillStyle = css(pal.fg);
    g.fillRect(W - 44, 21, 13, 6);
    g.fillRect(W - 25.5, 22, 1.6, 4);
    g.globalAlpha = 1;
  }

  drawClock() {
    const pal = this.pal, left = this.left, a = this.a.clock;
    const x = left ? W - 28 : 28, al = left ? 'right' : 'left';
    this.text(clockText(), x, CLOCK_TOP, 68, 300, pal.fg, a, -.05, al, 'top');
    this.text(dateText(), x, CLOCK_TOP + 68 + 8, 13, 400, pal.muted2, a, .02, al, 'top');
  }

  drawSearch() {
    const a = this.a.search;
    if (a <= .004) return;
    const g = this.g, pal = this.pal;
    const { x, w, h } = this.searchBox(), y = SEARCH_Y + (1 - a) * 8;
    g.globalAlpha = a;
    g.fillStyle = css(pal.bg, .55);
    g.beginPath();
    g.roundRect(x, y, w, h, h / 2);
    g.fill();
    g.strokeStyle = css(pal.fg, .22);
    g.lineWidth = 1;
    g.stroke();
    g.strokeStyle = css(pal.fg);
    g.lineWidth = 1.5;
    const ix = x + 14, iy = y + 15;
    g.beginPath();
    g.arc(ix + 5, iy + 5, 4.25, 0, TAU);
    g.moveTo(ix + 8.5, iy + 8.5);
    g.lineTo(ix + 12.74, iy + 12.74);
    g.stroke();
    this.text('search', ix + 24, y + h / 2, 13, 400, pal.fg, a, 0);
  }

  drawVoice() {
    const a = this.a.search;
    if (a <= .004) return;
    const g = this.g, pal = this.pal, b = this.voiceBox(), y = b.y + (1 - a) * 8;
    g.globalAlpha = a;
    g.fillStyle = css(pal.bg, .55);
    g.beginPath();
    g.arc(b.x + 22, y + 22, 22, 0, TAU);
    g.fill();
    g.strokeStyle = css(pal.fg, .22);
    g.lineWidth = 1;
    g.stroke();
    g.strokeStyle = css(pal.fg);
    g.lineWidth = 1.5;
    const ix = b.x + 15, iy = y + 15;
    g.beginPath();
    g.roundRect(ix + 4.5, iy + .75, 5, 8, 2.5);
    g.stroke();
    g.beginPath();
    g.ellipse(ix + 7, iy + 7.5, 5, 4, 0, 0, Math.PI);
    g.moveTo(ix + 7, iy + 11.5);
    g.lineTo(ix + 7, iy + 13.5);
    g.stroke();
    g.globalAlpha = 1;
  }

  drawQuick() {
    const a = this.a.search;
    if (a <= .004) return;
    const g = this.g, pal = this.pal, b = this.quickBox(), y = b.y + (1 - a) * 8;
    g.globalAlpha = a;
    g.fillStyle = css(pal.bg, .55);
    g.beginPath();
    g.roundRect(b.x, y, b.w, b.h, b.h / 2);
    g.fill();
    g.strokeStyle = css(pal.fg, .22);
    g.lineWidth = 1;
    g.stroke();
    g.globalAlpha = 1;
    this.dot(b.x + 21, y + b.h / 2, 3.5, pal.fg, a);
    this.text(QUICK, b.x + 38, y + b.h / 2, 13, 400, pal.fg, a, 0);
  }

  drawScrubLetter() {
    const a = this.a.scrub;
    if (this.scrub) this.letter = this.scrub[mod(Math.round(this.pos), this.scrub.length)];
    if (a <= .004 || !this.letter) return;
    const x = this.left ? W - 36 : 36;
    this.text(this.letter, x, this.cy + (1 - a) * 6, 112, 300, this.pal.accent, a, -.04, this.left ? 'right' : 'left');
  }

  drawOpen() {
    const o = this.open;
    if (!o) return;
    const v = this.openProgress(), g = this.g, pal = this.pal;
    const far = Math.max(Math.hypot(o.rx, o.ry), Math.hypot(W - o.rx, o.ry), Math.hypot(o.rx, HD - o.ry), Math.hypot(W - o.rx, HD - o.ry));
    g.fillStyle = css(pal.sheet);
    g.beginPath();
    g.arc(o.rx, o.ry, Math.max(0, v * far), 0, TAU);
    g.fill();
    const t = c01((v - .7) / .3);
    if (t > 0) this.text(o.name, W / 2, HD / 2 - 10, 30, 300, pal.fg, t, -.02, 'center');
  }

  drawMarks() {
    const g = this.g, pal = this.pal, L = this.layout, m = L.m, left = this.left, pos = this.pos;
    const gg = geo(this.radius, this.cy);
    const x = v => left ? W - v : v;
    if (m === 'ticks' || m === 'nodes') {
      g.strokeStyle = css(pal.fg, .14);
      g.lineWidth = 1;
      g.beginPath();
      g.arc(x(gg.cx), gg.cy, gg.r, 0, TAU);
      g.stroke();
    }
    const tpa = L.tpa;
    const first = Math.ceil((pos - gg.span) * tpa), last = Math.floor((pos + gg.span) * tpa);
    for (let i = first; i <= last; i++) {
      const d = i / tpa - pos, a = Math.abs(d);
      if (a > gg.span) continue;
      const ang = d * gg.step;
      const px = gg.cx - gg.r * Math.cos(ang), py = gg.cy + gg.r * Math.sin(ang);
      const major = mod(i, tpa) === 0, hot = major && a < .5;
      const fade = Math.pow(1 - a / gg.span, .8);
      if (m === 'ticks') {
        const w = major ? 14 : 6, sg = left ? -1 : 1;
        g.strokeStyle = css(pal.fg, (major ? .6 : .25) * fade);
        g.lineWidth = 1;
        g.beginPath();
        g.moveTo(x(px), py);
        g.lineTo(x(px) + sg * w * Math.cos(ang), py - w * Math.sin(ang));
        g.stroke();
      } else if (m === 'double') {
        // Outer dots swell toward the notch. A fainter inner arc marks each app.
        const f = Math.max(0, 1 - a / 3.6);
        const size = hot ? 9 : major ? 3 + 5 * f : 2 + 2.6 * f;
        this.dot(x(px), py, size / 2, hot ? pal.accent : pal.fg, hot ? 1 : .65 * (.3 + .7 * f) * fade);
        if (major) {
          const ri = gg.r - 16, s2 = 2.5 + 3 * f;
          this.dot(x(gg.cx - ri * Math.cos(ang)), gg.cy + ri * Math.sin(ang), s2 / 2, pal.fg, .65 * (.25 + .5 * f) * fade);
        }
      } else {
        const size = m === 'nodes' ? (major ? 6 : 2) : (major ? 4 : 2);
        const [c, al] = hot ? [pal.accent, 1] : major ? [pal.fg, .65] : m === 'dots' ? [pal.fg, .3] : [pal.fg, .22];
        this.dot(x(px), py, size / 2, c, al * fade);
      }
    }
  }

  dot(x, y, r, c, a) {
    const g = this.g;
    g.globalAlpha = c01(a);
    g.fillStyle = css(c);
    g.beginPath();
    g.arc(x, y, r, 0, TAU);
    g.fill();
    g.globalAlpha = 1;
  }

  drawLabels() {
    const names = this.scrub || this.names, n = names.length, pal = this.pal, g = this.g;
    const gg = geo(this.radius, this.cy), L = this.layout, la = this.a.labels;
    for (let i = 0; i < n; i++) {
      let d = mod(i - this.pos, n);
      if (d > n / 2) d -= n;
      const a = Math.abs(d);
      if (a >= gg.span) continue;
      const cl = Math.max(0, 1 - a), sel = cl > .5, name = names[i];
      const s = this.scrub ? 1 + .9 * cl * cl : labelScale(a, name.length);
      const p = labelPoseAt(d, name.length, s, gg, labelRadius(gg, L.m), this.left);
      const op = Math.pow(1 - a / gg.span, 1.35) * (this.open && this.open.name === name ? 0 : 1) * (this.open ? la : 1);
      if (op < .004) continue;
      this.label(name, p, s, sel, op);
      if (!this.scrub && this.unread.has(name)) this.dot(p.bx, p.by, 2.5, pal.accent, op);
    }
  }

  label(name, p, s, sel, op) {
    const g = this.g;
    g.save();
    g.translate(p.x, p.y);
    g.rotate(p.rot);
    g.scale(s, s);
    this.font(15, sel ? 500 : 400, .01);
    const w = g.measureText(name).width;
    g.textAlign = 'left';
    g.textBaseline = 'middle';
    g.globalAlpha = op;
    g.fillStyle = css(sel ? this.pal.accent : this.pal.fg);
    g.fillText(name, p.tx * w, 0);
    g.restore();
  }

  /** The fixed notch at the selected row. It kicks 4 units outward on every detent. */
  drawNotch() {
    const pal = this.pal, left = this.left, g = this.g, cy = this.cy;
    const off = (left ? 1 : -1) * this.pawl * 4;
    const x = v => (left ? W - v : v) + off;
    switch (this.layout.m) {
      case 'ticks':
        g.fillStyle = css(pal.accent);
        g.fillRect(x(left ? 242 : 204), cy - 1, 38, 2);
        break;
      case 'nodes':
        g.strokeStyle = css(pal.accent);
        g.lineWidth = 1.5;
        g.beginPath();
        g.arc(x(226), cy, 9.25, 0, TAU);
        g.stroke();
        break;
      case 'double':
        this.dot(x(226), cy, 8, pal.bg, 1);
        this.dot(x(226), cy, 5, pal.accent, 1);
        break;
      default:
        [[191.5, 3, .45], [200, 4, .7], [209.5, 5, 1]].forEach(([c, s, a]) => this.dot(x(c), cy, s / 2, pal.accent, a));
    }
  }
}
