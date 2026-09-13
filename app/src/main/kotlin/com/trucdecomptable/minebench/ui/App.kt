package com.trucdecomptable.minebench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.trucdecomptable.minebench.BuildConfig
import com.trucdecomptable.minebench.core.BenchmarkRunner
import com.trucdecomptable.minebench.core.Economics
import com.trucdecomptable.minebench.core.Format
import com.trucdecomptable.minebench.core.HashEngine
import com.trucdecomptable.minebench.core.NetInfo
import com.trucdecomptable.minebench.core.PalierMeasure
import com.trucdecomptable.minebench.core.PalierSpec
import com.trucdecomptable.minebench.core.Sensors
import com.trucdecomptable.minebench.core.ThermalStatus
import com.trucdecomptable.minebench.net.BtcNetwork
import com.trucdecomptable.minebench.service.MiningConfig
import com.trucdecomptable.minebench.service.MiningService
import com.trucdecomptable.minebench.service.MiningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun MineBenchApp(sensors: Sensors) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("B") },
                    label = { Text("Benchmark") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("M") },
                    label = { Text("Minage") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("i") },
                    label = { Text("Infos") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> BenchmarkScreen(sensors)
                1 -> MiningScreen()
                else -> InfoScreen()
            }
        }
    }
}

// ---------------------------------------------------------------- Benchmark

