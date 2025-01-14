package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.Utils;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

import static com.github.charlyb01.music_control.categories.Music.EMPTY_MUSIC;
import static com.github.charlyb01.music_control.categories.Music.EMPTY_MUSIC_ID;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private Random random;
    @Shadow
    private int timeUntilNextSong;
    @Shadow
    private SoundInstance current;

    @Shadow
    public abstract void play(MusicSound type);

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void playMusic(MusicSound type, CallbackInfo ci) {

        final Identifier id = type != null ? type.getSound().value().getId() : null;

        MusicControlClient.inCustomTracking = false;

        // do nothing if world is not loaded or controller is not enabled
        if (!MusicControlClient.init || this.client.world == null) {
            return;
        }

        // reset the pending time if it should not play
        if (!MusicControlClient.shouldPlay) {
            MusicControlClient.shouldPlay = true;
            this.timeUntilNextSong = Utils.getTimer(this.random);
            ci.cancel();
            return;
        }

        // stop current music
        this.client.getSoundManager().stop(this.current);

        if (MusicControlClient.musicSelected != null) {
            // a new music is selected from the menu
            MusicControlClient.currentMusic = MusicControlClient.musicSelected;
            MusicControlClient.musicSelected = null;
        } else if (MusicControlClient.previousMusic) {
            // previous music is assigned
            MusicControlClient.previousMusic = false;
            Identifier music = MusicCategories.PLAYED_MUSICS.peekLast();
            if (music != null) {
                MusicControlClient.currentMusic = music;
            }
        } else if (MusicControlClient.loopMusic) {
            // loop mode is on
            // do nothing, use the same music
        } else if (id != null
                && MusicControlClient.currentCategory.equals(Music.DEFAULT_MUSICS)
                && Music.MUSIC_BY_EVENT.containsKey(id)) {
            // normal procedure
            final ArrayList<Music> musics = new ArrayList<>(Music.MUSIC_BY_EVENT.get(id));
            final ArrayList<Music> gameMusics = new ArrayList<>(Music.MUSIC_BY_EVENT.get(MusicType.GAME.getSound().value().getId()));
            if (musics.isEmpty()) {
                // this means the current event corresponds to
                // an event with no music.
                if (ModConfig.get().musicFallback && !gameMusics.isEmpty()) {
                    // we should fallback on default game music
                    MusicControlClient.currentMusic = MusicCategories.getRandomMusicIdentifier(gameMusics, this.random);
                } else {
                    // don't play music
                    MusicControlClient.currentMusic = EMPTY_MUSIC_ID;
                    this.timeUntilNextSong = Utils.getTimer(this.random);
                    ci.cancel();
                    return;
                }
            } else {
                // play a music from current event
                MusicControlClient.currentMusic = MusicCategories.getRandomMusicIdentifier(musics, this.random);
            }
        } else {
            // just randomly pick one
            // from current category
            MusicControlClient.currentMusic = MusicCategories.getMusicIdentifier(this.random);
        }

        this.current = PositionedSoundInstance.music(SoundEvent.of(MusicControlClient.currentMusic));
        if (this.current.getSound() != SoundManager.MISSING_SOUND) {
            this.client.getSoundManager().play(this.current);
            MusicControlClient.inCustomTracking = true;
        }

        displayMusic();

        this.timeUntilNextSong = Utils.getTimer(this.random);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void handleMusic(CallbackInfo ci) {
        handlePreviousMusicKey();
        handleNextMusicKey();
        handleResumePauseKey();
        handleChangeCategoryKey();
        handleDisplayMusicKey();

        if (MusicControlClient.inCustomTracking) {
            if (this.client == null // no client
                    || !MusicControlClient.init || this.client.world == null // not in game
                    || this.current == null || !this.client.getSoundManager().isPlaying(this.current)) { // current stopped
                MusicControlClient.inCustomTracking = false;
            } else {
                // The music in playing could be forcedly replaced by the original tracker
                // if `this.client.getMusicType().shouldReplaceCurrentMusic()` is true.
                // This causes the absence of custom music in some cases (like in the end).
                ci.cancel();
            }
        }
    }

    private void displayMusic() {
        if (ModConfig.get().displayAtStart || MusicControlClient.categoryChanged) {
            printMusic();
        }

        if (MusicControlClient.categoryChanged) {
            MusicControlClient.categoryChanged = false;
        }
    }

    private void printMusic() {
        if (this.client.world == null)
            return;

        final String currentMusic = this.current != null ? this.current.getSound().getIdentifier().toString()
                : EMPTY_MUSIC;
        if (MusicControlClient.isPaused) {
            Utils.print(this.client, Text.translatable("music.paused"));

        } else if (currentMusic.equals(EMPTY_MUSIC)) {
            if (ModConfig.get().displayRemainingSeconds) {
                double remaining = this.timeUntilNextSong / 20.0;
                Utils.print(this.client, Text.translatable("music.no_playing_with_time", String.valueOf(remaining)));
            } else {
                Utils.print(this.client, Text.translatable("music.no_playing"));
            }
        } else {
            String categoryText = MusicControlClient.categoryChanged
                    ? MusicControlClient.currentCategory.toUpperCase().replace('_', ' ') + ": %s"
                    : "record.nowPlaying";
            Text title = Text.translatable(currentMusic);
            Utils.print(this.client, Text.translatable(categoryText, title));
        }
    }

    // key pressed event handlers

    private void handlePreviousMusicKey() {
        if (MusicControlClient.previousMusic) {
            if (MusicControlClient.isPaused) {
                MusicControlClient.previousMusic = false;
                printMusic();
            } else {
                MusicCategories.PLAYED_MUSICS.pollLast();
                this.play(null);
            }
        }
    }

    private void handleNextMusicKey() {
        if (MusicControlClient.nextMusic) {
            MusicControlClient.nextMusic = false;
            MusicControlClient.loopMusic = false;

            if (MusicControlClient.isPaused) {
                printMusic();
            } else {
                this.play(this.client.getMusicType());
            }
        }
    }

    private void handleResumePauseKey() {
        if (MusicControlClient.pauseResume) {
            MusicControlClient.pauseResume = false;

            if (MusicControlClient.isPaused) {
                MusicControlClient.isPaused = false;
                this.client.getSoundManager().resumeAll();

                if (this.client.player != null) {
                    Utils.print(this.client, Text.translatable("music.play"));
                }
            } else {
                MusicControlClient.isPaused = true;
                this.client.getSoundManager().pauseAll();

                if (this.client.player != null) {
                    Utils.print(this.client, Text.translatable("music.pause"));
                }
            }
        }
    }

    private void handleChangeCategoryKey() {
        if (MusicControlClient.nextCategory || MusicControlClient.previousCategory) {
            MusicControlClient.categoryChanged = true;
            MusicCategories.changeCategory(MusicControlClient.nextCategory);

            if (MusicControlClient.nextCategory) {
                MusicControlClient.nextCategory = false;
            } else {
                MusicControlClient.previousCategory = false;
            }

            this.play(this.client.getMusicType());
        }
    }

    private void handleDisplayMusicKey() {
        if (MusicControlClient.printMusic) {
            MusicControlClient.printMusic = false;

            printMusic();
        }
    }
}
