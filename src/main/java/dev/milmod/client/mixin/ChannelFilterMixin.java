package dev.milmod.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import dev.milmod.client.ConcussionAudio;
import dev.milmod.client.HeavyExplosionClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class ChannelFilterMixin implements ConcussionAudio.FilteredChannel {
    @Shadow(aliases = "f_83642_", remap = false) @Final private int source;
    @Unique private ConcussionAudio.Filter milmod$filter;
    @Unique private volatile boolean milmod$exempt;
    @Unique private volatile ConcussionAudio.Bandwidth milmod$bandwidth;

    @Override
    public void milmod$exempt(boolean value) {
        milmod$exempt = value;
    }

    @Override
    public void milmod$bandwidth(ConcussionAudio.Bandwidth source) { milmod$bandwidth = source; }

    @Inject(method = {"updateStream()V", "m_83682_()V"}, at = @At("HEAD"), remap = false)
    private void milmod$apply(CallbackInfo callback) { milmod$update(); }

    @Inject(method = {"destroy()V", "m_83665_()V"}, at = @At("HEAD"), remap = false)
    private void milmod$release(CallbackInfo callback) {
        if (milmod$filter != null) milmod$filter.destroy(source);
    }

    @Unique
    private void milmod$update() {
        float gain = milmod$exempt ? 1F : HeavyExplosionClient.highFrequencyGain();
        ConcussionAudio.Bandwidth bandwidth = milmod$bandwidth;
        if (bandwidth != null) gain = Math.min(gain, bandwidth.highFrequencyGain());
        if (milmod$filter == null && gain < .99F) milmod$filter = new ConcussionAudio.Filter();
        if (milmod$filter != null) milmod$filter.apply(source,gain);
    }
}
