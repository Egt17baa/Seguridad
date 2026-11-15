package app.seguridad.settings

/*
 * 2.0, task 10 (phase B): what the app shows by itself, one at a time ([openingItem]). Everything that can come up
 * without being asked for, from the code:
 * - «Antes de empezar» (NoticeScreen, until accepted; the guide's «Saltar» never skips it, O-7);
 * - the choice before passing to format v4 and, after it, the line about the copy made (VaultUpgrade.kt, O-3);
 * - the dialog after a change of master password (MasterChangedDialog, A6);
 * - what an action of «Bóvedas» said (it switched vaults, so it waits in AppContainer);
 * - how a replacement of the old copies asked for while locked went (ReplaceOutcomeDialog, A6);
 * - the notice of the new name (RenameNotice) and the line of the new look (AppearanceNotice, R-C8);
 * - the guide: the whole one or what is new ([GuideHolder]).
 * Others are not shown at the opening but answer something the user did (the clipboard notice A14, a snackbar, the
 * offer of «Copiar contraseña» on return), and stay where they are. The app receives no file nor text from another app
 * (no ACTION_SEND filter): the only deliveries are its own (a QR handed to a vault screen, a picker on its way back).
 */

/** The vault shown, as the opening needs it. */
enum class VaultPhase { LOADING, NONE, LOCKED, OPEN }

/** What can be shown by itself, in the order it is shown. */
enum class OpeningItem { NOTICE, UPGRADE, UPGRADE_DONE, MASTER_CHANGED, VAULTS_NOTICE, A6_OUTCOME, RENAME, APPEARANCE, GUIDE }

/**
 * What is due now: [noticeAccepted] («Antes de empezar»), the [vault] shown, the upgrade's choice and its line
 * ([upgradePending], [upgradeDone]), the dialogs waiting ([masterChangedOpen], [vaultsNotice], [a6Outcome]), the two
 * notices once shown ([renameDue], [appearanceDue]), the guide due or asked for in Ajustes ([guide]), whether the screen
 * is a tab ([onTabRoot]) and whether something of the app is on its way to a screen ([deliveryPending]: a QR handed over,
 * a picker or the scanner open).
 */
data class OpeningState(
    val noticeAccepted: Boolean,
    val vault: VaultPhase,
    val upgradePending: Boolean,
    val upgradeDone: Boolean,
    val masterChangedOpen: Boolean,
    val vaultsNotice: Boolean,
    val a6Outcome: Boolean,
    val renameDue: Boolean,
    val appearanceDue: Boolean,
    val guide: GuideKind?,
    val onTabRoot: Boolean,
    val deliveryPending: Boolean,
)

/**
 * Ronda 1 (m-1): a guide asked for from Ajustes is kept only while it can be shown there: on the tab of Ajustes
 * ([onTab], the route of the tab on screen; null on a screen that is not a tab), with the vault open or with none.
 * Anywhere else it is forgotten, so it never comes up later by itself, over the gate or out of a tab.
 */
fun guideRequestKept(onTab: String?, vault: VaultPhase): Boolean =
    onTab == "settings" && (vault == VaultPhase.OPEN || vault == VaultPhase.NONE)

/**
 * The one thing to show now, or null. In the order of [OpeningItem]: «Antes de empezar» before anything; the upgrade
 * and its copy before every other dialog (the guide never delays the copy before v4); the notices of the name and the
 * look only with the vault open or with none (never over the gate, where they would cover the password field or sit
 * under the fingerprint); the guide last, only then too, on a tab and with nothing of the app on its way.
 */
fun openingItem(s: OpeningState): OpeningItem? {
    if (!s.noticeAccepted) return OpeningItem.NOTICE
    val open = s.vault == VaultPhase.OPEN
    val calm = open || s.vault == VaultPhase.NONE
    return when {
        s.upgradePending && open -> OpeningItem.UPGRADE
        s.upgradeDone && open -> OpeningItem.UPGRADE_DONE
        s.masterChangedOpen -> OpeningItem.MASTER_CHANGED
        s.vaultsNotice -> OpeningItem.VAULTS_NOTICE
        s.a6Outcome -> OpeningItem.A6_OUTCOME
        s.renameDue && calm -> OpeningItem.RENAME
        s.appearanceDue && calm -> OpeningItem.APPEARANCE
        s.guide != null && calm && s.onTabRoot && !s.deliveryPending -> OpeningItem.GUIDE
        else -> null
    }
}
