// This server-only fixture proves bindings, events, and atomic script type registration through Rhino.
BRNQuest.registerTaskType('kubejs_smoke:external_progress')
BRNQuest.registerRewardType('kubejs_smoke:script_reward')

BRNQuestEvents.questCompleted(event => {})
BRNQuestEvents.taskProgressChanged(event => {})
BRNQuestEvents.rewardClaimed(event => {})
BRNQuestEvents.customReward('kubejs_smoke:script_reward', event => {
    if (!event.idempotencyKey) throw new Error('Script reward must provide an idempotency key')
})

const invalid = BRNQuest.completeQuest(null, 'brnquest:missing')

if (invalid.code !== 'INVALID_PLAYER' || invalid.success !== false || invalid.changed !== false) {
    throw new Error(`Unexpected BRNQuest result projection: ${invalid}`)
}

if (BRNQuest.getQuest('not a resource location') !== null) {
    throw new Error('Malformed BRNQuest query must return null')
}

// End the isolated smoke server cleanly once its normal server lifecycle is ready.
ServerEvents.loaded(event => {
    const extensions = BRNQuest.getScriptExtensions()
    if (extensions.registrationOpen !== false
            || extensions.taskTypeIds.join(',') !== 'kubejs_smoke:external_progress'
            || extensions.rewardTypeIds.join(',') !== 'kubejs_smoke:script_reward') {
        throw new Error(`Unexpected script extension snapshot: ${extensions}`)
    }

    console.info('[BRNQuest/KUBEJS_SMOKE] events-and-extensions-ready')
    event.server.runCommandSilent('stop')
})
