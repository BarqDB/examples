package io.github.barqdb.chat.client

import kotlinx.browser.document
import org.w3c.dom.HTMLStyleElement

/**
 * The entire look of the app, ported from the original design's inline styles into one
 * stylesheet and injected from Kotlin at startup. Keeping it here (instead of index.html)
 * keeps the front end pure Kotlin.
 */
fun injectStyles() {
    val style = document.createElement("style") as HTMLStyleElement
    style.textContent = CSS
    document.head!!.appendChild(style)
}

private const val CSS = """
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; height: 100%; font-family: Tahoma, 'Segoe UI', Verdana, Geneva, sans-serif; }
body {
  overflow: hidden;
  background: radial-gradient(circle at 30% 20%, #8fc4f0, #3f7fc4 55%, #245a97 100%);
}
a { color: #1c5fb0; text-decoration: none; }
a:hover { color: #0b3f80; }

#root { position: absolute; inset: 0; overflow: hidden; }

.bokeh { position: absolute; border-radius: 50%; pointer-events: none; }
.bokeh.a { top: 8%; left: 12%; width: 180px; height: 180px;
  background: radial-gradient(circle at 35% 35%, rgba(255,255,255,.35), rgba(255,255,255,0)); }
.bokeh.b { bottom: 6%; right: 10%; width: 260px; height: 260px;
  background: radial-gradient(circle at 35% 35%, rgba(255,255,255,.22), rgba(255,255,255,0)); }

.stage { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; padding: 24px; }

.card {
  background: #fff; border: 1px solid #2f6bb0; border-radius: 9px;
  box-shadow: 0 22px 60px rgba(10,40,80,.45); overflow: hidden;
}
/* Card sizes live in classes (not inline) so the mobile breakpoint can override them. */
.welcome-card { width: 400px; max-width: 100%; }
.rooms-card { width: 640px; max-width: 100%; display: flex; flex-direction: column; max-height: 88vh; }
.chat-card { width: 920px; max-width: 100%; height: 88vh; display: flex; flex-direction: column; }
.goldstrip { height: 6px; background: linear-gradient(90deg,#ffd23f,#ff9e1b); }

/* logo */
.logo-badge {
  width: 40px; height: 40px; border-radius: 9px; background: linear-gradient(180deg,#3b8ff0,#1c5fb0);
  display: flex; align-items: center; justify-content: center;
  box-shadow: inset 0 1px 0 rgba(255,255,255,.6), 0 2px 4px rgba(0,0,0,.25);
  animation: barqFloat 3s ease-in-out infinite;
}
.logo-badge span { font-size: 24px; line-height: 1; filter: drop-shadow(0 1px 1px rgba(0,0,0,.3)); }
.brand { font-size: 22px; font-weight: bold; color: #1c4f8f; letter-spacing: -.5px; }
.brand small { color: #7a7a7a; font-weight: normal; font-size: 22px; }
.brand-sub { font-size: 10px; color: #8aa; letter-spacing: 2px; text-transform: uppercase; }

.label { font-size: 11px; color: #666; margin-bottom: 6px; }

/* name field */
.namefield {
  flex: 1; display: flex; align-items: center; gap: 8px; border: 1px solid #9db8d6; border-radius: 5px;
  background: linear-gradient(180deg,#f4f9ff,#e9f2fc); padding: 9px 12px; box-shadow: inset 0 1px 2px rgba(0,0,0,.06);
}
.namefield .dot { width: 9px; height: 9px; border-radius: 50%; background: #37c257; box-shadow: 0 0 5px #37c257; flex: none; }
.namefield .nm { font-size: 16px; font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.btn { font-family: inherit; cursor: pointer; }
.btn-dice {
  flex: none; width: 44px; border: 1px solid #7ba3d2; border-radius: 5px;
  background: linear-gradient(180deg,#fbfdff,#dbe9f9); box-shadow: inset 0 1px 0 #fff; font-size: 17px;
}
.btn-dice:hover { background: linear-gradient(180deg,#ffffff,#c9defa); }
.btn-dice:active { background: linear-gradient(180deg,#cfe0f5,#e8f1fc); }

.btn-status {
  flex: 1; padding: 7px 4px; font-size: 11px; border-radius: 5px; border: 1px solid #cddcf0;
  background: linear-gradient(180deg,#fff,#eef4fc); color: #667;
}
.btn-status.on {
  border-color: #4d97ec; background: linear-gradient(180deg,#dcecfe,#c2ddfa); color: #1c4f8f; font-weight: bold;
}

.btn-green {
  border: 1px solid #1c6b2f; border-radius: 6px; color: #fff; font-weight: bold;
  text-shadow: 0 -1px 0 rgba(0,0,0,.3);
  background: linear-gradient(180deg,#66d17f,#2fa34e 52%,#238a3f);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.45), 0 2px 5px rgba(0,0,0,.2);
}
.btn-green:hover { background: linear-gradient(180deg,#79dd90,#34b356 52%,#279247); }
.btn-green:active { background: linear-gradient(180deg,#2fa34e,#66d17f); }

.poweredby { text-align: center; margin-top: 14px; font-size: 10px; color: #9ab;
  display: flex; align-items: center; justify-content: center; gap: 6px; }
.poweredby .blink { width: 6px; height: 6px; border-radius: 50%; background: #ffb400; animation: barqBlink 1.4s infinite; }
.poweredby b { color: #1c5fb0; }

/* titlebar (rooms + chat) */
.titlebar {
  display: flex; align-items: center; gap: 11px; padding: 10px 15px; color: #fff;
  background: linear-gradient(180deg,#4d97ec,#1c5fb0); box-shadow: inset 0 1px 0 rgba(255,255,255,.4);
}
.titlebar .t-name { font-size: 15px; font-weight: bold; text-shadow: 0 1px 1px rgba(0,0,0,.3); }
.titlebar .t-sub { font-size: 10.5px; opacity: .9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pill { font-size: 10.5px; background: rgba(0,0,0,.2); padding: 4px 10px; border-radius: 20px;
  display: inline-flex; align-items: center; gap: 5px; }
.pill .g { width: 6px; height: 6px; border-radius: 50%; background: #7dff9c; box-shadow: 0 0 5px #7dff9c; }
.x-btn { width: 26px; height: 26px; border: 1px solid rgba(255,255,255,.5); border-radius: 5px;
  background: rgba(255,255,255,.15); color: #fff; cursor: pointer; font-size: 14px; font-weight: bold; line-height: 1; }
.x-btn:hover { background: rgba(255,60,60,.85); border-color: #fff; }

/* room grid */
.roomgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.room-card {
  text-align: left; cursor: pointer; border: 1px solid #b8d0ec; border-radius: 8px;
  background: linear-gradient(180deg,#ffffff,#f0f6fe); padding: 12px 13px; display: flex; gap: 11px;
  align-items: flex-start; box-shadow: 0 1px 2px rgba(0,0,0,.05); font-family: inherit;
}
.room-card:hover { border-color: #4d97ec; background: linear-gradient(180deg,#ffffff,#e2eefc);
  box-shadow: 0 3px 10px rgba(28,95,176,.2); }
.room-card .ic { font-size: 26px; line-height: 1; flex: none; }
.room-card .nm { display: block; font-size: 13.5px; font-weight: bold; color: #1c4f8f; }
.room-card .tp { display: block; font-size: 11px; color: #7a8699; margin: 2px 0 7px; }
.tag { font-size: 10px; border-radius: 20px; padding: 1px 7px; display: inline-flex; align-items: center; gap: 4px; }
.tag.on { color: #2c8a45; background: #e2f6e8; border: 1px solid #bfe6c9; }
.tag.on .d { width: 5px; height: 5px; border-radius: 50%; background: #37c257; }
.tag.mps { color: #a8641f; background: #fdf1e0; border: 1px solid #f0d8b3; }
.tag.stored { color: #5a7290; background: #eef2f8; border: 1px solid #d5e0ee; }

/* archive search */
.searchbar { display: flex; align-items: center; gap: 8px; padding: 9px 12px; background: #e3eefb; border-bottom: 1px solid #cfe0f5; }
.searchbar .ic { font-size: 15px; flex: none; }
.search-input { flex: 1; min-width: 0; border: 1px solid #9db8d6; border-radius: 6px; padding: 8px 11px;
  font-size: 13px; font-family: inherit; background: #fff; box-shadow: inset 0 1px 2px rgba(0,0,0,.06); outline: none; }
.searchbar .corpus { font-size: 11px; color: #5a7290; white-space: nowrap; flex: none; }
.search-summary { padding: 10px 14px; font-size: 12.5px; color: #33507a; background: #eef5fd; border-bottom: 1px solid #dbe7f7; }
.search-summary b { color: #1c4f8f; }
.search-summary .ms { color: #2c8a45; font-weight: bold; }
.hitlist { padding: 10px; display: flex; flex-direction: column; gap: 6px; }
.hit { display: flex; gap: 9px; align-items: flex-start; padding: 8px 11px; border: 1px solid #e2ebf7;
  border-radius: 7px; background: linear-gradient(180deg,#fff,#f6faff); }
.hit .rm { font-size: 16px; line-height: 1.3; flex: none; }
.hit .bd { font-size: 12.5px; color: #2a2f36; line-height: 1.45; word-break: break-word; min-width: 0; }
.hit .u { font-weight: bold; }
.no-hits { padding: 22px; text-align: center; color: #7a8699; font-size: 12.5px; }

/* status bar */
.statusbar {
  display: flex; align-items: center; gap: 10px; padding: 6px 14px; font-size: 11px; color: #dceafc;
  background: linear-gradient(180deg,#2f6bb0,#1c4f8f); border-top: 1px solid #16487f;
}
.statusbar .sep { opacity: .5; }
.statusbar .blink { width: 7px; height: 7px; border-radius: 50%; background: #ffb400; animation: barqBlink 1.4s infinite; }
.statusbar .ok { color: #7dff9c; }

/* messages */
.msglist { flex: 1; overflow-y: auto; padding: 14px 16px; display: flex; flex-direction: column; gap: 9px;
  background: linear-gradient(180deg,#ffffff,#f6faff); }
.msg-row { display: flex; justify-content: flex-start; animation: msgIn .18s ease-out; }
.msg-row.self { justify-content: flex-end; }
.bubble { max-width: 78%; border: 1px solid #d7e4f4; border-radius: 10px; padding: 7px 11px;
  background: linear-gradient(180deg,#ffffff,#f1f6fd); box-shadow: 0 1px 1px rgba(0,0,0,.04); }
.bubble.self { border-color: #a9dcb6; background: linear-gradient(180deg,#e4f7e8,#d2f0da); }
.bubble .nm { font-size: 11px; font-weight: bold; margin-bottom: 2px; }
.bubble .nm .flag { font-weight: normal; color: #b7c2ce; font-size: 9.5px; }
.bubble .tx { font-size: 13px; color: #2a2f36; line-height: 1.4; word-break: break-word; }
.bubble .meta { font-size: 9.5px; color: #9fb0c2; margin-top: 3px; text-align: right;
  display: flex; gap: 4px; justify-content: flex-end; align-items: center; }

.typing { display: flex; align-items: center; gap: 7px; padding: 2px 4px; }
.typing .d { width: 5px; height: 5px; border-radius: 50%; background: #9fb3c9; }
.typing .who { font-size: 11px; color: #8496a8; }

/* composer */
.composer { border-top: 1px solid #d5e3f4; background: #eef5fd; padding: 8px 10px; }
.emobar { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.emoticon { width: 26px; height: 26px; border: 1px solid #cddcf0; border-radius: 5px;
  background: linear-gradient(180deg,#fff,#eaf2fc); cursor: pointer; font-size: 15px; line-height: 1; padding: 0; }
.emoticon:hover { border-color: #4d97ec; background: #fff; }
.hud { font-size: 10px; color: #8a6a12; background: linear-gradient(180deg,#fff6db,#ffe9a8);
  border: 1px solid #f0d488; border-radius: 20px; padding: 3px 10px; display: inline-flex; align-items: center;
  gap: 4px; animation: barqPulse 2.4s infinite; }
.input {
  flex: 1; border: 1px solid #9db8d6; border-radius: 6px; padding: 11px 13px; font-size: 13.5px;
  font-family: inherit; background: #fff; box-shadow: inset 0 1px 2px rgba(0,0,0,.06); outline: none;
}

/* sidebar */
.sidebar { width: 186px; flex: none; background: #f3f8fe; display: flex; flex-direction: column; }
.sidebar .head { padding: 9px 12px; font-size: 11px; font-weight: bold; color: #5a7290;
  border-bottom: 1px solid #dce8f6; display: flex; justify-content: space-between; align-items: center; }
.sidebar .head .cnt { color: #37a355; }
.sidebar .list { flex: 1; overflow-y: auto; padding: 6px; }
.urow { display: flex; align-items: center; gap: 7px; padding: 5px 7px; border-radius: 5px; }
.urow:hover { background: #e3eefb; }
.urow .d { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.urow .nm { font-size: 11.5px; font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.moreline { text-align: center; font-size: 10px; color: #9db; padding: 8px 4px; }

/* animations */
@keyframes barqPulse { 0% { box-shadow: 0 0 0 0 rgba(255,196,0,.7); } 70% { box-shadow: 0 0 0 9px rgba(255,196,0,0); } 100% { box-shadow: 0 0 0 0 rgba(255,196,0,0); } }
@keyframes barqBlink { 0%,60% { opacity: 1; } 80%,100% { opacity: .25; } }
@keyframes barqFloat { 0% { transform: translateY(0); } 50% { transform: translateY(-4px); } 100% { transform: translateY(0); } }
@keyframes msgIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

::-webkit-scrollbar { width: 15px; }
::-webkit-scrollbar-track { background: #e4eef8; }
::-webkit-scrollbar-thumb { background: linear-gradient(180deg,#bcd4ef,#8fb6e0); border: 1px solid #7ba3d2; border-radius: 7px; }

/* ───────────────────────── mobile ───────────────────────── */
@media (max-width: 600px) {
  .stage { padding: 8px; }

  /* rooms: one column, fill the screen */
  .roomgrid { grid-template-columns: 1fr; }
  .rooms-card { max-height: calc(100vh - 16px); }

  /* chat: drop the member sidebar (the count still shows in the title bar), fill the screen */
  .chat-card { height: calc(100vh - 16px); height: calc(100dvh - 16px); }
  .sidebar { display: none; }

  .titlebar { padding: 9px 12px; gap: 9px; }
  .titlebar .t-name { font-size: 14px; }
  .titlebar .pill { padding: 3px 7px; font-size: 10px; }

  /* status bar has many segments — let it wrap and drop the pipe separators */
  .statusbar { font-size: 10px; gap: 5px 8px; padding: 6px 10px; flex-wrap: wrap; }
  .statusbar .sep { display: none; }

  .emobar { flex-wrap: wrap; row-gap: 4px; }
  .bubble { max-width: 86%; }
  .msglist { padding: 12px; }
  .composer { padding: 8px; }

  /* search bar: drop the corpus hint, keep input + button on one row */
  .searchbar { padding: 8px 10px; gap: 6px; }
  .searchbar .corpus { display: none; }
}

/* very small phones */
@media (max-width: 360px) {
  .brand, .brand small { font-size: 20px; }
  .emoticon { width: 24px; height: 24px; font-size: 14px; }
}
"""
