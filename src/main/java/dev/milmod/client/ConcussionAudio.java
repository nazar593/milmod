package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.sound.ModSounds;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.event.sound.SoundEvent.SoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** OpenAL calls occur on the sound thread through Channel. */
@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class ConcussionAudio {
    public interface Bandwidth { float highFrequencyGain(); }

    public interface FilteredChannel {
        void milmod$exempt(boolean value);
        void milmod$bandwidth(Bandwidth source);
    }

    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private ConcussionAudio() {}

    private static void unavailable(String reason) {
        if (REPORTED.compareAndSet(false,true)) LogUtils.getLogger().debug("MilMod OpenAL EFX unavailable: {}", reason);
    }

    @SubscribeEvent
    public static void staticSound(PlaySoundSourceEvent event) { apply(event); }

    @SubscribeEvent
    public static void streamingSound(PlayStreamingSourceEvent event) { apply(event); }

    private static void apply(SoundSourceEvent event) {
        if (event.getChannel() instanceof FilteredChannel channel) {
            var sound = event.getSound();
            channel.milmod$bandwidth(sound instanceof Bandwidth bandwidth ? bandwidth : null);
            var name = sound.getLocation();
            channel.milmod$exempt(name.equals(ModSounds.TINNITUS.get().getLocation())
                    || name.equals(ModSounds.HEARTBEAT.get().getLocation()));
        }
    }

    public static final class Filter {
        private static final int TYPE = 0x8001;
        private static final int LOWPASS = 1;
        private static final int GAIN = 1;
        private static final int GAIN_HF = 2;
        private static final int DIRECT = 0x20005;
        private static final Api API = Api.load();
        private int id;
        private float last = 1F;
        private boolean unsupported;

        public void apply(int source, float requested) {
            if (API == null) {
                unavailable("LWJGL EFX entry points missing");
                return;
            }
            if (unsupported || Math.abs(requested-last)<.013F && requested<.99F) return;
            try {
                if (requested >= .99F) {
                    destroy(source);
                    return;
                }
                if (id == 0) {
                    long context = (long) API.context.invoke(null);
                    if (context == 0) return;
                    long device = (long) API.device.invoke(null,context);
                    if (device == 0 || !(boolean) API.extension.invoke(null,device,"ALC_EXT_EFX")) {
                        unavailable("active audio device does not report EFX");
                        unsupported=true;
                        return;
                    }
                    id = (int) API.generate.invoke(null);
                    if (id == 0) { unsupported=true; return; }
                    API.parameter.invoke(null,id,TYPE,LOWPASS);
                    API.gain.invoke(null,id,GAIN,.92F);
                }
                API.gain.invoke(null,id,GAIN_HF,requested);
                API.source.invoke(null,source,DIRECT,id);
                last=requested;
            } catch (ReflectiveOperationException | LinkageError exception) {
                destroy(source);
                unavailable(exception.getClass().getSimpleName());
                unsupported=true;
            }
        }

        public void destroy(int source) {
            if (id == 0 || API == null) return;
            try {
                API.source.invoke(null,source,DIRECT,0);
                API.delete.invoke(null,id);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // An audio-device reset can invalidate the context before a channel is closed.
            } finally {
                id=0; last=1;
            }
        }

        private record Api(Method context, Method device, Method extension, Method generate, Method parameter,
                           Method gain, Method delete, Method source) {
            static Api load() {
                try {
                    var alc = Class.forName("org.lwjgl.openal.ALC10");
                    var al = Class.forName("org.lwjgl.openal.AL10");
                    var efx = Class.forName("org.lwjgl.openal.EXTEfx");
                    return new Api(alc.getMethod("alcGetCurrentContext"),alc.getMethod("alcGetContextsDevice",long.class),
                            alc.getMethod("alcIsExtensionPresent",long.class,CharSequence.class),
                            efx.getMethod("alGenFilters"),efx.getMethod("alFilteri",int.class,int.class,int.class),
                            efx.getMethod("alFilterf",int.class,int.class,float.class),efx.getMethod("alDeleteFilters",int.class),
                            al.getMethod("alSourcei",int.class,int.class,int.class));
                } catch (ReflectiveOperationException | LinkageError exception) {
                    return null;
                }
            }
        }
    }
}
