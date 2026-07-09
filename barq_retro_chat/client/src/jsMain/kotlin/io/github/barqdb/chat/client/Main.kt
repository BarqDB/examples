package io.github.barqdb.chat.client

import io.github.barqdb.chat.protocol.ChatMessage
import io.github.barqdb.chat.protocol.ClientMsg
import io.github.barqdb.chat.protocol.Hello
import io.github.barqdb.chat.protocol.History
import io.github.barqdb.chat.protocol.Join
import io.github.barqdb.chat.protocol.Leave
import io.github.barqdb.chat.protocol.NewMessages
import io.github.barqdb.chat.protocol.Nick
import io.github.barqdb.chat.protocol.Presences
import io.github.barqdb.chat.protocol.RoomInfo
import io.github.barqdb.chat.protocol.RoomList
import io.github.barqdb.chat.protocol.Say
import io.github.barqdb.chat.protocol.ServerMsg
import io.github.barqdb.chat.protocol.Stats
import io.github.barqdb.chat.protocol.StatsUpdate
import io.github.barqdb.chat.protocol.Typing
import io.github.barqdb.chat.protocol.TypingSignal
import io.github.barqdb.chat.protocol.Welcome
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import kotlinx.html.js.onKeyDownFunction
import kotlinx.html.*
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.KeyboardEvent

