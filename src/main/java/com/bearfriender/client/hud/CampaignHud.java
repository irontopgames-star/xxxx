package com.bearfriender.client.hud;

import com.bearfriender.client.ClientCampaignState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Reloj de la noche (arriba a la derecha): "NOCHE 2" y la hora, de 12:00 AM a 6:00 AM. */
public final class CampaignHud {
    private CampaignHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!ClientCampaignState.active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        String line1;
        String line2;
        int color = 0xFFCC3A3A;
        if (ClientCampaignState.ended) {
            line1 = "FIN";
            line2 = "Busca la salida";
            color = 0xFF55CC55;
        } else if (ClientCampaignState.night >= 5) {
            line1 = "NOCHE 5";
            line2 = "???";
        } else {
            line1 = "NOCHE " + ClientCampaignState.night
                    + (ClientCampaignState.extraLoops > 0 ? " +" + ClientCampaignState.extraLoops : "");
            line2 = ClientCampaignState.clock();
        }

        int w = Math.max(font.width(line1), font.width(line2)) + 12;
        int x1 = width - 6;
        int x0 = x1 - w;
        int y0 = 6;
        int y1 = y0 + 4 + font.lineHeight * 2 + 6;
        g.fill(x0, y0, x1, y1, 0x99000000);                 // fondo oscuro semitransparente
        g.fill(x0, y0, x1, y0 + 1, 0x66883333);              // borde discreto
        g.fill(x0, y1 - 1, x1, y1, 0x66883333);
        g.drawString(font, line1, x1 - 6 - font.width(line1), y0 + 4, color, true);
        g.drawString(font, line2, x1 - 6 - font.width(line2), y0 + 6 + font.lineHeight, 0xFFDDDDDD, true);
    }
}
