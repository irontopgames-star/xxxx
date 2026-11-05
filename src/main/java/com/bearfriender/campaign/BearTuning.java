package com.bearfriender.campaign;

/**
 * Parámetros efectivos de Bearfriender: salen de la noche y de las misiones completadas.
 * La IA (Fase 8) y el jefe (Fase 9) los consultan; aquí se decide qué hace cada misión.
 *
 *  - Cinta 1 destruida: usa los televisores para distraer con menos frecuencia.
 *  - Cinta 2 destruida: ya no puede manifestarse a distancia (televisor -> aparición lejana).
 *  - Cinta 3 destruida: pierde una habilidad especial durante el combate final.
 *  - Sistema eléctrico restaurado: reduce su control de los dispositivos y permite aturdirlo.
 *  - Señal desactivada: no puede sobrecargarse ni potenciarse en el combate final.
 */
public record BearTuning(double stalkMin, double stalkMax, int appearCooldownSec, float tvUseChance,
                         boolean chaseAllowed, int chaseMaxSec, boolean opensDoors,
                         boolean canManifestAtDistance, boolean specialAbilityEnabled,
                         boolean canOverload, boolean stunMechanismsAvailable) {

    public static BearTuning effective(int night, CampaignData camp) {
        NightDefinition n = NightDefinition.of(night);
        int tapes = camp.getTapesDestroyed();
        float tv = n.tvUseChance();
        if (tapes >= 1) tv *= 0.55F;
        if (camp.isMissionDone(MissionId.POWER)) tv *= 0.8F;
        return new BearTuning(
                n.stalkMin(), n.stalkMax(), n.appearCooldownSec(), tv,
                n.chaseAllowed(), n.chaseMaxSec(), n.opensDoors(),
                tapes < 2,
                tapes < 3,
                !camp.isMissionDone(MissionId.SIGNAL),
                camp.isMissionDone(MissionId.POWER));
    }
}
