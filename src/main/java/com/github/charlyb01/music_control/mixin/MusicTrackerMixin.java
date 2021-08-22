package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.categories.MusicCategory;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Random random;
    @Shadow private int timeUntilNextSong;
    @Shadow private SoundInstance current;

    @Shadow public abstract void play(MusicSound type);

    @Inject(at = @At("HEAD"), method = "play", cancellable = true)
    private void playMusic (MusicSound type, CallbackInfo ci) {
        if (!MusicControlClient.shouldPlay) {
            MusicControlClient.shouldPlay = true;
            this.timeUntilNextSong = ModConfig.get().timer * 20;
            ci.cancel();
            return;
        }

        if (MusicControlClient.init && this.client.world != null) {
            this.client.getSoundManager().stop(this.current);

            Identifier identifier = MusicCategories.chooseIdentifier(this.random);
            SoundEvent soundEvent = Registry.SOUND_EVENT.get(identifier) == null ? new SoundEvent(identifier)
                    : Registry.SOUND_EVENT.get(identifier);
            MusicSound musicSound = new MusicSound(soundEvent, ModConfig.get().timer * 20, ModConfig.get().timer * 20, true);

            this.current = PositionedSoundInstance.music(musicSound.getSound());
            if (this.current.getSound() != SoundManager.MISSING_SOUND) {
                this.client.getSoundManager().play(this.current);
            }

            if (ModConfig.get().display.displayAtStart) {
                printMusic();
            }

            this.timeUntilNextSong = ModConfig.get().timer * 20;
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void changeMusic (CallbackInfo ci) {
        if (!ModConfig.get().cheat) {
            if ( this.client.player != null && !this.client.player.isCreative()) {
                MusicCategories.updateCategory(this.client.world);
            }
        }

        if (MusicControlClient.skip) {
            MusicControlClient.skip = false;

            this.play(null);
        }
        if (MusicControlClient.pause) {
            MusicControlClient.pause = false;

            if (MusicControlClient.isPaused) {
                MusicControlClient.isPaused = false;
                this.client.getSoundManager().resumeAll();

                if (this.client.player != null) {
                    this.print(Text.of("PLAY"));
                }
            } else {
                MusicControlClient.isPaused = true;
                this.client.getSoundManager().pauseAll();

                if (this.client.player != null) {
                    this.print(Text.of("PAUSE"));
                }
            }
        }
        if (MusicControlClient.category) {
            MusicControlClient.category = false;

            if (MusicCategories.changeCategory(this.client.player)) {
                this.play(null);
            }
        }
        if (MusicControlClient.random) {
            MusicControlClient.random = false;

            MusicControlClient.currentCategory = MusicCategory.ALL;
            this.play(null);
        }
        if (MusicControlClient.print) {
            MusicControlClient.print = false;

            printMusic();
        }
    }

    private void print(Text text) {
        switch (ModConfig.get().display.displayType) {
            case JUKEBOX -> this.client.inGameHud.setOverlayMessage(text, true);
            case ACTION_BAR -> this.client.inGameHud.setOverlayMessage(text, false);
            case CHAT -> {
                if (this.client.player != null) {
                    this.client.player.sendMessage(text, false);
                }
            }
        }
    }

    private void printMusic() {
        if (this.current != null) {
            String text = MusicControlClient.currentCategory.toString() + ": " + this.current.getSound().getIdentifier();
            if (MusicControlClient.isPaused) {
                text += " [PAUSED] ";
            }
            this.print(new TranslatableText("record.nowPlaying", text));
        }
    }
}