/** JSON codec — identical config to the server so the `t` discriminator lines up. */
private val json = Json {
    classDiscriminator = "t"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

// ─────────────────────────── app state ───────────────────────────

private val root get() = document.getElementById("root") as HTMLElement

private var username = Names.random()
private var color = Nick.colorFor(username)
private var status = "online"

private var rooms: List<RoomInfo> = emptyList()
private var current: RoomInfo? = null
private var screen = "welcome"

// live DOM handles for the chat screen
private var msgListEl: HTMLElement? = null
private var typingRowEl: HTMLElement? = null
private var typingWhoEl: HTMLElement? = null
private var inputEl: HTMLInputElement? = null
private var lastMsgUser: String? = null
private var typingTimer = 0
private var typingCooldown = false

private lateinit var socket: WebSocket

fun main() {
    injectStyles()
    connect()
    showWelcome()
}

// ─────────────────────────── networking ───────────────────────────

private fun connect() {
    val proto = if (window.location.protocol == "https:") "wss" else "ws"
    val url = "$proto://${window.location.host}/ws"
    socket = WebSocket(url).apply {
        onopen = { send(Hello(username, color, status)); Unit }
        onmessage = { e -> (e as MessageEvent).data.let { d -> if (d is String) handle(json.decodeFromString<ServerMsg>(d)) }; Unit }
        onclose = { window.setTimeout({ connect() }, 1000); Unit }
    }
}

private fun send(msg: ClientMsg) {
    if (::socket.isInitialized && socket.readyState == WebSocket.OPEN) {
        socket.send(json.encodeToString(ClientMsg.serializer(), msg))
    }
}

private fun handle(msg: ServerMsg) {
    when (msg) {
        is Welcome -> {
            rooms = msg.rooms
            applyStats(msg.stats)
            if (screen == "rooms") showRooms() // fill the grid once the catalogue arrives
        }
        is RoomList -> {
            rooms = msg.rooms
            if (screen == "rooms") showRooms() // live online / msg-s / stored per room
            else if (screen == "chat") current?.let { cur ->
                msg.rooms.firstOrNull { it.id == cur.id }?.let { setText("roomMps", fmtCount(it.mps.toLong())) }
            }
        }
        is StatsUpdate -> applyStats(msg.stats)
        is History -> if (screen == "chat" && msg.room == current?.id) renderHistory(msg.messages)
        is NewMessages -> if (screen == "chat" && msg.room == current?.id) msg.messages.forEach { appendMessage(it) }
        is Presences -> if (screen == "chat" && msg.room == current?.id) renderPresence(msg)
        is TypingSignal -> if (screen == "chat" && msg.user != username) showTyping(msg.user)
    }
}

// ─────────────────────────── stats plumbing ───────────────────────────

private fun setText(id: String, text: String) { document.getElementById(id)?.textContent = text }

/** Update every element carrying a stat class (a stat can appear more than once per screen). */
private fun setAll(cls: String, text: String) {
    val nodes = document.querySelectorAll(".$cls")
    for (i in 0 until nodes.length) (nodes.item(i) as? HTMLElement)?.textContent = text
}

private fun applyStats(s: Stats) {
    setAll("js-stored", fmtBig(s.stored))
    setAll("js-users", fmtBig(s.users))
    setAll("js-rooms", groupThousands(s.rooms.toLong()))
    setAll("js-latency", s.latencyMs.toString())
}

// ─────────────────────────── screen: welcome ───────────────────────────

private fun mount(card: HTMLElement) {
    root.innerHTML = ""
    root.append {
        div("bokeh a") {}
        div("bokeh b") {}
    }
    val stage = document.create.div("stage")
    stage.appendChild(card)
    root.appendChild(stage)
}

private fun showWelcome() {
    screen = "welcome"
    val card = document.create.div("card") {
        style = "width:400px; max-width:100%;"
        div("goldstrip") {}
        div { style = "padding:26px 30px 10px; text-align:center;"
            div { style = "display:inline-flex; align-items:center; gap:9px; margin-bottom:4px;"
                div("logo-badge") { span { +"⚡" } }
                div { style = "text-align:left;"
                    div("brand") { +"barqDB "; small { +"Messenger" } }
                    div("brand-sub") { +"realtime · retro edition" }
                }
            }
        }
        div { style = "padding:6px 30px 8px;"
            div("label") { +"Your screen name:" }
            div { style = "display:flex; gap:8px; align-items:stretch;"
                div("namefield") {
                    span("dot") {}
                    span("nm") { id = "nameLabel"; style = "color:$color"; +username }
                }
                button(classes = "btn btn-dice") { title = "Give me another one"; onClickFunction = { shuffle() }; +"🎲" }
            }
            div { style = "text-align:right; margin-top:5px;"
                a(href = "#") { style = "font-size:10.5px;"; onClickFunction = { it.preventDefault(); shuffle() }; +"↻ not me, shuffle again" }
            }
        }
        div { style = "padding:8px 30px 4px;"
            div("label") { +"Status:" }
            div("statusrow") { style = "display:flex; gap:6px;"
                statusButton("online", "🟢 Online")
                statusButton("away", "🌙 Away")
                statusButton("busy", "⛔ Busy")
            }
        }
        div { style = "padding:16px 30px 22px;"
            button(classes = "btn btn-green") { style = "width:100%; padding:12px; font-size:15px;"; onClickFunction = { signIn() }; +"Sign In  →" }
            div("poweredby") {
                span("blink") {}
                +"powered by "; b { +"barqDB" }; +" · "; span("js-latency") { +"…" }; +"ms writes"
            }
        }
    }
    mount(card)
}

private fun DIV.statusButton(key: String, label: String) {
    button(classes = "btn btn-status" + if (status == key) " on" else "") {
        onClickFunction = { status = key; showWelcome() }
        +label
    }
}

private fun shuffle() {
    username = Names.random()
    color = Nick.colorFor(username)
    document.getElementById("nameLabel")?.let { (it as HTMLElement).style.color = color; it.textContent = username }
    send(Hello(username, color, status))
}

private fun signIn() {
    send(Hello(username, color, status))
    showRooms()
}

// ─────────────────────────── screen: rooms ───────────────────────────

private fun showRooms() {
    screen = "rooms"
    val card = document.create.div("card") {
        style = "width:640px; max-width:100%; display:flex; flex-direction:column; max-height:88vh;"
        div("titlebar") {
            span { style = "font-size:18px;"; +"⚡" }
            div { style = "flex:1;"
                div("t-name") { +"Pick a chatroom" }
                div("t-sub") { +"welcome, "; b { id = "meName"; +username }; +" · "; span("js-rooms") { +"…" }; +" rooms live right now" }
            }
            span("pill") { span("g") {}; +"online" }
        }
        div { style = "overflow:auto; padding:12px; background:#eef5fd;"
            div("roomgrid") {
                rooms.forEach { r -> roomCard(r) }
                if (rooms.isEmpty()) div { style = "padding:20px; color:#7a8699; font-size:12px;"; +"connecting to barqDB…" }
            }
        }
        statusBar(inRoom = false)
    }
    mount(card)
}

private fun DIV.roomCard(r: RoomInfo) {
    button(classes = "btn room-card") {
        onClickFunction = { enterRoom(r) }
        span("ic") { +r.icon }
        span { style = "flex:1; min-width:0;"
            span("nm") { +r.name }
            span("tp") { +r.topic }
            span { style = "display:flex; gap:6px; flex-wrap:wrap;"
                span("tag on") { span("d") {}; +"${fmtCount(r.online)} online" }
                span("tag mps") { +"⚡ ${fmtCount(r.mps.toLong())} msg/s" }
                span("tag stored") { +"${fmtCount(r.stored)} msgs" }
            }
        }
    }
}

// ─────────────────────────── screen: chat ───────────────────────────

private fun enterRoom(r: RoomInfo) {
    current = r
    lastMsgUser = null
    showChat(r)
    send(Join(r.id))
}

private fun leaveRoom() {
    send(Leave)
    current = null
    showRooms()
}

private fun showChat(r: RoomInfo) {
    screen = "chat"
    val card = document.create.div("card") {
        style = "width:920px; max-width:100%; height:88vh; display:flex; flex-direction:column;"
        // title bar
        div("titlebar") {
            span { style = "font-size:24px; line-height:1;"; +r.icon }
            div { style = "flex:1; min-width:0;"
                div("t-name") { +r.name }
                div("t-sub") { +r.topic }
            }
            div { style = "display:flex; align-items:center; gap:8px; flex:none;"
                span("pill") { span("g") {}; span { id = "roomOnline"; +fmtCount(r.online) }; +" online" }
                span("pill") { +"⚡ "; span { id = "roomMps"; +fmtCount(r.mps.toLong()) }; +" msg/s" }
                button(classes = "btn x-btn") { title = "Back to rooms"; onClickFunction = { leaveRoom() }; +"×" }
            }
        }
        // body: messages + sidebar
        div { style = "flex:1; display:flex; min-height:0;"
            div { style = "flex:1; display:flex; flex-direction:column; min-width:0; border-right:1px solid #d5e3f4;"
                div("msglist") {
                    div("typing") { style = "display:none;"
                        span { style = "display:inline-flex; gap:3px; align-items:center;"
                            span("d") { style = "animation:barqBlink 1s infinite;" }
                            span("d") { style = "animation:barqBlink 1s .2s infinite;" }
                            span("d") { style = "animation:barqBlink 1s .4s infinite;" }
                        }
                        span("who") { +"someone is typing…" }
                    }
                }
                // composer
                div("composer") {
                    div("emobar") {
                        emoticons().forEach { (code, ch) ->
                            button(classes = "btn emoticon") { title = code; onClickFunction = { insertEmoticon(code) }; +ch }
                        }
                        span { style = "flex:1;" }
                        span("hud") { +"⚡ synced · "; span("js-latency") { +"…" }; +"ms" }
                    }
                    div { style = "display:flex; gap:8px; align-items:stretch;"
                        input(classes = "input") {
                            placeholder = "type something… press Enter to send :)"
                            onInputFunction = { onType() }
                            onKeyDownFunction = { e -> if ((e as KeyboardEvent).key == "Enter") { e.preventDefault(); sendMessage() } }
                        }
                        button(classes = "btn btn-green") { style = "padding:0 22px; font-size:13.5px;"; onClickFunction = { sendMessage() }; +"Send" }
                    }
                }
            }
            // sidebar
            div("sidebar") {
                div("head") { span { +"In this room" }; span("cnt") { id = "sideCount"; +fmtCount(r.online) } }
                div("list") { id = "sideList" }
            }
        }
        statusBar(inRoom = true)
    }
    mount(card)

    msgListEl = card.querySelector(".msglist") as HTMLElement
    typingRowEl = card.querySelector(".typing") as HTMLElement
    typingWhoEl = card.querySelector(".typing .who") as HTMLElement
    inputEl = (card.querySelector(".input") as HTMLInputElement).also { it.focus() }
}

private fun renderHistory(messages: List<ChatMessage>) {
    val list = msgListEl ?: return
    // wipe everything except the typing row
    while (list.firstChild != null && list.firstChild != typingRowEl) list.removeChild(list.firstChild!!)
    lastMsgUser = null
    messages.forEach { appendMessage(it) }
}

private fun appendMessage(m: ChatMessage) {
    val list = msgListEl ?: return
    val self = m.user == username
    val showName = m.user != lastMsgUser
    lastMsgUser = m.user

    val row = document.create.div("msg-row" + if (self) " self" else "") {
        div("bubble" + if (self) " self" else "") {
            if (showName) div("nm") {
                style = "color:${m.color}"
                +m.user
                if (m.flag.isNotEmpty()) { +" "; span("flag") { +m.flag } }
            }
            div("tx") { +m.text }
            div("meta") { +(if (self) "✓✓ delivered · ${m.latencyMs}ms" else "⚡ ${m.latencyMs}ms") }
        }
    }
    list.insertBefore(row, typingRowEl)

    // keep only the last ~60 bubbles in the DOM
    var count = list.childElementCount - 1 // minus the typing row
    while (count > 60 && list.firstChild != null && list.firstChild != typingRowEl) {
        list.removeChild(list.firstChild!!); count--
    }
    list.scrollTop = list.scrollHeight.toDouble()
}

private fun renderPresence(p: Presences) {
    setText("roomOnline", fmtCount(p.online))
    setText("sideCount", fmtCount(p.online))
    setText("roomCount", groupThousands(p.stored)) // real messages stored in this room
    val listEl = document.getElementById("sideList") ?: return
    listEl.innerHTML = ""
    listEl.append {
        p.users.forEach { u ->
            val you = u.name == username
            div("urow") {
                span("d") { style = "background:${u.dot}; box-shadow:0 0 4px ${u.dot};" }
                span("nm") { style = "color:${u.color}"; +(if (you) "${u.name} (you)" else u.name) }
            }
        }
        val more = (p.online - p.users.size).coerceAtLeast(0)
        if (more > 0) div("moreline") { +"+ ${fmtCount(more)} more chatting…" }
    }
}

private fun showTyping(who: String) {
    val row = typingRowEl ?: return
    typingWhoEl?.textContent = "$who is typing…"
    row.style.display = "flex"
    if (typingTimer != 0) window.clearTimeout(typingTimer)
    typingTimer = window.setTimeout({ row.style.display = "none"; typingTimer = 0 }, 1600)
}

private fun onType() {
    if (!typingCooldown) {
        send(Typing)
        typingCooldown = true
        window.setTimeout({ typingCooldown = false }, 1200)
    }
}

private fun insertEmoticon(code: String) {
    val el = inputEl ?: return
    el.value = if (el.value.isEmpty()) code else "${el.value} $code"
    el.focus()
}

private fun sendMessage() {
    val el = inputEl ?: return
    val text = el.value.trim()
    if (text.isEmpty()) return
    send(Say(text))     // the server writes it to BarqDB; the subscription echoes it back to us
    el.value = ""
    el.focus()
}

// ─────────────────────────── shared status bar ───────────────────────────

private fun DIV.statusBar(inRoom: Boolean) {
    div("statusbar") {
        span { style = "display:flex; align-items:center; gap:5px;"
            span("blink") {}; b { style = "color:#ffd98a;"; +"barqDB" }; +" realtime"
        }
        span("sep") { +"|" }
        span { b("js-stored") { +"…" }; +" stored" }
        span("sep") { +"|" }
        span { b("js-users") { +"…" }; +" served" }
        span("sep") { +"|" }
        if (inRoom) span { b { id = "roomCount"; +"…" }; +" in room" }
        else span { b("js-rooms") { +"…" }; +" rooms" }
        span { style = "margin-left:auto; display:flex; align-items:center; gap:5px;"
            +"⚡ write "; b("ok") { span("js-latency") { +"…" }; +"ms" }
        }
    }
}