@Composable
fun BenchmarkScreen(sensors: Sensors) {
    val cores = Runtime.getRuntime().availableProcessors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var net by remember { mutableStateOf(NetInfo.fallback()) }
    var netLoaded by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(10) }
    var engines by remember { mutableStateOf(setOf(HashEngine.MD, HashEngine.PURE)) }
    val threadChoices = remember(cores) {
        listOf(1, 2, maxOf(1, cores / 2), cores).distinct().sorted()
    }
    var threads by remember { mutableStateOf(setOf(2)) }
    var duties by remember { mutableStateOf(setOf(100, 50)) }
    val results = remember { androidx.compose.runtime.mutableStateListOf<PalierMeasure>() }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("prêt") }

    LaunchedEffect(Unit) {
        net = BtcNetwork.fetch()
        netLoaded = true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Réseau Bitcoin", style = MaterialTheme.typography.titleMedium)
                Text("Hashrate réseau : ${Format.hashrate(net.networkHashrate)}")
                Text("Difficulté : ${String.format(Locale.FRANCE, "%.3e", net.difficulty)}")
                Text("Prix BTC : ${Format.eur(net.btcPriceEur)}")
                Text("Source : ${if (netLoaded) net.origin else "chargement…"}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Durée par palier", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { s ->
                        FilterChip(selected = seconds == s, onClick = { seconds = s }, label = { Text("$s s") })
                    }
                }
                Text("Moteur de hachage", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HashEngine.entries.forEach { e ->
                        FilterChip(
                            selected = engines.contains(e),
                            onClick = {
                                engines = if (engines.contains(e)) engines - e else engines + e
                            },
                            label = { Text(e.label) }
                        )
                    }
                }
                Text("Threads", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    threadChoices.forEach { t ->
                        FilterChip(
                            selected = threads.contains(t),
                            onClick = { threads = if (threads.contains(t)) threads - t else threads + t },
                            label = { Text(if (t == cores) "$t (tous)" else "$t") }
                        )
                    }
                }
                Text("Charge CPU (duty cycle)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 50, 25).forEach { d ->
                        FilterChip(
                            selected = duties.contains(d),
                            onClick = { duties = if (duties.contains(d)) duties - d else duties + d },
                            label = { Text("$d %") }
                        )
                    }
                }
                Button(
                    enabled = !running && engines.isNotEmpty() && threads.isNotEmpty() && duties.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            running = true
                            results.clear()
                            val runner = BenchmarkRunner(sensors)
                            val specs = buildList {
                                engines.forEach { e ->
                                    threads.sorted().forEach { t ->
                                        duties.sortedDescending().forEach { d ->
                                            add(PalierSpec(e, t, d, seconds))
                                        }
                                    }
                                }
                            }
                            specs.forEachIndexed { index, spec ->
                                statusText = "${index + 1}/${specs.size} : ${spec.engine.label}, " +
                                    "${spec.threads} thread(s), ${spec.dutyPercent} %"
                                progress = index.toFloat() / specs.size
                                val measure = runCatching {
                                    withContext(Dispatchers.Default) { runner.run(spec) }
                                }.getOrElse { error ->
                                    statusText = "palier interrompu: ${error.javaClass.simpleName}"
                                    null
                                }
                                if (measure != null) results.add(measure)
                            }
                            progress = 1f
                            statusText = "terminé (${results.size} paliers)"
                            running = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (running) "Mesure en cours…" else "Lancer le benchmark")
                }
                if (running) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (results.isNotEmpty()) {
            Text("Résultats", style = MaterialTheme.typography.titleMedium)
            results.sortedByDescending { it.hashrate }.forEach { measure ->
                ResultCard(measure, net)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val report = buildReport(results, net, cores)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "MineBench : rapport de benchmark")
                        putExtra(android.content.Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Partager le rapport"))
                }) { Text("Partager le rapport") }
                OutlinedButton(onClick = { results.clear() }) { Text("Vider") }
            }
        }
        Text(
            "Rappel : aucun téléphone n'est rentable pour miner du Bitcoin. Le benchmark sert à mesurer " +
                "le débit réel, l'échauffement et l'effet de la limitation CPU.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResultCard(measure: PalierMeasure, net: NetInfo) {
    val eco = Economics.estimate(measure.hashrate, net)
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${measure.spec.engine.label} · ${measure.spec.threads} thread(s) · ${measure.spec.dutyPercent} %",
                style = MaterialTheme.typography.titleSmall
            )
            Text("Débit : ${Format.hashrate(measure.hashrate)}", fontFamily = FontFamily.Monospace)
            Text("Hashes : ${measure.hashes} en ${measure.elapsedMs / 1000.0} s", style = MaterialTheme.typography.bodySmall)
            Text(
                "Batterie : ${Format.temp(measure.tempStartC)} → ${Format.temp(measure.tempEndC)} · " +
                    "thermique ${ThermalStatus.label(measure.thermalEnd)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Part du réseau : ${Format.share(eco.shareOfNetwork)}", style = MaterialTheme.typography.bodySmall)
            Text("Revenu espéré : ${Format.eur(eco.revenueEurPerDay)} / jour", style = MaterialTheme.typography.bodySmall)
            Text("1 € en ${Format.duration(eco.daysToEarnOneEur)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun buildReport(results: List<PalierMeasure>, net: NetInfo, cores: Int): String {
    val sb = StringBuilder()
    sb.append("MineBench, rapport de benchmark\n")
    sb.append("Cœurs détectés : ").append(cores).append('\n')
    sb.append("Réseau : ").append(Format.hashrate(net.networkHashrate))
        .append(" · difficulté ").append(String.format(Locale.FRANCE, "%.3e", net.difficulty))
        .append(" · BTC ").append(Format.eur(net.btcPriceEur)).append('\n')
    sb.append("Source réseau : ").append(net.origin).append("\n\n")
    results.sortedByDescending { it.hashrate }.forEach { m ->
        val eco = Economics.estimate(m.hashrate, net)
        sb.append("- ").append(m.spec.engine.label)
            .append(" | ").append(m.spec.threads).append(" thread(s)")
            .append(" | ").append(m.spec.dutyPercent).append(" %")
            .append(" | ").append(Format.hashrate(m.hashrate))
            .append(" | batterie ").append(Format.temp(m.tempStartC)).append("→").append(Format.temp(m.tempEndC))
            .append(" | thermique ").append(ThermalStatus.label(m.thermalEnd))
            .append(" | revenu ").append(Format.eur(eco.revenueEurPerDay)).append("/j")
            .append('\n')
    }
    return sb.toString()
}

// -------------------------------------------------------------------- Minage

