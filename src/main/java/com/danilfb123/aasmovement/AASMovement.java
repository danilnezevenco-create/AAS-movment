package com.danilfb123.aasmovement;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("aas_movement") // Это ID должен совпадать с тем, что в mods.toml
public class AASMovement {
    public AASMovement() {
        MinecraftForge.EVENT_BUS.register(new MovementHandler());
    }
}