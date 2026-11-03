#!/usr/bin/env node
// Makes the images in docs/media: screenshots of the app on an emulator, framed by frame.html.
//
//   node tools/media/make.mjs                 capture every scene, then frame it
//   node tools/media/make.mjs search rename   only these scenes
//   node tools/media/make.mjs --frame-only    frame the last screenshots again, without the emulator
//
// It needs a running 1080x2400 emulator with a Google Play or Google APIs image (the "gym_pixel" AVD),
// the debug build of Detent installed on it (./gradlew installDebug), and Chrome or Chromium.
// adb comes from ANDROID_HOME or local.properties, and Chrome from CHROME or the usual places.
//
// The script sets the clock to Sat 31 Oct, 23:30 (with adb root), hides the system status bar,
// makes Detent the home app, and writes its settings for each scene. It puts all of that back afterwards.

import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawn, spawnSync } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const shots = join(root, "build/media");
const out = join(root, "docs/media");
const pkg = "app.detent.launcher";

// The themes and layouts are saved by their place in the lists in Model.kt.
const model = readFileSync(join(root, "app/src/main/java/app/detent/launcher/Model.kt"), "utf8");
const block = start => { const i = model.indexOf(start); return model.slice(i, model.indexOf("\n)", i)); };
const LAYOUTS = [...block("val LAYOUTS").matchAll(/Layout\("([^"]+)"/g)].map(m => m[1]);
const THEMES = [...block("val THEMES").matchAll(/(?:phone|omarchy|ThemeDef)\(\s*"([^"]+)"/g)].map(m => m[1]);
const KOI = THEMES.indexOf("koi-watercolor");
const NODES = LAYOUTS.indexOf("flat · radial · nodes");
if (KOI < 0 || NODES < 0) throw new Error("koi-watercolor or the nodes layout is missing from Model.kt");

// The apps on the emulator by package, with the labels the app shows. The dial sorts them by label.
const APPS = {
    "com.google.android.calendar": "calendar",
    "com.android.camera2": "camera",
    "com.android.chrome": "chrome",
    "com.google.android.deskclock": "clock",
    "com.google.android.contacts": "contacts",
    "com.google.android.apps.docs": "drive",
    "com.google.android.documentsui": "files",
    "com.google.android.gm": "gmail",
    "com.google.android.apps.maps": "maps",
    "com.google.android.apps.messaging": "messages",
    "com.google.android.dialer": "phone",
    "com.google.android.apps.photos": "photos",
    "com.android.settings": "settings",
    "com.google.android.apps.youtube.music": "yt music",
};
// Off the dial, so that search shows "+ dial" on them.
const OFF_DIAL = ["com.google.android.youtube", "com.google.android.googlequicksearchbox"];
const RECENT_APPS = ["com.google.android.apps.maps", "com.google.android.youtube", "com.android.settings", "com.google.android.calendar", "com.google.android.gm"];
const RECENT_SEARCHES = ["weather this weekend", "koi pond plants", "ramen near me", "how to fold a paper crane", "jetbrains mono"];

// Points on the 1080x2400 screen.
const P = {
    selected: [800, 1262], // the app in the notch
    dial: [720, 1140], // the app above the notch: holding it scrubs letters
    apps: [175, 2205],
    corner: [880, 2205],
    next: [893, 2213],
};

// Each scene: the words in the frame, the app's settings, and the steps to the screen.
// A "hold" step keeps the finger down while the screenshot is taken. A scene "like" another frames its screenshot,
// and "size" sets the frame in pixels (1280x720 by default).
const SCENES = {
    home: { title: "detent", sub: "a home screen you turn.", select: "gmail", steps: [] },
    plain: { select: "gmail", steps: [] },
    "plain-4x3": { like: "plain", size: [960, 720] },
    "plain-1075": { like: "plain", size: [1075, 720] },
    letter: { title: "hold the dial", sub: "jump by letter.", select: "maps", steps: [["hold", P.dial, 1400]] },
    menu: { title: "hold an app", sub: "shortcuts, info and a new name.", select: "settings", steps: [["press", P.selected, 900], ["wait", 900]] },
    themes: {
        title: `${THEMES.length} themes`, sub: "each with its own wallpaper.", fresh: true,
        steps: [["wait", 1200], ["tap", P.next], ["wait", 1200], ["tap", P.next], ["wait", 1500]],
    },
    search: {
        title: "search the rest", sub: "add any app to the dial.", select: "gmail",
        steps: [["tap", P.apps], ["wait", 1200], ["tapText", "+ dial"], ["wait", 900]],
    },
    web: {
        title: "search the web", sub: "your last searches, a tap away.", select: "gmail",
        steps: [["tap", P.corner], ["wait", 1500], ["key", "BACK"], ["wait", 900]],
    },
    rename: {
        title: "rename an app", sub: "call it what you like.", select: "yt music",
        steps: [["press", P.selected, 900], ["wait", 900], ["tapText", "rename"], ["wait", 1500], ["text", "music"], ["wait", 400], ["key", "BACK"], ["wait", 900]],
    },
};

const args = process.argv.slice(2);
const frameOnly = args.includes("--frame-only");
const names = args.filter(a => !a.startsWith("--"));
for (const n of names) if (!SCENES[n]) throw new Error(`no scene "${n}". The scenes: ${Object.keys(SCENES).join(", ")}`);
const scenes = names.length ? names : Object.keys(SCENES);


// Capture

function sdk() {
    const env = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
    if (env) return env;
    const props = join(root, "local.properties");
    const m = existsSync(props) && readFileSync(props, "utf8").match(/^sdk\.dir=(.*)$/m);
    if (m) return m[1].replace(/\\(.)/g, "$1").trim();
    throw new Error("set ANDROID_HOME, or sdk.dir in local.properties");
}

function adbPath() {
    const exe = join(sdk(), "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
    return existsSync(exe) ? exe : "adb";
}

const ADB = frameOnly ? null : adbPath();

function adb(args, input) {
    const r = spawnSync(ADB, args, { input, maxBuffer: 64 << 20 });
    if (r.status !== 0) throw new Error(`adb ${args.join(" ")}: ${r.stderr}`);
    return r.stdout;
}
const sh = cmd => adb(["shell", cmd]).toString().trim();
const sleep = ms => new Promise(r => setTimeout(r, ms));
const waitSync = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

function capture(list) {
    if (!adb(["devices"]).toString().split("\n").slice(1).some(l => /\tdevice/.test(l))) throw new Error("no emulator is running");
    const size = sh("wm size");
    if (!size.includes("1080x2400")) throw new Error(`the screen is not 1080x2400: ${size}`);
    if (!sh(`pm path ${pkg}`)) throw new Error("Detent is not installed. Run ./gradlew installDebug");

    // The launcher activity of each app, as the app keys them.
    const activities = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
        .split("\n").map(l => l.trim()).filter(l => l.includes("/"));
    const key = p => { const a = activities.find(l => l.startsWith(p + "/")); return a && a + "#0"; };
    const dial = Object.keys(APPS).filter(key);

    const saved = { home: sh("cmd role get-role-holders android.app.role.HOME"), prefs: readPrefs(), icons: sh("settings get secure icon_blacklist"), autoTime: sh("settings get global auto_time"), hours: sh("settings get system time_12_24") };
    adb(["root"]);
    adb(["wait-for-device"]);
    try {
        // Only the home app gets the app shortcuts that the hold menu shows.
        sh(`cmd role add-role-holder android.app.role.HOME ${pkg}`);
        sh("settings put global auto_time 0");
        sh("settings put system time_12_24 24");
        // No clock, icons or notifications: frame.html draws its own status bar.
        sh("settings put secure icon_blacklist clock,battery,wifi,mobile,airplane,rotate,headset,volume,alarm_clock,cast,hotspot,vpn,location,nfc,ethernet,data_saver,zen,managed_profile,bluetooth");
        sh("settings put global sysui_demo_allowed 1");
        sh("am broadcast -a com.android.systemui.demo -e command enter");
        sh("am broadcast -a com.android.systemui.demo -e command notifications -e visible false");
        for (const name of list) {
            const s = SCENES[name];
            console.log(`capture ${name}`);
            sh(`am force-stop ${pkg}`);
            writePrefs(s.fresh ? freshPrefs() : prefs(s, dial, key));
            sh("date 103123302026.00");
            sh(`am start -W -n ${pkg}/.MainActivity`);
            waitSync(2000);
            run(name, s.steps);
        }
    } finally {
        sh(`am force-stop ${pkg}`);
        if (saved.prefs) writePrefs(saved.prefs);
        sh("am broadcast -a com.android.systemui.demo -e command exit");
        sh(saved.icons === "null" ? "settings delete secure icon_blacklist" : `settings put secure icon_blacklist ${saved.icons}`);
        sh(`settings put global auto_time ${saved.autoTime === "null" ? 1 : saved.autoTime}`);
        sh(saved.hours === "null" ? "settings delete system time_12_24" : `settings put system time_12_24 ${saved.hours}`);
        if (saved.home && saved.home !== pkg) sh(`cmd role add-role-holder android.app.role.HOME ${saved.home}`);
        sh("input keyevent HOME");
    }

    function run(name, steps) {
        let held = null;
        for (const [kind, a, b] of steps) {
            if (kind === "wait") waitSync(a);
            else if (kind === "tap") sh(`input tap ${a[0]} ${a[1]}`);
            else if (kind === "press") sh(`input swipe ${a[0]} ${a[1]} ${a[0]} ${a[1]} ${b}`);
            else if (kind === "key") sh(`input keyevent ${a}`);
            else if (kind === "text") sh(`input text ${a.replace(/ /g, "%s")}`);
            else if (kind === "tapText") tapText(a);
            else if (kind === "hold") {
                // The finger stays down past the screenshot, which comes after b milliseconds.
                held = spawn(ADB, ["shell", `input swipe ${a[0]} ${a[1]} ${a[0]} ${a[1]} ${b + 2500}`]);
                waitSync(b);
            }
        }
        const png = adb(["exec-out", "screencap", "-p"]);
        writeFileSync(join(shots, `${name}.png`), png);
        if (held) waitSync(2600);
    }

    // Taps the first view whose text is t, found in the accessibility tree.
    function tapText(t) {
        sh("uiautomator dump /sdcard/ui.xml");
        const xml = sh("cat /sdcard/ui.xml");
        const esc = t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/&/g, "&amp;");
        const m = xml.match(new RegExp(`text="${esc}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`));
        if (!m) throw new Error(`nothing on the screen says "${t}"`);
        const [x0, y0, x1, y1] = m.slice(1).map(Number);
        sh(`input tap ${(x0 + x1) >> 1} ${(y0 + y1) >> 1}`);
    }
}

function readPrefs() {
    const r = spawnSync(ADB, ["exec-out", "run-as", pkg, "cat", "shared_prefs/detent.xml"]);
    return r.status === 0 && r.stdout.toString().startsWith("<?xml") ? r.stdout.toString() : null;
}

function writePrefs(xml) {
    adb(["exec-in", "run-as", pkg, "sh", "-c", "mkdir -p shared_prefs && cat > shared_prefs/detent.xml"], xml);
}

const x = v => v.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function prefs(s, dial, key) {
    const labels = dial.map(p => APPS[p]).sort();
    const pos = Math.max(0, labels.indexOf(s.select));
    const recent = RECENT_APPS.map(key).filter(Boolean);
    return `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="done" value="true" />
    <int name="layout" value="${NODES}" />
    <int name="theme" value="${KOI}" />
    <string name="side">right</string>
    <string name="corner">web</string>
    <boolean name="lockWall" value="false" />
    <boolean name="sound" value="false" />
    <int name="pos" value="${pos}" />
    <set name="apps">
${dial.filter(p => !OFF_DIAL.includes(p)).map(p => `        <string>${x(key(p))}</string>`).join("\n")}
    </set>
    <string name="recent">${x(recent.join("\n"))}</string>
    <string name="recentSearches">${x(RECENT_SEARCHES.join("\n"))}</string>
</map>
`;
}

// A first start: onboarding, on the default layout and theme.
function freshPrefs() {
    return `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="lockWall" value="false" />
    <boolean name="sound" value="false" />
</map>
`;
}

// Frame

function chromePath() {
    const list = [
        process.env.CHROME,
        "C:/Program Files/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
    ];
    const found = list.find(p => p && existsSync(p));
    if (!found) throw new Error("set CHROME to a Chrome or Chromium binary");
    return found;
}

async function frame(list) {
    const profile = mkdtempSync(join(tmpdir(), "detent-media-"));
    const chrome = spawn(chromePath(), [
        "--headless=new", "--remote-debugging-port=0", `--user-data-dir=${profile}`,
        "--allow-file-access-from-files", "--hide-scrollbars", "--force-color-profile=srgb", "about:blank",
    ]);
    try {
        const port = await devtoolsPort(profile);
        const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
        const page = await connect(target.webSocketDebuggerUrl);
        const wall = pathToFileURL(join(root, "app/src/main/assets/walls/koi-watercolor.webp")).href;
        for (const name of list) {
            const s = SCENES[name];
            const [width, height] = s.size || [1280, 720];
            await page("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: false });
            const shot = join(shots, `${s.like || name}.png`);
            if (!existsSync(shot)) throw new Error(`no screenshot for ${name}. Capture it first`);
            const hash = new URLSearchParams({ shot: pathToFileURL(shot).href, wall, title: s.title || "", sub: s.sub || "", w: width, h: height });
            await page("Page.navigate", { url: `${pathToFileURL(join(here, "frame.html")).href}?${name}#${hash}` });
            await page("Runtime.evaluate", { expression: "new Promise(r => { const go = () => window.ready ? window.ready.then(r) : setTimeout(go, 20); go(); })", awaitPromise: true });
            const { data } = await page("Page.captureScreenshot", { format: "jpeg", quality: 86, clip: { x: 0, y: 0, width, height, scale: 1 } });
            writeFileSync(join(out, `${name}.jpg`), Buffer.from(data, "base64"));
            console.log(`frame ${name} -> docs/media/${name}.jpg`);
        }
        page.close();
    } finally {
        chrome.kill();
        await sleep(300);
        rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    }
}

async function devtoolsPort(profile) {
    const file = join(profile, "DevToolsActivePort");
    for (let i = 0; i < 100; i++) {
        if (existsSync(file)) return readFileSync(file, "utf8").split("\n")[0];
        await sleep(100);
    }
    throw new Error("Chrome did not start");
}

async function connect(url) {
    const ws = new WebSocket(url);
    await new Promise((ok, fail) => { ws.onopen = ok; ws.onerror = fail; });
    let id = 0;
    const waiting = new Map();
    ws.onmessage = e => {
        const m = JSON.parse(e.data);
        const w = waiting.get(m.id);
        if (!w) return;
        waiting.delete(m.id);
        m.error ? w.fail(new Error(m.error.message)) : w.ok(m.result);
    };
    const call = (method, params = {}) => new Promise((ok, fail) => {
        waiting.set(++id, { ok, fail });
        ws.send(JSON.stringify({ id, method, params }));
    });
    call.close = () => ws.close();
    return call;
}

// Run, once everything above is defined.

mkdirSync(shots, { recursive: true });
if (!frameOnly) capture([...new Set(scenes.map(n => SCENES[n].like || n))]);
await frame(scenes);