@Composable
fun MiningScreen() {
    val context = LocalContext.current
    val cores = Runtime.getRuntime().availableProcessors()
    var host by remember { mutableStateOf("solo.ckpool.org") }
    var port by remember { mutableStateOf("3333") }
    var worker by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("x") }
    var threads by remember { mutableStateOf(2f) }
    var duty by remember { mutableStateOf(50f) }
    var maxTemp by remember { mutableStateOf(42f) }
    var minBattery by remember { mutableStateOf(20f) }
    var requireCharging by remember { mutableStateOf(true) }

    val running by MiningState.running.collectAsState()
    val status by MiningState.status.collectAsState()
    val hashrate by MiningState.hashrate.collectAsState()
    val accepted by MiningState.sharesAccepted.collectAsState()
    val rejected by MiningState.sharesRejected.collectAsState()
    val difficulty by MiningState.difficulty.collectAsState()
    val jobId by MiningState.jobId.collectAsState()
    val uptime by MiningState.uptimeMs.collectAsState()
    val temp by MiningState.batteryTempC.collectAsState()
    val battery by MiningState.batteryPercent.collectAsState()
    val thermal by MiningState.thermalStatus.collectAsState()
    val dutyEffective by MiningState.dutyEffective.collectAsState()
    val blockFound by MiningState.blockFound.collectAsState()
    val log by MiningState.log.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pool Stratum", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(host, { host = it }, label = { Text("Hôte") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(worker, { worker = it },
                    label = { Text("Worker (adresse BTC.nom)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Mot de passe") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Rappel : en solo lottery, l'adresse BTC sert d'identifiant de compte.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Limitation CPU et garde-fous", style = MaterialTheme.typography.titleMedium)
                Text("Threads : ${threads.toInt()} sur $cores")
                Slider(value = threads, onValueChange = { threads = it }, valueRange = 1f..cores.toFloat(),
                    steps = maxOf(0, cores - 2))
                Text("Charge CPU max : ${duty.toInt()} %")
                Slider(value = duty, onValueChange = { duty = it }, valueRange = 10f..100f, steps = 8)
                Text("Température d'arrêt : ${maxTemp.toInt()} °C")
                Slider(value = maxTemp, onValueChange = { maxTemp = it }, valueRange = 35f..50f, steps = 14)
                Text("Batterie minimale : ${minBattery.toInt()} %")
                Slider(value = minBattery, onValueChange = { minBattery = it }, valueRange = 5f..50f, steps = 8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = requireCharging, onCheckedChange = { requireCharging = it })
                    Text("  Miner uniquement en charge")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    MiningService.start(
                        context,
                        MiningConfig(
                            host = host.trim(),
                            port = port.trim().toIntOrNull() ?: 3333,
                            worker = worker.trim(),
                            password = password,
                            threads = threads.toInt(),
                            dutyPercent = duty.toInt(),
                            maxTempC = maxTemp.toDouble(),
                            minBatteryPercent = minBattery.toInt(),
                            requireCharging = requireCharging
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Démarrer") }
            OutlinedButton(
                enabled = running,
                onClick = { MiningService.stop(context) },
                modifier = Modifier.weight(1f)
            ) { Text("Arrêter") }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Session", style = MaterialTheme.typography.titleMedium)
                Text("État : $status")
                Text("Hashrate : ${Format.hashrate(hashrate)}", fontFamily = FontFamily.Monospace)
                Text("Shares : $accepted acceptées / $rejected refusées")
                Text("Difficulté pool : $difficulty · job $jobId", style = MaterialTheme.typography.bodySmall)
                Text("Durée : ${uptime / 1000} s · charge effective $dutyEffective %")
                Text("Batterie : ${if (battery != null) "$battery %" else "n/d"} · ${Format.temp(temp)} · thermique ${ThermalStatus.label(thermal)}")
                if (blockFound) Text("BLOC TROUVÉ !", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (log.isNotEmpty()) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Journal", style = MaterialTheme.typography.titleMedium)
                    log.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- Infos

@Composable
fun InfoScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MineBench ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Deux usages : mesurer le débit SHA-256d réel de l'appareil (mode benchmark), " +
                        "et miner pour de vrai sur un pool Stratum avec limitation de charge (mode minage)."
                )
                Text(
                    "Aucune rentabilité : un téléphone produit de l'ordre de 0,000001 € par jour au cours " +
                        "et à la difficulté actuels. L'application sert à apprendre, mesurer, et éventuellement " +
                        "tenter la loterie du minage solo.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Limitation CPU : nombre de threads, duty cycle (part du temps réellement travaillée), " +
                        "arrêt sur température de batterie, arrêt sur statut thermique du système, arrêt si " +
                        "l'appareil est débranché ou sous le seuil de batterie.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Attention : chauffer un téléphone en continu dégrade la batterie. Google Play interdit " +
                        "les applications de minage, cette application se distribue en APK direct.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
