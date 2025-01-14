package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.event.SoundEventBiome;
import com.github.charlyb01.music_control.event.SoundLoadedEvent;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class MusicControlClient implements ClientModInitializer {
    public static final String MOD_ID = "music_control";

    public static boolean init = false;
    public static boolean inCustomTracking = false;
    public static boolean isPaused = false;
    public static boolean shouldPlay = true;
    public static boolean categoryChanged = false;
    public static Identifier musicSelected;

    public static Identifier currentMusic = new Identifier("current");
    public static String currentCategory;

    public static boolean previousMusic = false;
    public static boolean nextMusic = false;
    public static boolean pauseResume = false;
    public static boolean loopMusic = false;
    public static boolean previousCategory = false;
    public static boolean nextCategory = false;
    public static boolean printMusic = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(ModConfig.class, JanksonConfigSerializer::new);
        SoundEventBiome.init();
        SoundLoadedEvent.SOUNDS_LOADED.register(soundManager -> MusicCategories.init(MinecraftClient.getInstance()));

        currentCategory = ModConfig.get().musicCategoryStart;

        MusicKeyBinding.init();
        MusicKeyBinding.register();
    }
}
