// Connects the page to the dials: the demo, the layout previews, and the theme swatches.
import { Dial, LAYOUTS, THEMES, DEFAULT_LAYOUT, DEFAULT_THEME, palette, css } from './dial.js';

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* The page works without storage. */ } },
};
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];

let theme = THEMES.findIndex(t => t.name === store.get('detent.theme'));
if (theme < 0) theme = DEFAULT_THEME;
// Number(null) is 0, so a missing layout must be caught before the range check.
const savedLayout = store.get('detent.layout');
let layout = savedLayout === null ? NaN : Number(savedLayout);
if (!(layout >= 0 && layout < LAYOUTS.length)) layout = DEFAULT_LAYOUT;
let left = store.get('detent.side') === 'left';

const said = $('#said');
const hint = $('.hint');
const hintText = hint.textContent;
let hintTimer = 0;
function showHint(text) {
  hint.textContent = text;
  said.textContent = text;
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => { hint.textContent = hintText; }, 4000);
}
const demo = new Dial($('#dial'), {
  theme, layout, left,
  onChange: e => {
    if (e.open) said.textContent = `${e.open} opens.`;
    if (e.voice) showHint('The mic button listens on your phone. The demo has no microphone.');
  },
});
const previews = $$('.card canvas').map((c, i) => new Dial(c, { theme, layout: i, left, still: true }));
const dials = [demo, ...previews];

// Theme swatches.
for (const group of ['phone', 'omarchy', 'sepro']) {
  const box = $(`.swatches[data-group="${group}"]`);
  THEMES.forEach((t, i) => {
    if (t.group !== group) return;
    const b = document.createElement('button');
    b.className = 'swatch';
    b.dataset.theme = i;
    b.textContent = t.name;
    b.style.background = css(t.bg);
    b.style.color = css(t.fg);
    b.style.setProperty('--sw-accent', css(t.accent));
    b.style.setProperty('--sw-dim', css(t.fg, .5));
    b.addEventListener('click', () => applyTheme(i));
    box.append(b);
  });
}

function applyTheme(i) {
  theme = i;
  const t = THEMES[i], p = palette(t), root = document.documentElement;
  const vars = {
    '--bg': css(p.bg), '--fg': css(p.fg), '--accent': css(p.accent), '--on-accent': css(p.onAccent),
    '--muted': css(p.muted), '--muted2': css(p.muted2), '--card': css(p.card), '--sheet': css(p.sheet),
    '--rim': css(p.fg, .14), '--rim2': css(p.fg, .22), '--grid': css(p.fg, .1),
  };
  for (const k in vars) root.style.setProperty(k, vars[k]);
  root.style.colorScheme = t.light ? 'light' : 'dark';
  root.dataset.themed = '';
  $('meta[name="theme-color"]').content = css(p.bg);
  dials.forEach(d => d.setTheme(i));
  $('#theme-name').textContent = t.name;
  $$('.swatch').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.theme) === i)));
  store.set('detent.theme', t.name);
}

function applyLayout(i) {
  layout = i;
  demo.setLayout(i);
  $('#layout-name').textContent = LAYOUTS[i].name;
  $$('[data-chip]').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.chip) === i)));
  $$('.card').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.layout) === i)));
  store.set('detent.layout', String(i));
}

function applySide(l) {
  left = l;
  dials.forEach(d => d.setSide(l));
  $$('[data-side]').forEach(b => b.setAttribute('aria-pressed', String((b.dataset.side === 'left') === l)));
  store.set('detent.side', l ? 'left' : 'right');
}

applyTheme(theme);
applyLayout(layout);
applySide(left);

$$('[data-chip]').forEach(b => b.addEventListener('click', () => applyLayout(Number(b.dataset.chip))));
$$('.card').forEach(b => b.addEventListener('click', () => {
  applyLayout(Number(b.dataset.layout));
  const r = $('#demo').getBoundingClientRect();
  if (r.bottom < 0 || r.top > innerHeight) $('#demo').scrollIntoView({ block: 'center' });
}));
$$('[data-side]').forEach(b => b.addEventListener('click', () => applySide(b.dataset.side === 'left')));
$('#theme-prev').addEventListener('click', () => applyTheme((theme + THEMES.length - 1) % THEMES.length));
$('#theme-next').addEventListener('click', () => applyTheme((theme + 1) % THEMES.length));

$$('[data-copy]').forEach(b => b.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText($(`#${b.dataset.copy}`).textContent);
    b.textContent = 'copied';
  } catch {
    b.textContent = 'select it';
  }
  setTimeout(() => { b.textContent = 'copy'; }, 1600);
}));

// Canvas text needs the font weights before the first frame.
Promise.all(['300 68px', '400 15px', '500 15px'].map(f => document.fonts.load(`${f} "JetBrains Mono"`)))
  .catch(() => {})
  .then(() => dials.forEach(d => d.redraw()));

setInterval(() => dials.forEach(d => d.tick()), 10000);

// The demo turns 2 apps and back once, so it is clear that the dial moves.
if (!reduced) {
  let touched = false;
  $('#dial').addEventListener('pointerdown', () => { touched = true; }, { once: true });
  new IntersectionObserver((entries, io) => {
    if (!entries.some(e => e.isIntersecting)) return;
    io.disconnect();
    setTimeout(() => { if (!touched) demo.stepBy(2); }, 700);
    setTimeout(() => { if (!touched) demo.stepBy(-2); }, 1700);
  }, { threshold: .6 }).observe($('#dial'));
}

fetch('https://api.github.com/repos/bjarneo/detent/releases/latest')
  .then(r => r.ok ? r.json() : null)
  .then(j => { if (j && j.tag_name) $('#version').textContent = j.tag_name; })
  .catch(() => {});
