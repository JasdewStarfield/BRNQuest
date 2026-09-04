// First load installs type A and its book; the second load intentionally fails after staging type B.
const previousTypes = BRNQuest.getScriptExtensions().taskTypeIds
const secondLoad = previousTypes.join(',') === 'kubejs_reload_smoke:type_a'

if (secondLoad) {
    BRNQuest.registerTaskType('kubejs_reload_smoke:type_b')
    throw new Error('[BRNQuest/KUBEJS_RELOAD_SMOKE] intentional reload failure')
}

BRNQuest.registerTaskType('kubejs_reload_smoke:type_a')

ServerEvents.loaded(event => {
    const book = BRNQuest.getActiveBook()
    const types = BRNQuest.getScriptExtensions()
    if (book === null || book.id !== 'kubejs_reload_smoke:book'
            || types.taskTypeIds.join(',') !== 'kubejs_reload_smoke:type_a') {
        throw new Error(`Initial script/book pair was not committed: book=${book}, types=${types}`)
    }

    console.info('[BRNQuest/KUBEJS_RELOAD_SMOKE] initial-pair-ready')
    // Minecraft owns both schedules, so unloading the first Rhino context cannot cancel them.
    event.server.runCommandSilent('schedule function kubejs_reload_smoke:reload 1s replace')
    event.server.runCommandSilent('schedule function kubejs_reload_smoke:stop 8s replace')
})
