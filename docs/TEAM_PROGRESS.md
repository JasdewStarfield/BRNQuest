# Team quest progress

Book settings now include **Share team progress**, enabled by default (`settings.share_team_progress`). Disable it to keep separate personal quest and reward histories while remaining in the same OPAC party.

Quest behavior includes **Require every team member**, disabled by default (`behavior.require_all_team_members`). In shared mode, each member must complete every required objective individually, including their own item submissions. The UI shows “Your objectives are complete; waiting for teammates” until everyone finishes. Rewards and completed-prerequisite unlocks wait for the whole team. Other quests continue sharing counters. In personal mode or without a party, the quest behaves as an ordinary personal quest.

Until completion, the current OPAC roster applies, including offline members. Joining members must finish their own objectives; departing members stop blocking completion. A completed quest stays completed after later joins, and new members cannot claim rewards from that old cycle. Existing repeatable-quest rules still recognize previously completed prerequisite cycles.

Personal and team histories are retained separately. Switching the book policy selects a history without copying, merging or clearing it. Individual objective records belong to that team: rejoining the same team restores the current cycle's records, while joining another team does not transfer them. Enabling the rule on an already completed quest preserves completion history; reset the quest if it should be repeated.

A full quest reset clears every member's objectives and reward receipts for that quest. Resetting one objective clears it for every member and requires personal completion again while preserving reward receipts. A new repeat cycle clears all member objectives. Administrators can force-complete an entire quest to bypass the rule; forcing a single objective advances only the selected player's counter.

Apply or publish editor changes through the existing workflow. Update both client and server to network protocol 19.
