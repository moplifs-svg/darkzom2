package com.example.darkzombies;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(DarkZombies.MODID)
public class DarkZombies {
    public static final String MODID = "darkzombies";

    public DarkZombies() {
        MinecraftForge.EVENT_BUS.register(new ZombieEvents());
    }
}
