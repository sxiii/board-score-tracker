package com.example.boardscores

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF17212B)
private val Muted = Color(0xFF687582)
private val Accent = Color(0xFF2563EB)
private val Surface = Color(0xFFF4F6F8)
private val Line = Color(0xFFE0E5EA)

data class Player(val name: String, val total: Int)
data class Round(val scores: List<Int>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("score_tracker", Context.MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    background = Surface,
                    surface = Color.White,
                    onBackground = Ink,
                    onSurface = Ink
                )
            ) {
                ScoreApp(
                    load = { prefs.getString("game", null) },
                    save = { prefs.edit().putString("game", it).apply() }
                )
            }
        }
    }
}

@Composable
private fun ScoreApp(load: () -> String?, save: (String) -> Unit) {
    var players by remember { mutableStateOf(listOf<Player>()) }
    var rounds by remember { mutableStateOf(listOf<Round>()) }
    var draftNames by remember { mutableStateOf(listOf("", "")) }
    var scoreInputs by remember { mutableStateOf(listOf<String>()) }
    var initialized by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    fun persist(ps: List<Player>, rs: List<Round>) {
        val root = JSONObject()
        root.put("players", JSONArray().apply {
            ps.forEach { put(JSONObject().put("name", it.name).put("total", it.total)) }
        })
        root.put("rounds", JSONArray().apply {
            rs.forEach { round -> put(JSONArray().apply { round.scores.forEach { put(it) } }) }
        })
        save(root.toString())
    }

    fun restore() {
        val raw = load()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val root = JSONObject(raw)
                val restoredPlayers = root.getJSONArray("players")
                val restoredRounds = root.getJSONArray("rounds")
                players = (0 until restoredPlayers.length()).map {
                    val p = restoredPlayers.getJSONObject(it)
                    Player(p.getString("name"), p.getInt("total"))
                }
                rounds = (0 until restoredRounds.length()).map { i ->
                    val arr = restoredRounds.getJSONArray(i)
                    Round((0 until arr.length()).map { arr.getInt(it) })
                }
                scoreInputs = players.map { "" }
            }
        }
        initialized = true
    }

    LaunchedEffect(Unit) { restore() }

    if (!initialized) {
        Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (players.isEmpty()) {
        SetupScreen(
            names = draftNames,
            onNameChange = { i, value -> draftNames = draftNames.toMutableList().also { it[i] = value } },
            onAdd = { if (draftNames.size < 12) draftNames = draftNames + "" },
            onRemove = { i -> if (draftNames.size > 2) draftNames = draftNames.toMutableList().also { it.removeAt(i) } },
            onStart = {
                val names = draftNames.mapIndexed { i, s -> s.trim().ifBlank { "Player ${i + 1}" } }
                if (names.size >= 2) {
                    players = names.map { Player(it, 0) }
                    rounds = emptyList()
                    scoreInputs = names.map { "" }
                    persist(players, rounds)
                }
            }
        )
    } else {
        GameScreen(
            players = players,
            rounds = rounds,
            scoreInputs = scoreInputs,
            onScoreChange = { i, v -> scoreInputs = scoreInputs.toMutableList().also { it[i] = v.filter { c -> c == '-' || c.isDigit() }.take(7) } },
            onAddRound = {
                val scores = players.indices.map { scoreInputs.getOrNull(it)?.toIntOrNull() ?: 0 }
                players = players.mapIndexed { i, p -> p.copy(total = p.total + scores[i]) }
                rounds = rounds + Round(scores)
                scoreInputs = players.map { "" }
                persist(players, rounds)
            },
            onUndo = {
                if (rounds.isNotEmpty()) {
                    val last = rounds.last()
                    players = players.mapIndexed { i, p -> p.copy(total = p.total - (last.scores.getOrNull(i) ?: 0)) }
                    rounds = rounds.dropLast(1)
                    scoreInputs = players.map { "" }
                    persist(players, rounds)
                }
            },
            onNewGame = { showResetConfirm = true }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start a new game?") },
            text = { Text("This clears the current players and score history.") },
            confirmButton = {
                TextButton(onClick = {
                    players = emptyList()
                    rounds = emptyList()
                    draftNames = listOf("", "")
                    scoreInputs = emptyList()
                    save("")
                    showResetConfirm = false
                }) { Text("New game") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SetupScreen(
    names: List<String>,
    onNameChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onStart: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Surface).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Board Score", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Add 2–12 players", fontSize = 14.sp, color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            itemsIndexed(names) { index, name ->
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", color = Muted, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { onNameChange(index, it.take(28)) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 16.sp),
                        decorationBox = { inner ->
                            Box {
                                if (name.isBlank()) Text("Player name", color = Color(0xFF9AA4AE), fontSize = 16.sp)
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    )
                    if (names.size > 2) {
                        TextButton(onClick = { onRemove(index) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("×", fontSize = 21.sp, color = Muted)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onAdd,
                enabled = names.size < 12,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("+ Add player") }
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1.25f).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Start game") }
        }
    }
}

@Composable
private fun GameScreen(
    players: List<Player>,
    rounds: List<Round>,
    scoreInputs: List<String>,
    onScoreChange: (Int, String) -> Unit,
    onAddRound: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit
) {
    val ranked = players.withIndex().sortedWith(compareByDescending<IndexedValue<Player>> { it.value.total }.thenBy { it.index })
    Column(Modifier.fillMaxSize().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Scores", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("${players.size} players · ${rounds.size} rounds", fontSize = 12.sp, color = Muted)
            }
            TextButton(onClick = onUndo, enabled = rounds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("↶ Undo", fontSize = 13.sp)
            }
            TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("New", fontSize = 13.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE9EEF5), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PLAYER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.weight(1f))
            Text("TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("ROUND", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.width(70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(players) { index, player ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 42.dp).background(Color.White, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        player.name,
                        modifier = Modifier.weight(1f).padding(end = 5.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Ink
                    )
                    Text(
                        player.total.toString(),
                        modifier = Modifier.width(58.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = if (player.total < 0) Color(0xFFB42318) else Ink
                    )
                    Box(
                        Modifier.width(70.dp).height(34.dp).background(Surface, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = scoreInputs.getOrNull(index) ?: "",
                            onValueChange = { onScoreChange(index, it) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Ink, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if ((scoreInputs.getOrNull(index) ?: "").isEmpty()) Text("0", color = Color(0xFFADB6C0), fontSize = 14.sp)
                                    inner()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Button(
            onClick = onAddRound,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Add round", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }

        if (rounds.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                "Last round: " + rounds.last().scores.mapIndexedNotNull { i, s ->
                    if (s != 0) "${players.getOrNull(i)?.name ?: "?"} $s" else null
                }.joinToString("  ·  ").ifBlank { "all scores were 0" },
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
