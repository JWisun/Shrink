package net.gigabit101.shrink.api;

import net.minecraft.entity.EntitySize;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;

public interface IShrinkProvider extends INBTSerializable<CompoundNBT>
{
    boolean isShrunk();

    void setShrunk(boolean set);

    void sync(@Nonnull LivingEntity livingEntity);

    void shrink(@Nonnull LivingEntity livingEntity);

    void deShrink(@Nonnull LivingEntity livingEntity);

    EntitySize defaultEntitySize();

    float defaultEyeHeight();

    float scale();

    void setScale(float scale);
}
