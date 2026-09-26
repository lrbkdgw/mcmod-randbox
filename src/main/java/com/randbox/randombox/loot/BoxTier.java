package com.randbox.randombox.loot;

import net.minecraft.util.RandomSource;

public enum BoxTier {
    COMMON(1,3,0,0xEEEEEE), RARE(2,4,.5,0x55FF55), EPIC(3,5,1,0xBB55FF),
    LEGENDARY(4,6,2.5,0xFFD83D), MYTHIC(5,8,10,0xFF3333);
    public final int id, count, color; public final double bonus;
    BoxTier(int id,int count,double bonus,int color){this.id=id;this.count=count;this.bonus=bonus;this.color=color;}
    public static BoxTier byId(int id){return values()[Math.max(1,Math.min(5,id))-1];}
    public static BoxTier roll(RandomSource r){double x=r.nextDouble(); return x<.50?COMMON:x<.80?RARE:x<.92?EPIC:x<.985?LEGENDARY:MYTHIC;}
}
