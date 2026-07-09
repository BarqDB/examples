package io.github.barqdb.chat.server

/**
 * Room metadata is the only static content — just the topic list (icon/name/topic). The
 * numbers a room shows (people online, msg/s, messages stored) are all measured live, never
 * seeded here. Bot chatter and general text come from the vocab below.
 */

data class SeedRoom(
    val id: String, val icon: String, val name: String, val topic: String,
)

val SEED_ROOMS = listOf(
    SeedRoom("lobby", "🌐", "The Lobby", "say hi to the whole planet"),
    SeedRoom("gaming", "🎮", "Gaming HQ", "wow, halo, cod & more"),
    SeedRoom("music", "🎵", "Music Lounge", "now playing: everything"),
    SeedRoom("movies", "🎬", "Movies & TV", "no spoilers plz!!"),
    SeedRoom("tech", "💻", "Tech Talk", "gadgets, code & gizmos"),
    SeedRoom("sports", "⚽", "Sports Bar", "goooaaal ⚽"),
    SeedRoom("love", "💖", "Heart2Heart", "relationship advice ♥"),
    SeedRoom("random", "🎲", "Random", "totally random lol"),
)

val BOT_NAMES = listOf(
    "xX_ShadowNinja_Xx", "sk8er_grrl_92", "lolcatz2010", "D4rkKn1ght", "PixelPixie",
    "neon_dreamz", "iiMissBubblez", "RyderStorm_", "ghostwolf", "luna_star_x",
    "T3chW1zard", "blaze_it_up", "panda_luvr", "cyber_reb3l", "frostbyte", "MsSunshine07",
)

val FLAGS = listOf(
    "🇺🇸", "🇧🇷", "🇯🇵",
    "🇬🇧", "🇩🇪", "🇮🇳",
    "🇰🇷", "🇨🇦", "🇫🇷",
    "🇦🇺", "🇲🇽", "🇳🇱",
)

/** Generic chatter used across every room. */
val BOT_TEXTS = listOf(
    "brb mom needs the fone lol", "asl?? :P", "anyone else here from myspace??",
    "rofl this room is poppin 😂", "add me on msn!! ✉", "omg hiii everyone (:",
    "how is this so fast?? theres literally 0 lag", "g2g dinner cya <3",
    "who wants to trade neopets", "blasting my fav song rn 🎧", "lol nice", "xD",
    "anyone got the new update yet", "my msg showed up INSTANTLY ⚡ wut",
    "greetings from brazil!! 🇧🇷", "hello from japan ✌",
    "typing from my sidekick lol", "wassup peeps 8)",
    "this is the fastest chat ive ever been in ngl", "no way this many ppl are online rn",
    "haha same", "omg yesss", "pics or it didnt happen :P",
    "brb refreshing... oh wait i dont even need to 😎", "anyone wanna be msn buddies",
    "cyaaa", "ttyl!!", "that was so fast i didnt even see it load",
    "im literally chatting with the whole planet rn 🌍",
    "how are we all in sync like this??", "lag who? never met her",
)

/** A little room-specific flavor on top of the generic chatter. */
val ROOM_TEXTS: Map<String, List<String>> = mapOf(
    "gaming" to listOf("gg ez", "anyone on halo 2 rn?", "add me for co-op", "no scope!! 🎯", "lag switchers get out"),
    "music" to listOf("now playing: mcr 🎸", "aux cord is MINE", "this song is fire 🔥", "skip pls", "who made this playlist"),
    "movies" to listOf("NO SPOILERS", "just watched it omg", "team edward btw", "the twist tho 😱", "rewatching for the 5th time"),
    "tech" to listOf("just flashed my sidekick", "html is a programming language fight me", "my dial up finally died", "got the new razr 📱", "defrag gang"),
    "sports" to listOf("GOOOAL ⚽", "ref is blind smh", "did u see that play??", "we winning the cup this year", "offside!!"),
    "love" to listOf("he still hasnt texted back 😢", "we made it official 💕", "advice pls", "long distance is hard", "cutest couple here"),
)
