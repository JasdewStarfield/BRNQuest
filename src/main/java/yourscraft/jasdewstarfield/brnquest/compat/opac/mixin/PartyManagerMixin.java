package yourscraft.jasdewstarfield.brnquest.compat.opac.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yourscraft.jasdewstarfield.brnquest.owner.OwnerInvalidation;

/** Notifications only: mutation callbacks can observe intermediate state, so never resolve here. */
@Pseudo
@Mixin(targets = "xaero.pac.common.server.parties.party.PartyManager", remap = false)
public abstract class PartyManagerMixin {
    @Inject(method = {
            "addParty(Lxaero/pac/common/server/parties/party/ServerParty;)V",
            "removeTypedParty(Lxaero/pac/common/server/parties/party/ServerParty;)V",
            "onMemberAdded(Lxaero/pac/common/server/parties/party/ServerParty;Lxaero/pac/common/parties/party/member/PartyMember;)V",
            "onMemberRemoved(Lxaero/pac/common/server/parties/party/ServerParty;Lxaero/pac/common/parties/party/member/PartyMember;)V",
            "onOwnerChange(Lxaero/pac/common/parties/party/member/PartyMember;Lxaero/pac/common/parties/party/member/PartyMember;)V"
    }, at = @At("RETURN"), require = 0, remap = false)
    private void brnquest$invalidate(CallbackInfo callback) { OwnerInvalidation.invalidate(); }
}
