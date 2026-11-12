package app.seguridad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android-only code that the JVM cannot run, checked in its source (audit 1.3.0, batch F1). The behaviour itself is in
 * the owner's phone test (README).
 */
class PlatformSourcesTest {
    private fun main(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** Code only: comments say what it does, but they must not make the check pass. */
    private fun code(path: String) = main(path).lines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    /**
     * 1.4.2, the owner's phone test (F-5): «Activar huella» with the phone turned did nothing. The phone's fingerprint
     * dialog is portrait-only: it turned the screen upright and back, each turn re-created the activity, and a
     * re-creation ends the enable (M-4). The main activity handles a turn or a resize itself (Compose redraws), so the
     * prompt and the enable go on. Theme, language and the rest still re-create it.
     */
    @Test fun `turning the phone does not re-create the main activity`() {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/AndroidManifest.xml").isFile) dir = dir.parentFile
        val manifest = File(dir, "app/src/main/AndroidManifest.xml").readText()
        val main = Regex("""<activity\s+android:name="\.MainActivity"[^>]*>""").find(manifest)?.value ?: error("MainActivity")
        val changes = Regex("""android:configChanges="([^"]*)"""").find(main)?.groupValues?.get(1)?.split('|')?.toSet() ?: emptySet()
        assertEquals(setOf("orientation", "screenSize", "screenLayout", "smallestScreenSize"), changes)
    }

    /**
     * 1.4.2, the owner's request: every file picker says which place it is ([PickerPlace]), so it opens where it was
     * used last; «Importar de otra app» is the place kept only while the app runs; the folder of the automatic copies
     * opens on that folder; deleting the vault forgets every place.
     */
    @Test fun `every file picker opens where it was used last`() {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/app/seguridad").isDirectory) dir = dir.parentFile
        val calls = File(dir, "app/src/main/java/app/seguridad").walk().filter { it.isFile && it.name.endsWith(".kt") }.flatMap { f ->
            Regex("""rememberDocumentPicker\(([^\n]*)""").findAll(f.readText()).map { f.name to it.groupValues[1] }
        }.filterNot { (_, args) -> args.isBlank() }.toList()
        assertEquals(29, calls.size) // 2.0, task 1: +1, the copy before the upgrade to format v4 (VaultUpgrade.kt).
        for ((file, args) in calls) assertTrue("$file: $args", Regex("""place = (PickerPlace\.[A-Z_]+|null)""").containsMatchIn(args))
        assertTrue("place = PickerPlace.IMPORT_OTHER" in code("ui/screens/ImportScreen.kt"))
        val settings = code("ui/screens/SettingsScreen.kt")
        assertTrue("OpenDocumentTree(), place = null" in settings && "picker.launchOrError(folderStart(folder))" in settings)
        val vault = code("ui/screens/VaultScreen.kt")
        assertTrue(vault.indexOf("pickerPlaces(context)?.forgetAll()") in 0 until vault.indexOf("store.destroy()"))
        // The places kept only in memory go at every lock and unlock (code review of 1.4.2).
        assertTrue(".drop(1).collect { pickerPlaces.forgetSession() }" in code("AppContainer.kt"))
    }

    /**
     * 1.4.2 (F-5): the notice of an interrupted «Activar huella» is marked as seen only once it was shown. Marked first,
     * a second re-creation while it was on screen lost it.
     */
    @Test fun `the interrupted-enable notice is marked seen after it is shown`() {
        val src = code("ui/screens/VaultScreen.kt")
        val show = src.indexOf("snackbar.showSnackbar(message)")
        val seen = src.indexOf("BiometricVault.interruptionShown(message)")
        assertTrue(show >= 0 && seen > show)
        // Code review of 1.4.2: cancelled by leaving the list it still counts as seen, but not by a re-creation.
        val fin = src.indexOf("} finally {", show)
        assertTrue(fin in show until seen)
        assertTrue("if (activity?.isChangingConfigurations != true) BiometricVault.interruptionShown(message)" in src)
    }

    /**
     * 1.4.1, the owner's phone test: deleting an entry went back twice. Its detail screen
     * leaves when the entry is gone (LockedNotice, the same as a lock), and it also called onBack after the delete, so a
     * second popBackStack took the vault list too (a blank screen, or Herramientas). The entry going away is what leaves,
     * once, as the contact screen does.
     */
    @Test fun `deleting an entry goes back once, when the entry is gone`() {
        val src = code("ui/screens/VaultEntryScreen.kt")
        assertTrue("if (entry == null) return LockedNotice(onBack)" in src)
        assertTrue("store.delete(entry.id)" in src)
        assertFalse("a second onBack after the delete", Regex("""store\.delete\(entry\.id\)\)\s*onBack\(\)""").containsMatchIn(src))
    }

    /**
     * 1.4.1, F-1 hardened: a «back» pops only the screen that asks for it, and only while that screen is on top. A screen
     * that leaves by itself (LockedNotice) and also has a button that goes back can never pop the screen under it, the
     * vault list or the start destination (the blank screen, or another tab, of F-1).
     */
    @Test fun `back pops only the screen that asks, while it is on top`() {
        val src = code("ui/AppNavHost.kt")
        assertTrue("backOnce reads the entry that composes it", "LocalLifecycleOwner.current as? NavBackStackEntry" in src)
        assertTrue("and pops only while it is the current one", "if (nav.currentBackStackEntry === entry) {" in src)
        // A call while another screen is on top waits until this one is on top again (the 1.4.1 audit, finding 1).
        assertTrue("a waiting back", "if (pending && top === entry) {" in src && "nav.currentBackStackEntryFlow.collect" in src)
        val guard = src.indexOf("private fun backOnce(")
        assertTrue("every popBackStack is in backOnce", guard > 0 && Regex("""popBackStack\(""").findAll(src).all { it.range.first > guard })
        assertFalse("no shared, unguarded back", Regex("""\bval back\b""").containsMatchIn(src))
        assertFalse("every screen gets backOnce(nav)", Regex("""(onBack|onDone) = back\b""").containsMatchIn(src))
    }

    /**
     * 1.4.1, F-4 (the owner): after deleting «Beta» from «Alfa, Beta, Gamma», the vault list is back where it was, at
     * «Alfa, Gamma», not at the top. The search, the type and tag filters and the scroll position live in the list's
     * ViewModel (never in saved state: the search can name an entry), so opening, editing or deleting an entry keeps
     * them, and a lock of the vault drops them.
     */
    @Test fun `the vault list keeps its search, filters and place after an entry, and drops them on lock`() {
        val src = code("ui/screens/VaultScreen.kt")
        assertTrue("the list's own ViewModel", "private class EntryListViewModel(" in src)
        // The place as two numbers, never the LazyListState (it holds the list's last layout; the 1.4.1 audit, finding 2).
        assertTrue("the scroll position is the ViewModel's", "LazyListState(list.firstIndex, list.firstOffset)" in src)
        assertTrue("and goes back to it", "list.firstIndex = listState.firstVisibleItemIndex" in src)
        assertFalse("no LazyListState kept in the ViewModel", Regex("""var listState by""").containsMatchIn(src))
        assertTrue("a gone tag filter is dropped", "if (tag != null && tag !in allTags) tag = null" in src)
        for (v in listOf("query", "type", "tag")) assertTrue(v, "var $v by list::$v" in src)
        assertFalse("no search in local state", Regex("""var query by remember""").containsMatchIn(src))
        assertFalse("nothing of the list in saved state", Regex("""rememberSaveable""").containsMatchIn(src))
        // From the vault's own state, in the ViewModel: a lock while another tab is shown drops them too.
        assertTrue("a lock drops them", "store.state.collect { if (it !is VaultState.Unlocked) reset() }" in src)
        assertFalse("not only while the list is on screen", "list.reset()" in src)
    }

    @Test fun `COV2-4 the window and every dialog are marked accessibility data sensitive on Android 14+`() {
        val src = code("android/SecureWindow.kt")
        val marks = Regex("""setAccessibilityDataSensitive\(View\.ACCESSIBILITY_DATA_SENSITIVE_YES\)""").findAll(src).count()
        assertTrue("secureWindow() and FilterObscuredTouches() must both set it", marks >= 2)
        assertTrue("guarded by API 34", "Build.VERSION_CODES.UPSIDE_DOWN_CAKE" in src)
    }

    @Test fun `COV2-3 MainActivity tells the vault when another app has the focus in multi-window`() {
        val src = code("MainActivity.kt")
        assertTrue("onTopResumedActivityChanged" in src)
        assertTrue("isInMultiWindowMode" in src)
        assertTrue("container.leftForAnotherApp()" in src)
        assertTrue("container.backFromAnotherApp()" in src)
    }

    @Test fun `COV2-3 M3 PIN y codigos hears the multi-window away signal for its whole entry`() {
        val src = code("ui/screens/CodesScreen.kt")
        assertTrue("session.otherAppFocused(" in src)
        assertTrue("session.otherAppLeft(" in src)
        assertTrue("awayInOtherApp" in code("ui/AppNavHost.kt"))
    }

    @Test fun `COV1-1 the text, file and leak tools follow the PIN y codigos rule, in a ViewModel`() {
        val src = code("ui/screens/SecurityToolsScreen.kt")
        // One ViewModel per tool, each watching the time away, the other app and the vault, and the screen's lifecycle.
        for (vm in listOf("TextCryptoViewModel", "FileCryptoViewModel", "BreachViewModel")) assertTrue(vm, "class $vm(" in src)
        assertEquals(3, Regex("""watchAway\(session\.guard, store, lockAfterMs, awayInOtherApp\)""").findAll(src).count())
        assertEquals(3, Regex("""GuardEffects\(s\.guard, lockAfterMs\)""").findAll(src).count())
        // Nothing of theirs in saved state (F6 round 1, M2: not even the decrypted copy's path any more).
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        val wiring = code("ui/screens/ScreenGuardWiring.kt")
        assertTrue("guard.otherAppFocused(" in wiring && "guard.otherAppLeft(" in wiring && "guard.vaultStateIs(" in wiring)
        assertTrue("Lifecycle.Event.ON_STOP" in wiring && "Lifecycle.Event.ON_START" in wiring && "onDispose" in wiring)
        // The QR inspector hears the multi-window signal too (F1 open point).
        val qr = code("ui/screens/QrInspectScreen.kt")
        assertTrue("away.otherAppFocused(" in qr && "away.otherAppLeft(" in qr)
        assertTrue(Regex("""QrInspectScreen\([^)]*awayInOtherApp""").containsMatchIn(code("ui/AppNavHost.kt")))
    }

    /** COV1-1, one place (code review of 1.4.2): the tools that wipe what was typed say it with [AwayNote], every case folded. */
    @Test fun `the away note says the short line and folds every case`() {
        val src = code("ui/screens/MoreInfo.kt").substringAfter("internal fun AwayNote(")
        assertTrue("Text(TOOLS_AWAY_WIPE_SHORT" in src.substringBefore("MoreInfo {"))
        assertTrue("Text(TOOLS_AWAY_WIPE_NOTE" in src.substringAfter("MoreInfo {"))
        assertEquals(3, Regex("""AwayNote\(\)""").findAll(code("ui/screens/SecurityToolsScreen.kt")).count())
    }

    @Test fun `F6 M1 every text field of the three tools counts as use of the screen`() {
        val src = code("ui/screens/SecurityToolsScreen.kt")
        val parts = listOf(
            src.substringAfter("private fun SecretInput(").substringBefore("private fun hasSecret("),
            src.substringAfter("fun TextCryptoScreen(").substringBefore("private fun SeedHelper("),
            src.substringAfter("fun BreachScreen("),
        )
        var fields = 0
        for (part in parts) {
            for (m in Regex("""onValueChange = \{[^\n]*""").findAll(part)) {
                fields++
                assertTrue(m.value, "guard.typed()" in m.value)
            }
            for (m in Regex("""PasswordField\([^\n]*""").findAll(part)) {
                fields++
                assertTrue(m.value, "guard.typed()" in m.value)
            }
            for (m in Regex("""SeedHelper\(bip39[^\n]*""").findAll(part)) assertTrue(m.value, "guard.typed()" in m.value)
        }
        assertEquals("input ×2, password, key, and «¿Filtrada?»", 5, fields)
        assertEquals(2, Regex("""guard = s\.guard""").findAll(src).count()) // SecretInput in «Cifrar texto» and «Cifrar archivos»
        assertTrue("used(SystemClock.elapsedRealtime())" in code("ui/screens/ScreenGuardWiring.kt"))
    }

    @Test fun `F6 M2 and M7 the decrypted copy is the session's, and the file's name is read on IO`() {
        val src = code("ui/screens/SecurityToolsScreen.kt")
        val screen = src.substringAfter("fun FileCryptoScreen(").substringBefore("@Composable\nprivate fun StrengthBar")
        val callback = screen.substringAfter("val saveDecrypted = ").substringBefore("val ready = vm.decrypted")
        assertTrue("s.takePending()" in callback && "s.isCurrent(copy.run)" in callback)
        assertTrue("No se ha guardado: vuelve a descifrar el archivo." in main("ui/screens/SecurityToolsScreen.kt"))
        assertTrue("onDiscard = { deleteLater(appContext, it) }" in src)
        assertFalse("displayName(" in screen)
        assertTrue(Regex("""withContext\(Dispatchers\.IO\) \{ runCatching \{ displayName\(appContext, uri\) \}""").findAll(src + code("ui/screens/HashLabScreen.kt")).count() >= 2)
    }

    @Test fun `F6 N5 to N7 the decrypted copy is deleted on IO, the name read is shown, a late success is not`() {
        val src = code("ui/screens/SecurityToolsScreen.kt")
        val vm = src.substringAfter("private class FileCryptoViewModel(").substringBefore("@Composable\nfun FileCryptoScreen(")
        // N5: every deletion in the ViewModel goes through deleteNow (IO, NonCancellable) or deleteLater (the app's scope).
        assertEquals(1, Regex("""\.delete\(\)""").findAll(vm).count())
        assertTrue("withContext(NonCancellable + Dispatchers.IO) { file.delete() }" in vm)
        // N7: a success after a wipe is not shown.
        assertTrue("if (session.isCurrent(run) || !message.first) session.message = message" in vm)
        // N6: «Comprobando el archivo…» also while the name is read.
        assertTrue("if (checking || (source != null && vm.sourceName == null))" in src)
    }

    @Test fun `COV1-2 Laboratorio de hashes cancels the run before, and its button is off while one runs`() {
        val src = code("ui/screens/HashLabScreen.kt")
        assertTrue("job?.cancel()" in src)
        assertTrue("file.finish(mine, hash)" in src)
        assertTrue("digest(chosen, counting) {" in src && "ensureActive()" in src)
        assertTrue("withContext(Dispatchers.IO)" in src)
        assertTrue("enabled = !f.busy" in src)
        assertTrue("TextButton(onClick = vm::cancel)" in src)
        // The HMAC key, the message and the text never reach saved state.
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        assertTrue("override fun onCleared()" in src && "session.wipe()" in src)
        // Every copy goes through the clearing helper.
        assertFalse("setPrimaryClip" in src || "LocalClipboard" in src)
        assertTrue("copySensitive(context, value)" in src)
        assertTrue("HASH_LAB_NO_MATCH" in src && "No se pudo leer" in main("tools/hashlab/HashLabTexts.kt"))
        // The failure is shown by the screen, not only defined; so is «Lo que no protege».
        assertTrue("if (f.failed) Text(HASH_LAB_FILE_FAILED" in src)
        assertTrue("Text(HASH_LAB_NOT_PROTECTED," in src && "Text(HASH_LAB_NOT_PROTECTED_TITLE" in src)
        // Important 1: the guard of «Cifrar texto» (vault lock, other app, time away) and its note.
        assertTrue("watchAway(session.guard, store, lockAfterMs, awayInOtherApp)" in src)
        assertTrue("GuardEffects(s.guard, lockAfterMs)" in src && "ToolColumn(HASH_LAB_TITLE, onBack, s.guard)" in src)
        assertTrue("AwayNote {" in src && "session.guard.typed()" in src)
        assertTrue("HashLabScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp" in code("ui/AppNavHost.kt"))
        // The over-limit note sits under each field, the algorithm change never re-reads a file, and progress is the last run's.
        assertEquals(6, Regex("""TooLongNote\(""").findAll(src).count()) // the definition and one note per field
        assertTrue("HashLabSession(ComposeCells, SystemClock.elapsedRealtime()) { job?.cancel(); job = null }" in src)
        // Branch review C, M-1: only the run started last writes its progress and size; the session refuses an older one,
        // also after a wipe (HashLabSessionTest).
        assertTrue("session.progress(mine, n)" in src && "session.sized(mine, size)" in src && "val mine = session.startFile()" in src)
    }

    @Test fun `Laboratorio clasico has the guard of the other tools, bounded fields and the clearing copy`() {
        val src = code("ui/screens/ClassicLabScreen.kt")
        // Typed text never reaches saved state; the ViewModel wipes it on being cleared.
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        assertTrue("override fun onCleared()" in src && "session.wipe()" in src)
        // The guard of «Cifrar texto» (vault lock, other app, time away) and its note, wired from the navigation.
        assertTrue("watchAway(session.guard, store, lockAfterMs, awayInOtherApp)" in src)
        assertTrue("GuardEffects(s.guard, lockAfterMs)" in src && "ToolColumn(CLASSIC_TITLE, onBack, s.guard)" in src)
        assertTrue("AwayNote {" in src && "session.guard.typed()" in src)
        assertTrue("ClassicLabScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp" in code("ui/AppNavHost.kt"))
        // Every field is bounded, and every copy goes through the clearing helper.
        assertTrue("MAX_CLASSIC_CHARS" in src && "MAX_CLASSIC_KEY_CHARS" in src && "vm.limited(" in src)
        // M-3: no field refuses a paste silently, and the rotors and reflector are in the wiped session (M-4).
        assertFalse("vm.limited(null" in src)
        // N-4: the over-limit note goes with a tab change and with leaving the screen.
        assertTrue("fun chooseTab(i: Int) {\n        tab = i\n        refused = null" in src && "onDispose { vm.clearRefused() }" in src)
        assertEquals(5, Regex("""KeyTooLong\(vm, ClassicLabViewModel\.Field""").findAll(src).count())
        assertTrue("s.rotors" in src && "s.reflector" in src && "vm.rotors" !in src)
        assertFalse("setPrimaryClip" in src || "LocalClipboard" in src)
        assertTrue("copySensitive(context, value)" in src)
        // The visible notice, word for word, and the entry in Herramientas.
        assertTrue("Text(CLASSIC_NOTICE)" in src)
        assertTrue("Son cifrados históricos, rotos desde hace décadas: no protegen nada. Sirven para aprender cómo funcionaban." in main("tools/classic/ClassicTexts.kt"))
        assertTrue("Tool(\"tools/classic\"" in code("ui/screens/ToolsScreen.kt"))
    }

    @Test fun `Repartir un secreto has the guard of the other tools, runs off the main thread and copies with clearing`() {
        val src = code("ui/screens/SecretShareScreen.kt")
        // Nothing secret reaches saved state; the ViewModel wipes the session on being cleared, and a wipe stops the run.
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        assertTrue("override fun onCleared()" in src && "session.wipe()" in src)
        assertTrue("val session: SecretShareSession = SecretShareSession(ComposeCells, SystemClock.elapsedRealtime()) { runs.stop() }" in src)
        assertTrue("private val runs: SecretShareRuns = SecretShareRuns(session, viewModelScope)" in src)
        // The guard of «Cifrar texto» (vault lock, other app, time away) and its note, wired from the navigation.
        assertTrue("watchAway(session.guard, store, lockAfterMs, awayInOtherApp)" in src)
        assertTrue("GuardEffects(s.guard, lockAfterMs)" in src && "ToolColumn(SHARE_TITLE, onBack, s.guard)" in src)
        assertTrue("AwayNote {" in src && "session.guard.typed()" in src)
        assertTrue("SecretShareScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp" in code("ui/AppNavHost.kt"))
        assertTrue("composable(\"tools/share-secret\")" in code("ui/AppNavHost.kt"))
        assertTrue("Tool(\"tools/share-secret\", \"🧩\", SHARE_TITLE, SHARE_SUBTITLE)" in code("ui/screens/ToolsScreen.kt"))
        // PBKDF2 off the main thread (SecretShareRuns, tested in SecretShareRunsTest), each run on a snapshot of the form.
        assertTrue("val r = splitRequest(s.secretInput, isPhrase = !hexInput, s.passphrase, s.groupThreshold, s.groups, words)" in src)
        assertTrue("val r = combineRequest(s.typedParts, s.passphrase)" in src)
        assertTrue("runs.split({ check -> slip.splitSecret(master, pass, r.groupThreshold, r.groups, check = check) })" in src)
        assertTrue("runs.combine({ check -> slip.combineShares(r.parts, pass, check) }) { pass.fill('\\u0000') }" in src)
        assertTrue("master.fill(0)" in src)
        assertFalse("Dispatchers.Default" in src)
        assertTrue("work: CoroutineDispatcher = Dispatchers.Default" in main("tools/slip39/SecretShareRuns.kt"))
        // The word lists are read on IO.
        assertTrue("withContext(Dispatchers.IO)" in src && "\"slip39_english.txt\"" in src)
        // Every field is bounded, and every copy goes through the clearing helper.
        for (max in listOf("SHARE_MAX_SECRET_CHARS", "SHARE_MAX_PARTS_CHARS", "SLIP39_MAX_PASSPHRASE_CHARS", "SHARE_MAX_NUMBER_CHARS")) {
            assertTrue(max, "vm.limited(SecretShareViewModel.Field." in src && max in src)
        }
        assertFalse("setPrimaryClip" in src || "LocalClipboard" in src)
        assertEquals(2, Regex("""copySensitive\(context, value\)""").findAll(src).count())
        // The warning in its own card before the parts, and what the tool does not protect.
        assertTrue(src.indexOf("Text(SHARE_WARNING)") in 0 until src.indexOf("val parts = s.parts ?: return"))
        assertTrue("Text(SHARE_NOT_PROTECTED)" in src && "Text(SHARE_RESULT_NOTE" in src)
        // Review I-1: the phrase of the recovered bytes carries its note, between the phrase and «Copiar».
        assertTrue("Output(context, phrase, note = SHARE_RESULT_PHRASE_NOTE)" in src)
        val output = src.substringAfter("private fun Output(").substringBefore("\n}\n")
        assertTrue(output, output.indexOf("note?.let { Text(it") in 0 until output.indexOf("copySensitive(context, value)"))
        assertTrue(
            ("\"Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase \" +\n" +
                "        \"daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39.\"") in main("tools/slip39/SecretShareTexts.kt"),
        )
        val texts = main("tools/slip39/SecretShareTexts.kt")
        assertTrue(
            ("\"Estas partes no son una copia SLIP-39 de cartera. Metidas en una cartera de hardware como recuperación SLIP-39 \" +\n" +
                "        \"darían otra cartera distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía.\"") in texts,
        )
        // checkAssets fails the build if the list is not in the APK.
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/build.gradle.kts").isFile) dir = dir.parentFile
        val gradle = File(dir, "app/build.gradle.kts").readText()
        val anchor = "val checkAssets = tasks.register(\"checkAssets\")"
        assertTrue("app/build.gradle.kts must declare $anchor", anchor in gradle)
        val task = gradle.substringAfter(anchor).substringBefore("tasks.named(\"check\")")
        assertTrue(task, "\"slip39_english.txt\"" in task.substringAfter("val wordLists = listOf(").substringBefore(")"))
    }

    @Test fun `Comprobar una copia has the guard of the other tools, stops its run and changes neither the vault nor a copy`() {
        val src = code("ui/screens/BackupCheckScreen.kt")
        // Nothing secret reaches saved state; the ViewModel wipes the session on being cleared, and a wipe stops the run.
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        assertTrue("override fun onCleared()" in src && "session.wipe()" in src)
        assertTrue("val session: BackupCheckSession = BackupCheckSession(ComposeCells, SystemClock.elapsedRealtime()) { stopAll() }" in src)
        assertTrue("private val runs: BackupCheckRuns = BackupCheckRuns(session, viewModelScope) { store.lockEpoch }" in src)
        // The guard of «Cifrar texto» (vault lock, other app, time away) and its note, wired from the navigation.
        assertTrue("watchAway(session.guard, store, lockAfterMs, awayInOtherApp)" in src)
        assertTrue("GuardEffects(s.guard, lockAfterMs)" in src && "ToolColumn(BACKUP_CHECK_TITLE, onBack, s.guard)" in src)
        assertTrue("AwayNote {" in src && "session.guard.typed()" in src && "Text(BACKUP_CHECK_STOPS_NOTE" in src)
        // What the check does not prove, always on screen.
        assertTrue("Text(BACKUP_CHECK_NOT_PROVEN," in src)
        // The password leaves the field as a CharArray the run owns and zeroes (BackupCheckSessionTest).
        assertTrue("val master = session.password.toCharArray()" in src && "session.password = \"\"" in src && "runs.start(master)" in src)
        assertTrue("master?.fill('\\u0000')" in code("vault/BackupCheckSession.kt") && "password.fill('\\u0000')" in code("vault/BackupCheckSession.kt"))
        // checkBackup on IO, in cacheDir/comprobar/ (one folder per run, BackupCheckTest).
        assertTrue("withContext(Dispatchers.IO)" in src && "checkBackup(it.buffered(), pw, dir)" in src && "File(appContext.cacheDir, BACKUP_CHECK_DIR)" in src)
        // A lock of the vault and leaving the app (not a rotation) stop the run going on.
        assertTrue("if (wasOpen && !open) runs.stop()" in src)
        assertTrue("LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (activity?.isChangingConfigurations != true) vm.leftApp() }" in src)
        // The screen reads the vault's state and its lock count and, for the picker, touches it (the idle count and the
        // A11 clock mark, as «Importar copia»); none of its files changes the vault, a setting, the fingerprint or a copy.
        assertEquals(setOf("state", "lockEpoch", "touch"), Regex("""\bstore\.(\w+)""").findAll(src).map { it.groupValues[1] }.toSet())
        // m3: the picker as «Importar copia»'s (VaultBackup.kt): the vault stays open while it is on top, the time away
        // counts from the tap, and the flag goes back if the picker never opened (MainActivity.onStart takes it back).
        // The four steps in this order, whatever the spacing and line breaks.
        val flat = src.replace(Regex("""\s+"""), " ")
        assertTrue(
            flat,
            ("VaultLock.keepOpenOnce = true store.touch() pickerError = pick.launchOrError(arrayOf(\"*/*\")) " +
                "if (pickerError != null) VaultLock.keepOpenOnce = false") in flat,
        )
        val importer = code("ui/screens/VaultBackup.kt").substringAfter("import = {").substringBefore("changeMaster = ")
        assertTrue(importer, "VaultLock.keepOpenOnce = true" in importer && "store.touch()" in importer && "importer.launchOrError(arrayOf(\"*/*\"))" in importer)
        assertEquals(1, Regex("""store\.touch\(\)""").findAll(src).count())
        assertEquals(1, Regex("""keepOpenOnce = true""").findAll(src).count())
        assertTrue("VaultLock.keepOpenOnce = false" in code("MainActivity.kt").substringAfter("override fun onStart()").substringBefore("override fun "))
        val files = listOf(src, code("vault/BackupCheck.kt"), code("vault/BackupCheckSession.kt"))
        val writes = listOf(
            "importReplace", "exportBackup", "upsert(", "addAll(", "addAttachment", "removeAttachment", "changeMaster", "destroy(",
            "VaultStore(", "SettingsStore", "settings", ".update(", "openOutputStream", "writeBackup", "BiometricVault", "autoBackup",
            "AutoBackup", "writeText", "writeBytes", "vault.bin",
        )
        for (file in files) for (w in writes) assertFalse(w, w in file)
        for (w in listOf("keepOpenOnce", "touch()")) for (file in files.drop(1)) assertFalse(w, w in file)
        assertFalse("VaultStore" in code("vault/BackupCheck.kt") || "VaultStore" in code("vault/BackupCheckSession.kt"))
        // The row in Ajustes, the route, and the leftovers of a run cut short go at start.
        assertTrue("onClick = onCheckBackup" in code("ui/screens/SettingsScreen.kt") && "Text(BACKUP_CHECK_TITLE)" in code("ui/screens/SettingsScreen.kt"))
        assertTrue("composable(\"settings/check-backup\")" in code("ui/AppNavHost.kt"))
        assertTrue("BackupCheckScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))" in code("ui/AppNavHost.kt"))
        assertTrue("removeBackupCheckLeftovers(File(cacheDir, BACKUP_CHECK_DIR))" in code("SeguridadApp.kt"))
        assertTrue("const val BACKUP_CHECK_TITLE = \"Comprobar una copia\"" in main("vault/BackupCheckTexts.kt"))
        // The import uses the same checks (BackupCheck.kt), so they cannot drift apart.
        val store = code("vault/VaultStore.kt")
        assertTrue("checkCopyEntries(incomingEntries)" in store && "attachmentInCopy(it, backup.first)" in store)
    }

    /** Ruling R3: no other product's brand in a user-visible text; the licence's attribution lives on the Licencias screen. */
    @Test fun `no wallet brand appears in the app's texts outside the licences`() {
        var dir: File? = File(".").absoluteFile
        var root: File? = null
        while (dir != null && root == null) {
            File(dir, "app/src/main/java").takeIf { it.isDirectory }?.let { root = it }
            dir = dir.parentFile
        }
        val brands = Regex("(?i)trezor|keystone|satoshilabs")
        val found = root!!.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "LicensesScreen.kt" }
            .filter { brands.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(emptyList<String>(), found)
        assertTrue("SatoshiLabs" in main("ui/screens/LicensesScreen.kt"))
    }

    @Test fun `HERR-1 Limpiar foto passes its cancellation to the readers`() {
        val src = code("ui/screens/PhotoCleanScreen.kt")
        assertTrue("PhotoCleaner.inspect(bytes, check)" in src && "PhotoCleaner.clean(bytes, check)" in src)
    }

    @Test fun `ANDROID-1 a deleted share copy has its URI permission revoked`() {
        assertTrue("revokeUriPermission" in code("android/ImageShare.kt"))
        assertTrue("revokeSharedCopy" in code("SeguridadApp.kt"))
    }

    /**
     * [src] with every comment and the inside of every string literal turned into spaces: same length and same line
     * breaks, so an index found in [src] is the same here, and a parenthesis or brace inside a text is not counted.
     */
    private fun blank(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        fun clear(from: Int, to: Int) { for (k in from until to) if (out[k] != '\n') out[k] = ' ' }
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> { val e = src.indexOf('\n', i).let { if (it < 0) src.length else it }; clear(i, e); i = e }
                src.startsWith("/*", i) -> {
                    var depth = 0
                    var j = i
                    while (j < src.length) {
                        if (src.startsWith("/*", j)) { depth++; j += 2 } else if (src.startsWith("*/", j)) { depth--; j += 2; if (depth == 0) break } else j++
                    }
                    clear(i, j); i = j
                }
                src.startsWith("\"\"\"", i) -> { val e = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }; clear(i + 3, e - 3); i = e }
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != '"') j += if (src[j] == '\\') 2 else 1
                    clear(i + 1, j); i = j + 1
                }
                src[i] == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != '\'') j += if (src[j] == '\\') 2 else 1
                    clear(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** The index just past the bracket that closes the one at [open] in blanked code [b]. */
    private fun closing(b: String, open: Int): Int {
        val o = b[open]
        val c = if (o == '(') ')' else '}'
        var depth = 0
        for (k in open until b.length) {
            if (b[k] == o) depth++ else if (b[k] == c) { depth--; if (depth == 0) return k + 1 }
        }
        error("Unbalanced ${o} at $open")
    }

    /** The call at [at] (its name) up to the end of its arguments and of a trailing lambda, in blanked code [b]. */
    private fun callRange(b: String, at: Int): IntRange {
        val paren = b.indexOf('(', at)
        var end = closing(b, paren)
        var k = end
        while (k < b.length && b[k].isWhitespace()) k++
        if (k < b.length && b[k] == '{') end = closing(b, k)
        return at until end
    }

    /** Whether [index] of [src] is inside the content lambda of a call to [call]. */
    private fun insideCall(src: String, call: String, index: Int): Boolean {
        val b = blank(src)
        return Regex("""\b$call\b""").findAll(b).any { m ->
            val start = m.range.first
            if (start >= index || b.lastIndexOf("fun ", start).let { it >= 0 && b.substring(it + 4, start).isBlank() }) return@any false
            var k = m.range.last + 1
            while (k < b.length && b[k].isWhitespace()) k++
            if (k < b.length && b[k] == '(') { k = closing(b, k); while (k < b.length && b[k].isWhitespace()) k++ }
            k < b.length && b[k] == '{' && index in k until closing(b, k)
        }
    }

    @Test fun `ANDROID-3 every dialog and menu window of the app filters obscured touches`() {
        val root = mainDir()
        val window = Regex("""\b(AlertDialog|BasicAlertDialog|Dialog|DropdownMenu|ExposedDropdownMenu|ModalBottomSheet|Popup|DatePickerDialog)\(""")
        val missing = mutableListOf<String>()
        var windows = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            val b = blank(src)
            for (m in window.findAll(b)) {
                val before = b.substring(b.lastIndexOf('\n', m.range.first) + 1, m.range.first)
                if (before.trimStart().startsWith("import") || before.trimEnd().endsWith("fun")) continue
                windows++
                val range = callRange(b, m.range.first)
                if ("FilterObscuredTouches()" !in b.substring(range.first, range.last + 1)) {
                    missing += "${f.name}:${src.substring(0, m.range.first).count { it == '\n' } + 1} ${m.groupValues[1]}"
                }
            }
        }
        assertTrue("Too few dialogs found: $windows", windows >= 15)
        assertTrue("Windows without FilterObscuredTouches():\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    private fun mainDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        error("app/src/main/java/app/seguridad not found")
    }

    @Test fun `the source helpers see through texts and comments`() {
        val src = "AlertDialog(text = { Text(\"a ) b\") /* ) */ }) // )\nNoCopy(Modifier) { Field(\"x\") }\nOther { \"y\" }"
        assertEquals(src.length, blank(src).length)
        assertEquals(0 until src.indexOf(" // )"), callRange(blank(src), 0))
        assertTrue(insideCall(src, "NoCopy", src.indexOf("\"x\"")))
        assertFalse(insideCall(src, "NoCopy", src.indexOf("\"y\"")))
    }

    @Test fun `TECLADO-1 NoCopy filters the new text menu and keeps the old toolbar`() {
        val src = main("ui/theme/Components.kt")
        val b = blank(src)
        val noCopy = callRange(b, b.indexOf("fun NoCopy("))
        val body = b.substring(noCopy.first, noCopy.last + 1)
        assertTrue("the new menu (Compose foundation 1.9+)", "filterTextContextMenuComponents(" in body)
        assertTrue("with the tested allow-list", "keptInNoCopyMenu" in code("ui/theme/Components.kt"))
        assertTrue("the old menu", "LocalTextToolbar provides" in body)
        assertTrue("F4 review M1: keyboard copy and cut", "onPreviewKeyEvent" in body && "isCopyOrCutKey(" in body)
    }

    @Test fun `TECLADO-2 the plain texts of Cifrar para and Firmar and every password field are inside NoCopy`() {
        val pk = main("ui/screens/PkActionsScreen.kt")
        for (label in listOf("\"Texto a cifrar\"", "\"Texto a firmar\"")) {
            val at = pk.indexOf(label)
            assertTrue("$label not found", at >= 0)
            assertTrue("$label outside NoCopy", insideCall(pk, "NoCopy", at))
        }
        // The master password (create, unlock, the contacts prompt) and every other PasswordField, also after «Ver».
        val comp = main("ui/theme/Components.kt")
        val field = comp.indexOf("OutlinedTextField(", comp.indexOf("fun PasswordField("))
        assertTrue(field > 0)
        assertTrue("PasswordField outside NoCopy", insideCall(comp, "NoCopy", field))
    }

    @Test fun `TECLADO-3 a copy found on reopening is cleared by its time, also when the app was closed before`() {
        val src = code("android/Clipboard.kt")
        assertTrue("ownCopyClearDelay(" in src)
        // F4 review M3: both timers use the same rule.
        assertEquals(2, Regex("""wipeOwnCopy\(""").findAll(src).count())
        // F4 re-review N1: the clip's text is read only inside the check, which runs only under our label.
        val clip = main("android/Clipboard.kt")
        val read = clip.indexOf("cm.primaryClip?.getItemAt")
        assertTrue(read > 0)
        assertTrue("the text read must be inside wipeOwnCopy's check", insideCall(clip, "wipeOwnCopy", read))
        assertEquals("one read of the clip's content", 1, Regex("""\.primaryClip\b""").findAll(code("android/Clipboard.kt").substringBefore("fun readClipboardText")).count())
        // F4 review M2: the process-wide timer must not hold the Activity.
        val b = blank(main("android/Clipboard.kt"))
        val stale = callRange(b, b.indexOf("fun clearStaleCopy("))
        val body = b.substring(stale.first, stale.last + 1)
        assertTrue("applicationContext.getSystemService(ClipboardManager" in body)
        assertFalse(Regex("""[^.]context\.getSystemService""").containsMatchIn(body))
        assertTrue("clearStaleCopy(this)" in code("MainActivity.kt"))
    }

    @Test fun `COV2-5 the clipboard texts come from ToolTexts and say this phone only`() {
        val tools = code("ui/screens/ToolsScreen.kt")
        assertTrue("clipboardAboutText(settings.clipboard.seconds)" in tools)
        assertFalse("lo que copias se borra del portapapeles a los" in tools)
        val settings = code("ui/screens/SettingsScreen.kt")
        assertTrue("Hint(CLIPBOARD_CLEAR_HINT)" in settings)
        assertFalse("Solo se borra si sigue" in settings)
    }

    @Test fun `CRY-B01 the app's client checks the chain Android validated, and fails closed without the server's name`() {
        assertTrue("trustedChainOf = { androidTrustedChain(it) }" in code("SeguridadApp.kt"))
        assertTrue("secureClient(appVersion, trustedChainOf)" in code("AppContainer.kt"))
        val src = code("android/TrustedChain.kt")
        assertTrue("X509TrustManagerExtensions(system)" in src)
        assertTrue("checkServerTrusted(chain, authType, host)" in src)
        assertTrue(Regex("""if \(host == null\) throw CertificateException\(""").containsMatchIn(src))
        // Review round 1, minor 1: a system trust manager it cannot take refuses every connection; the app does not close.
        assertTrue(Regex("""catch \(e: IllegalArgumentException\) \{\s*return \{ _, _, _ -> throw CertificateException\(""").containsMatchIn(src))
    }

    @Test fun `Importar de otra app holds what it read in a ViewModel, reads the file once and drops all on lock or back`() {
        val src = code("ui/screens/ImportScreen.kt")
        val flat = src.replace(Regex("""\s+"""), " ")
        // Nothing read, typed or planned reaches saved state; leaving the screen wipes it; a wipe stops what runs.
        assertEquals(0, Regex("""rememberSaveable""").findAll(src).count())
        assertTrue("override fun onCleared()" in src && "session.wipe()" in src)
        assertTrue("val session: ImportSession = ImportSession(ComposeCells, SystemClock.elapsedRealtime()) { stopAll() }" in src)
        // The guard of the other tools, plus any lock of the vault (not only the «PIN y códigos» rule).
        assertTrue("watchAway(session.guard, store, lockAfterMs, awayInOtherApp)" in src && "GuardEffects(s.guard, lockAfterMs)" in src)
        assertTrue("store.state.collect { session.vaultStateIs(it is VaultState.Unlocked) }" in flat)
        assertTrue("session.guard.typed()" in src && "Text(IMPORT_WIPE_NOTE" in src)
        assertTrue("usedFor(s.guard)" in flat && "touchesVault(store)" in src && "VaultIdleLock(store)" in src)
        // Owner's decision (a): at most one byte past the limit, on IO, cancellable; (b): the document is opened once.
        assertTrue(".readAtMost(IMPORT_MAX_BYTES + 1)" in flat && "withContext(Dispatchers.IO)" in src)
        assertEquals(1, Regex("""openInputStream\(""").findAll(src).count())
        assertTrue("StopOnCancel(raw) { reading.ensureActive() }" in src && "check()" in src)
        // R11b: the reader off the main thread, and OutOfMemoryError caught around that one call only.
        assertEquals(1, Regex("""\breadImport\(""").findAll(src).count())
        assertEquals(1, Regex("""OutOfMemoryError""").findAll(src).count())
        assertTrue(flat, "val read = { try { readImport(bytes, pw) } catch (e: OutOfMemoryError) { null } }" in flat)
        // R38: with a password the read may derive a key, one large derivation at a time in the app (KdfLock).
        assertTrue(flat, "if (pw == null) withContext(Dispatchers.Default) { read() } else KdfLock.derive(Dispatchers.Default, read)" in flat)
        // The password is a CharArray zeroed however the read ends; one KDF at a time (SingleRun), released when it ends.
        assertTrue("pw?.fill('\\u0000')" in src && "IMPORT_KDF_RUN.start()" in src && "ticket?.release()" in src)
        assertTrue("bytes?.fill(0)" in code("imports/ImportSession.kt") || "it.fill(0)" in code("imports/ImportSession.kt"))
        // The plan only from one snapshot (entries and revision read together, as planImport does, kept for the
        // estimate); the confirmation only through ImportFlow, which a back does not cut (a lock before the save
        // cancels it, ruling R37: VaultStoreImportTest and AutoBackupImportCopyTest).
        assertTrue("val snap = store.importSnapshot()" in src && "planMerge(read, it.entries, it.revision)" in src)
        assertEquals(1, Regex("""planMerge\(""").findAll(src).count())
        assertEquals("the revision only from the snapshot", 1, Regex("""\.revision\b""").findAll(src).count())
        assertTrue("ImportFlow(" in src && "store.applyImport(plan, default, overrides, sinceLock = since)" in src && "withContext(NonCancellable)" in src)
        // Ruling R66: both saves (confirm and copyTo) record the lock epoch when saving starts and pass it on.
        assertEquals(2, Regex("""val since = store\.lockEpoch""").findAll(src).count())
        assertEquals(2, Regex("""applyImport\(plan, default, overrides, sinceLock = since\)""").findAll(src).count())
        assertTrue("copyBeforeImport()" in src)
        // Both pickers as «Importar copia»: the vault stays open while one is on top; the flag goes back if none opened.
        assertTrue(flat, ("VaultLock.keepOpenOnce = true store.touch() pickerError = pick.launchOrError(arrayOf(\"*/*\")) " +
            "if (pickerError != null) VaultLock.keepOpenOnce = false") in flat)
        assertTrue(flat, ("VaultLock.keepOpenOnce = true store.touch() pickerError = createCopy.launchOrError(importCopyFileName(LocalDateTime.now())) " +
            "if (pickerError != null) VaultLock.keepOpenOnce = false") in flat)
        // M-3: both pickers are registered before the locked return (a result after process death still arrives, and a
        // created document is then deleted); a file picked while locked is not read.
        val screen = src.substringAfter("fun ImportScreen(")
        val lockedReturn = screen.indexOf("if (state !is VaultState.Unlocked) {")
        assertTrue(lockedReturn > 0)
        for (picker in listOf("val pick = rememberDocumentPicker(", "val createCopy = rememberDocumentPicker(")) {
            assertTrue(picker, screen.indexOf(picker) in 0 until lockedReturn)
        }
        assertTrue("if (uri != null && store.state.value is VaultState.Unlocked) vm.pick(uri)" in src)
        // Round 2, minor 1: the estimate's fallback claims nothing about the tags.
        assertTrue("PreviewEstimate(0L, null, 0)" in src && "emptySet()" !in src)
        // M-5: «Elegir otro archivo» on the password step opens the picker, as on the other steps.
        assertTrue(flat, "OutlinedButton(onClick = { vm.cancel() launchPick() }" in flat)
        // R18: the vault's tag chips are lazy.
        assertTrue("LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(allTags, key = { it })" in code("ui/screens/VaultScreen.kt").replace(Regex("""\s+"""), " "))
        // A document created for the copy and not used (refused, failed) is deleted, never left empty.
        assertTrue("discardDocumentNow(appContext, uri)" in src)
        // «Quitar el archivo exportado»: the document picked in this run, once, after an import; the provider asked first.
        assertEquals(1, Regex("""DocumentsContract\.deleteDocument\(""").findAll(src).count())
        assertTrue("FLAG_SUPPORTS_DELETE" in src && "DocumentsContract.isDocumentUri(" in src && "session.takeRemovable()" in src)
        // Owner's decision (c): what comes from the file is plain text; never a link, an intent, HTML or an annotation.
        for (file in listOf(src, code("imports/ImportSession.kt"))) {
            for (w in listOf("openUri", "UriHandler", "Linkify", "LinkAnnotation", "ACTION_VIEW", "fromHtml", "AnnotatedString", "ClickableText", "startActivity", "Log.")) {
                assertFalse(w, w in file)
            }
        }
        assertTrue(Regex("""plainReportText\(""").findAll(src).count() >= 5)
        // The entry in the vault's menu, next to «Importar copia», and its route.
        val vault = code("ui/screens/VaultScreen.kt")
        assertTrue(vault.indexOf("Text(IMPORT_TITLE)") > vault.indexOf("Text(\"Importar copia\")"))
        assertTrue("composable(\"vault/import\")" in code("ui/AppNavHost.kt"))
        assertTrue(
            "ImportScreen(vault, container.autoBackup::copyBeforeImport, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))" in
                code("ui/AppNavHost.kt"),
        )
    }

    /**
     * Ruling R9: a title, user, URL, service, account or tag is stored as it was read or typed, and SHOWN through
     * plainReportText (controls become spaces, bidi and invisible characters go) wherever it appears: the lists, the
     * entry's header and fields, the dialogs that name it, the 2FA QR, «Salud» and the contacts. Editing fields keep
     * the stored text.
     */
    @Test fun `R9 every title, user, URL, service, account and tag shown goes through plainReportText`() {
        val pins = mapOf(
            "ui/screens/VaultScreen.kt" to listOf(
                "Text(plainReportText(e.title), maxLines = 1",
                "if (sub.isNotBlank()) Text(plainReportText(sub)",
                "label = { Text(\"#\${plainReportText(t)}\") }",
            ),
            "ui/screens/VaultEntryScreen.kt" to listOf(
                "BackScaffold(\"\${typeIcon(entry.type)} \${plainReportText(entry.title)}\", onBack)",
                "Field(\"Usuario\", entry.username, plain = true)",
                "Field(\"Web\", entry.url, plain = true)",
                "if (plain) plainReportText(value, value.length) else value",
                "entry.tags.map { \"#\${plainReportText(it)}\" }",
                "Text(\"¿Borrar «\${plainReportText(entry.title)}»?\")",
                "\${plainReportText(entries.single().title)}",
                "entries.joinToString(\", \") { plainReportText(it.title) }",
            ),
            "ui/screens/OtpQrScreen.kt" to listOf("Text(plainReportText(listOf(totp.issuer, totp.account).filter { it.isNotBlank() }.joinToString(\" · \"))"),
            "ui/screens/HealthScreen.kt" to listOf("Text(plainReportText(title).ifEmpty { \"(sin título)\" }"),
            "ui/screens/ContactsScreen.kt" to listOf("Text(plainReportText(shownContactTitle(e.title)), maxLines = 1"),
            "ui/screens/ContactDetailScreen.kt" to listOf("Text(plainReportText(shownContactTitle(entry.title)), style"),
            "ui/screens/PkActionsScreen.kt" to listOf("Text(plainReportText(shownContactTitle(c.title)), maxLines = 1"),
            // M-6: the texts built in vault/ that name a stored contact.
            "vault/ContactsView.kt" to listOf(
                "Ya lo tienes: \${plainReportText(shownContactTitle(check.entry.title))}",
                "misma clave de cifrado que \${plainReportText(shownContactTitle(check.entry.title))}",
                "tu contacto «\${plainReportText(check.entry.title)}»",
            ),
            "vault/PkActions.kt" to listOf(
                "senderContactText(sender.entry.verified, addressedToMe, plainReportText(sender.entry.title))",
                "fileSignatureUnverifiedText(plainReportText(sender.entry.title))",
            ),
        )
        for ((file, snippets) in pins) {
            val src = code(file)
            for (s in snippets) assertTrue("$file: $s", s in src)
        }
        // The TOTP header of the entry, twice (a code shown, a code this app does not calculate).
        assertEquals(2, Regex(Regex.escape("Text(plainReportText(listOf(t.issuer, t.account).filter { it.isNotBlank() }.joinToString(\" · \"))")).findAll(code("ui/screens/VaultEntryScreen.kt")).count())
        // Nowhere in the UI a stored title, user or URL, or a 2FA's names, straight into a Text or a title.
        val raw = Regex("""(Text\(|BackScaffold\()[^\n]*?\b(e|entry|it|item|existing)\.(title|username|url)\b|Text\(listOf\(\w+\.issuer""")
        val bad = mainDir().resolve("ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            code("ui/" + f.relativeTo(mainDir().resolve("ui")).invariantSeparatorsPath).lines().filter { raw.containsMatchIn(it) && "plainReportText(" !in it }.map { "${f.name}: ${it.trim()}" }
        }.toList()
        assertEquals(emptyList<String>(), bad)
    }

    /** The arguments of each call to [name] in [src], up to its closing parenthesis (strings without parentheses). */
    private fun calls(src: String, name: String): List<String> {
        val out = ArrayList<String>()
        var i = src.indexOf("$name(")
        while (i >= 0) {
            var depth = 0
            var j = i + name.length
            while (j < src.length) {
                when (src[j]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) break
                }
                j++
            }
            out += src.substring(i, j + 1)
            i = src.indexOf("$name(", j)
        }
        return out
    }

    /**
     * Branch review B, I-1 (MASVS-STORAGE-2): every field of the «Laboratorio de hashes» and the «Laboratorio clásico»
     * can hold a secret (their sessions wipe them by the «PIN y códigos» rule, and their texts say nothing is kept on
     * the phone), so each uses the keyboard that neither suggests nor learns, as «Cifrar texto» and «Repartir un
     * secreto»; the HMAC key is a [app.seguridad.ui.theme.PasswordField], hidden too.
     */
    @Test fun `I-1 the labs and the other secret fields use the keyboard that does not learn`() {
        assertTrue(
            "val SECRET_TEXT_KEYBOARD = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)" in
                code("ui/theme/Components.kt"),
        )
        for ((file, fields) in listOf("ui/screens/HashLabScreen.kt" to 4, "ui/screens/ClassicLabScreen.kt" to 6)) {
            val found = calls(code(file), "OutlinedTextField")
            assertEquals(file, fields, found.size)
            for (f in found) {
                assertTrue("$file: $f", "keyboardOptions = SECRET_TEXT_KEYBOARD," in f)
                // Shown as typed: the keyboard type alone masks nothing, and nothing else is changed.
                assertFalse("$file: $f", "visualTransformation" in f)
            }
        }
        assertEquals(1, Regex("""PasswordField\(\s*s\.hmacKey,""").findAll(code("ui/screens/HashLabScreen.kt")).count())
        assertEquals(2, Regex("""keyboardOptions = SECRET_TEXT_KEYBOARD,""").findAll(code("ui/screens/SecretShareScreen.kt")).count())
        assertTrue("keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)," in code("ui/screens/SecurityToolsScreen.kt"))
    }
}
