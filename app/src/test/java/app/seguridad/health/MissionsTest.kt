package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.crypto.TotpSecret
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.backupCheckNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0, tarea 10 (fase B): «Salud» como misiones, de una en una, sin presión (diseño; Abogado Digital, R-2 y O-6). */
class MissionsTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun login(id: String, title: String, pw: String, totp: Boolean = false, changed: Long = now) = VaultEntry(
        id, EntryType.LOGIN, title, username = "ana@example.com", password = pw, createdAt = changed, updatedAt = changed,
        passwordChangedAt = changed, totp = if (totp) TotpSecret("JBSWY3DPEHPK3PXP") else null,
    )

    private fun of(vararg e: VaultEntry, checked: Long = now) = VaultData(entries = e.toList(), lastCheckedBackupAt = checked)

    private fun all(data: VaultData) = missions(vaultHealth(data.live, now), data, now)

    private val strong = "caballo-grapa-bateria-correcto-77"

    @Test fun oneAtATime() {
        val data = of(
            login("a", "Correo", "123456"),
            login("b", "Banco", "123456"),
            login("c", "Foro", strong + "x", changed = now - 400 * day),
            login("d", "Tienda", strong + "y", totp = true),
            checked = 0L,
        )
        val list = all(data)
        assertEquals(
            "Repeated, weak, no 2FA, old, the copy: each once",
            listOf(MISSION_REUSED, MISSION_WEAK, MISSION_NO_2FA, MISSION_OLD, MISSION_BACKUP),
            list.map { it.id },
        )
        assertEquals("Cambia 2 contraseñas repetidas", list[0].title)
        assertEquals("Cambia 2 contraseñas débiles", list[1].title)
        assertEquals("Activa el 2FA en «Correo»", list[2].title)
        assertEquals("Cambia 1 contraseña antigua", list[3].title)
        assertEquals("Comprueba una copia", list[4].title)
        // One on screen: the first one not put off.
        assertEquals(MISSION_REUSED, nextMission(list, emptySet(), off = false)?.id)
        assertEquals(MISSION_NO_2FA, nextMission(list, setOf(MISSION_REUSED, MISSION_WEAK), off = false)?.id)
        assertNull(nextMission(list, list.map { it.id }.toSet(), off = false))
        // Each one only navigates: to the entry to change, or to «Comprobar una copia».
        assertEquals(MissionAction.EditEntry("a"), list[0].action)
        assertEquals(MissionAction.EditEntry("a"), list[2].action)
        assertEquals(MissionAction.EditEntry("c"), list[3].action)
        assertEquals(MissionAction.CheckBackup, list[4].action)
    }

    @Test fun nothingToDoIsNoMission() {
        assertTrue(all(of(login("a", "Correo", strong, totp = true))).isEmpty())
    }

    /** The copy's mission reads what [backupCheckNotice] reads (the same function, the same date). */
    @Test fun theCopysMissionIsTheNoticesRule() {
        for (checked in listOf(0L, now - 31 * day, now - 29 * day, now + 2 * day, now)) {
            val list = all(of(login("a", "Correo", strong, totp = true), checked = checked))
            assertEquals("checked $checked", backupCheckNotice(checked, now) != null, list.any { it.id == MISSION_BACKUP })
        }
    }

    /** O-6: no mission asks for the leak check (only its two buttons connect), and the 2FA one names only the title. */
    @Test fun noMissionGoesOnline() {
        val data = of(login("a", "Correo <b>‮evil", "123456"), login("b", "Banco", "123456"), checked = 0L)
        val list = missions(vaultHealth(data.live, now, breached = mapOf("a" to 3)), data, now)
        assertTrue(list.none { "filtr" in it.title.lowercase() })
        assertTrue(list.all { it.action is MissionAction.EditEntry || it.action == MissionAction.CheckBackup })
        // Shown as plain text: no direction marks nor controls from the vault.
        val twoFa = list.single { it.id == MISSION_NO_2FA }.title
        assertFalse(twoFa.any { it == '‮' })
    }

    /** No streaks, points, countdowns or pressure: whole words, so «ya» in «haya» is fine, and no «!» nor «¡». */
    @Test fun noPressureWords() {
        val many = listOf(
            of(login("a", "Ya", "123456"), login("b", "Banco", "123456"), checked = 0L),
            of(login("a", "Correo", "abc", changed = now - 900 * day), checked = now - 40 * day),
            of(login("a", "Correo", strong)),
        )
        val texts = many.flatMap { d -> all(d).map { it.title } }.filter { "«" !in it } +
            listOf(MISSION_GO, MISSION_LATER, MISSIONS_SHORT, MISSIONS_SWITCH, MISSIONS_LABEL)
        assertTrue(texts.isNotEmpty())
        for (t in texts) assertEquals(t, emptyList<String>(), pressureWords(t))
        // The check itself: whole words, and the marks anywhere.
        assertEquals(emptyList<String>(), pressureWords("Puede que haya una copia"))
        assertEquals(listOf("ya"), pressureWords("Hazlo ya"))
        assertEquals(listOf("!"), pressureWords("Bien!"))
        assertEquals(listOf("¡"), pressureWords("¡Bien"))
        assertEquals(listOf("racha"), pressureWords("Tu racha"))
        assertEquals(listOf("puntos"), pressureWords("10 puntos"))
        assertEquals(listOf("urgente"), pressureWords("Es URGENTE"))
    }

    /**
     * «Ahora no» hides that mission until the next unlock of the same vault, in memory: another vault does not see it, and
     * a lock (a new epoch) ends it.
     */
    @Test fun postponedUntilNextUnlock() {
        val p = MissionPostponements()
        p.postpone("principal", epoch = 3, missionId = MISSION_REUSED)
        assertEquals(setOf(MISSION_REUSED), p.postponed("principal", epoch = 3))
        assertEquals("Another vault", emptySet<String>(), p.postponed("0123456789abcdef0123456789abcdef", epoch = 3))
        assertEquals("After a lock and an unlock", emptySet<String>(), p.postponed("principal", epoch = 4))
        // And once the epoch moved on, the old «Ahora no» is gone for good, also if the number came back.
        assertEquals(emptySet<String>(), p.postponed("principal", epoch = 3))
        p.postpone("principal", epoch = 5, missionId = MISSION_REUSED)
        p.postpone("principal", epoch = 5, missionId = MISSION_WEAK)
        assertEquals(setOf(MISSION_REUSED, MISSION_WEAK), p.postponed("principal", epoch = 5))
    }

    /** Each action's destination is a route of the navigation (with simple mode on too: SimpleModeTest). */
    @Test fun actionsHaveRoutes() {
        assertEquals("vault/edit", MissionAction.EditEntry("x").routeBase)
        assertEquals("settings/check-backup", MissionAction.CheckBackup.routeBase)
    }

    @Test fun theWordsWordForWord() {
        assertEquals("Ir", MISSION_GO)
        assertEquals("Ahora no", MISSION_LATER)
        assertEquals("Sugerencias de «Salud»", MISSIONS_SWITCH)
        assertEquals("Enseñar sugerencias", MISSIONS_LABEL)
        assertEquals("Una sugerencia cada vez en «Salud de la bóveda», con «Ahora no». Ninguna se conecta a internet.", MISSIONS_SHORT)
        assertEquals("Aún no tienes bóveda. Créala en la pestaña Bóveda.", app.seguridad.tools.HEALTH_NO_VAULT)
        for (t in listOf(MISSION_GO, MISSION_LATER, MISSIONS_SWITCH, MISSIONS_LABEL, MISSIONS_SHORT, app.seguridad.tools.HEALTH_NO_VAULT)) {
            assertEquals(t, emptyList<String>(), app.seguridad.tools.ForbiddenWords.problems(t))
        }
    }

    @Test fun anUntitledLoginHasNoEmptyQuotes() {
        val data = of(login("a", "  ", "123456"))
        assertEquals("Activa el 2FA en un acceso", all(data).single { it.id == MISSION_NO_2FA }.title)
    }

    /**
     * Ruling T10: a mission's title may hold an entry's title (data of the vault). It lives only in the open vault's
     * branch of «Salud», computed off the main thread there, so a lock (which takes the branch away) cancels it and drops
     * it; what outlives the vault keeps ids only ([MissionPostponements]).
     */
    @Test fun titlesLiveOnlyWithTheOpenVault() {
        val src = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/ui/screens/HealthScreen.kt") }.first { it.isFile }.readText()
        val gate = src.indexOf("return@BackScaffold")
        val computed = src.indexOf("missions(vaultHealth(")
        assertTrue(gate in 0 until computed)
        assertTrue("Off the main thread", Regex("""withContext\(Dispatchers\.Default\) \{\s*val now = System\.currentTimeMillis\(\)\s*missions\(""").containsMatchIn(src))
        assertFalse("Never in saved state", "rememberSaveable" in src)
        val holder = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/health/Missions.kt") }.first { it.isFile }.readText()
        assertTrue("Only ids are kept", "HashMap<String, Pair<Int, Set<String>>>" in holder)
    }

    /** Ronda 1 (m-6, R-2): with the switch off, no mission is shown, whatever there is to do. */
    @Test fun theSwitchTurnsThemAllOff() {
        val list = all(of(login("a", "Correo", "123456"), login("b", "Banco", "123456"), checked = 0L))
        assertTrue(list.isNotEmpty())
        assertEquals(MISSION_REUSED, nextMission(list, emptySet(), off = false)?.id)
        assertNull(nextMission(list, emptySet(), off = true))
        assertNull(nextMission(list, setOf(MISSION_WEAK), off = true))
    }

    /**
     * Ronda 1 (m-5): every fixed text and every template of the missions, with a neutral title, singular and plural, and
     * the texts of «Salud» and Ajustes: no pressure.
     */
    @Test fun everyTextOfTheMissionsHasNoPressure() {
        val titles = listOf(1, 2, 7).flatMap { n ->
            listOf("repetida" to "repetidas", "débil" to "débiles", "antigua" to "antiguas").map { (one, many) -> if (n == 1) "Cambia 1 contraseña $one" else "Cambia $n contraseñas $many" }
        }
        val produced = listOf(1, 2).flatMap { n ->
            val weakOld = (1..n).map { login("w$it", "X", "abc$it", changed = now - 900 * day) }
            all(of(*weakOld.toTypedArray(), login("r1", "X", "mismo-1234"), login("r2", "X", "mismo-1234"), checked = 0L)).map { it.title }
        }
        val untitled = all(of(login("a", "", "caballo-grapa-bateria-correcto-77"))).map { it.title }
        val texts = titles + produced + untitled + listOf(
            "Activa el 2FA en «X»", "Activa el 2FA en un acceso", "Comprueba una copia",
            MISSION_GO, MISSION_LATER, MISSIONS_SWITCH, MISSIONS_LABEL, MISSIONS_SHORT, app.seguridad.tools.HEALTH_NO_VAULT,
        )
        assertTrue(produced.any { it.startsWith("Cambia 1 contraseña") } && produced.any { it.startsWith("Cambia 2 contraseñas") })
        assertTrue("Activa el 2FA en «X»" in produced)
        assertTrue("Activa el 2FA en un acceso" in untitled)
        for (t in texts) {
            assertEquals(t, emptyList<String>(), pressureWords(t))
            assertEquals(t, emptyList<String>(), app.seguridad.tools.ForbiddenWords.problems(t))
        }
    }

    /**
     * Ronda 1 (i-1, truth): «Ir» takes to «Comprobar una copia», which checks a copy and makes none; the title says that,
     * as the notice of the same date does ([backupCheckNotice]), and not the design's example «Haz y comprueba…».
     */
    @Test fun theCopysTitleSaysWhatItsScreenDoes() {
        val m = all(of(login("a", "Correo", strong, totp = true), checked = 0L)).single()
        assertEquals("Comprueba una copia", m.title)
        assertEquals(MissionAction.CheckBackup, m.action)
        assertFalse("Haz" in m.title)
    }
}
