# Re-revisión 1 de la tarea 2 (4056f34..f6b98b7)

### Finding Verdicts

- **I-1 ADDRESSED.** The A11 mark is per vault (`Settings.kt:65` `VaultSettings.clockHighWaterMs`; `AppContainer.kt` `clockSeen`/`clockLowered` per id, `markOf`). `rememberClock` raises every vault's mark (`:116`); `resetClock(id, now)` lowers only its own (`:128`); each store gets `clockHighWater = { clockMark(id) }` and `onClockReset = { resetClock(id, it) }` (`:361-363`). `VaultStore.kt:534` gates the reset in `unlockLocked` with `active()`. The 1.4.3 mark goes to `principal` (`PerVaultSettingsTest` reads it in `v`). A new vault starts at the highest mark of any other vault (`startClock`, `newVault`). Test `masterOfOneDoesNotHealTheClockOfAnother` (AppContainerVaultsTest); the report gives the RED and the GREEN.
- **I-2 ADDRESSED.** `importAsNewVault` checks the copy and its password in the new store (`before`: `load` + `importReplace`) while the previous vault stays active and open. `opened` keeps the inactive store locked and zeroes the key (`VaultStore.kt:453-459`), so nothing of the copy's key or master is retained. `made` then switches and unlocks. Results and busy state live in `AppContainer` (`vaultsNotice`, `runVaultsAction`, `launchVaultsAction`, `AppContainer.kt:419-462`) and `VaultNav` shows them after the switch (`AppNavHost.kt:722`). The notice holds only fixed texts. Test `importAsNewWithAWrongPasswordStaysWhereItWas`.
- **M-1 ADDRESSED.** `PickerDialog.DeleteEmpty` and `emptySlot` (`VaultPickerScreen.kt`), with `VAULT_DELETE_EMPTY_WARNING` on view.
- **M-2 ADDRESSED.** `isEmptySlot` (`AppContainer.kt:414`): no `vault.bin`, `vault.bin.bad-` or `att.bad-`. `deleteVault` returns false otherwise (`:376`). Test `anEmptySlotGoesWithoutMasterButASetAsideFileStays`.
- **M-3 ADDRESSED.** `newVault` and `deleteVault` use `slot(id).store`; `autoBackup` reads `_activeVault.value.id` (`:250`).
- **M-4 ADDRESSED.** `lateWriteNeverLandsInAnotherVault` is rewritten with a latch on the compute dispatcher and `a.state.first { Locked }`. It has no `Thread.sleep`; the gate is released in a `finally`; the report gives a mutation that makes it fail. The "switch waits" check is a 1 s negative timeout, which is acceptable.
- **M-5 ADDRESSED.** `listed(id)` guards `recordAutoBackup` and `recordImportCopy` (`:731`, `:746`). Test `aLateCopyResultOfADeletedVaultIsDropped`.
- **M-6 ADDRESSED.** `VaultScopes` keeps one store per id; `VaultScoped` clears it in `DisposableEffect.onDispose`, only if the vault is no longer the active one (`AppNavHost.kt:141`).

### New Breakage in the Fix Diff

None Critical or Important. Checked against the named risks:
- The mark only rises from times seen, and only drops through its own vault's `resetClock` (`unlockLocked`, `confirmMaster`, the imports). `dropVault` and `deleteVault` call `forgetClock`, and `saveClock` writes only the vaults in the list.
- The migration of the 1.4.3 mark and a new vault's starting mark are covered above.
- Import-as-new pre-check: the key is zeroed on the inactive store; the master stays with the caller, who zeroes it in its `finally`.

Minor (info): `launchVaultsAction` checks `_vaultsBusy` and sets it inside the launched coroutine, so two taps in the same frame could both launch (`AppContainer.kt:459`). The buttons are disabled with `busy`, which makes the race unlikely.

### Out-of-Scope Observations

- `deleteVault` with a single vault returns false and the empty-slot dialog would show `VAULT_DELETE_DAMAGED` (misleading), but the UI only offers it when `vaults.size > 1`.
- M-3 and M-6 have no test of their own (stated in the report).

### Verdict — **Fix round:** All findings addressed, no new Critical/Important breakage
