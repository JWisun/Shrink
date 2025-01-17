package net.gigabit101.shrink.items;

import net.gigabit101.shrink.ShrinkContainer;
import net.gigabit101.shrink.api.ShrinkAPI;
import net.gigabit101.shrink.client.KeyBindings;
import net.gigabit101.shrink.config.ShrinkConfig;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class ItemShrinkingDevice extends Item implements INamedContainerProvider
{
    public ItemShrinkingDevice(Properties properties)
    {
        super(properties.rarity(Rarity.EPIC).maxStackSize(1));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand)
    {
        ItemStack stack = player.getHeldItem(hand);

        if(!player.isSneaking())
        {
            player.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
            {
               if(!iShrinkProvider.isShrunk())
               {
                   if (!world.isRemote) NetworkHooks.openGui((ServerPlayerEntity) player, this);
               }
               else
               {
                   if (!world.isRemote) player.sendStatusMessage(new StringTextComponent("Can't open while shrunk"), false);
               }
            });
        }

        if (!world.isRemote() && player.isSneaking())
        {
            player.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
            {
                if (!iShrinkProvider.isShrunk() && canUse(stack, player))
                {
                    iShrinkProvider.shrink((ServerPlayerEntity) player);
                }
                else if(iShrinkProvider.isShrunk() && canUse(stack, player))
                {
                    iShrinkProvider.deShrink((ServerPlayerEntity) player);
                }
                else if(!canUse(stack, player) && ShrinkConfig.POWER_REQUIREMENT.get())
                {
                    player.sendStatusMessage(new StringTextComponent("Not enough power in device"), false);
                }
            });
        }

        if(world.isRemote && player.isSneaking())
        {
            world.playSound(player.getPosX(), player.getPosY(), player.getPosZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 5F, 0F, true);
            spawnParticle(world, player.getPosX(), player.getPosY(), player.getPosZ() -1, world.rand);
        }
        return new ActionResult<>(ActionResultType.PASS, player.getHeldItem(hand));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, PlayerEntity player, Entity entity)
    {
        AtomicReference<Float> scale = new AtomicReference<>(0.1F);
        player.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider -> scale.set(iShrinkProvider.scale()));

        if(entity instanceof LivingEntity && !entity.world.isRemote)
        {
            entity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
            {
                iShrinkProvider.setScale(scale.get());

                if (!iShrinkProvider.isShrunk() && canUse(stack, player))
                {
                    iShrinkProvider.shrink((LivingEntity) entity);
                }
                else if (iShrinkProvider.isShrunk() && canUse(stack, player))
                {
                    iShrinkProvider.deShrink((LivingEntity) entity);
                }
            });
            return true;
        }
        return false;
    }

    public void spawnParticle(World worldIn, double posX, double posY, double posZ, Random rand)
    {
        for (int i = 0; i < 16; ++i)
        {
            int j = rand.nextInt(2) * 2 - 1;
            int k = rand.nextInt(2) * 2 - 1;
            double d0 = posX + 0.5D + 0.25D * (double) j;
            double d1 = ((float) posY + rand.nextFloat());
            double d2 = posZ + 0.5D + 0.25D * (double) k;
            double d3 = (rand.nextFloat() * (float) j);
            double d4 = (rand.nextFloat() - 0.5D) * 0.125D;
            double d5 = (rand.nextFloat() * (float) k);
            worldIn.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
        }
    }

    public boolean canUse(ItemStack stack, PlayerEntity playerEntity)
    {
        if(!ShrinkConfig.POWER_REQUIREMENT.get()) return true;
        if(playerEntity.isCreative()) return true;

        LazyOptional<IEnergyStorage> optional = stack.getCapability(CapabilityEnergy.ENERGY);
        if(optional.isPresent())
        {
            IEnergyStorage energyStorage = optional.orElseThrow(IllegalStateException::new);
            if(energyStorage.getEnergyStored() >= ShrinkConfig.POWER_COST.get())
            {
                energyStorage.extractEnergy(ShrinkConfig.POWER_COST.get(), false);
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundNBT nbt)
    {
        return new ICapabilityProvider()
        {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
            {
                if (cap == CapabilityEnergy.ENERGY) return LazyOptional.of(() -> new EnergyStorageItemImpl(stack, ShrinkConfig.POWER_CAPACITY.get(), ShrinkConfig.POWER_CAPACITY.get(), ShrinkConfig.POWER_CAPACITY.get())).cast();
                return LazyOptional.empty();
            }
        };
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack)
    {
        return ShrinkConfig.POWER_REQUIREMENT.get();
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack)
    {
        return 1 - getChargeRatio(stack);
    }

    @Override
    public int getRGBDurabilityForDisplay(ItemStack stack)
    {
        return MathHelper.hsvToRGB((1 + getChargeRatio(stack)) / 3.0F, 1.0F, 1.0F);
    }

    public static float getChargeRatio(ItemStack stack)
    {
        LazyOptional<IEnergyStorage> optional = stack.getCapability(CapabilityEnergy.ENERGY);
        if (optional.isPresent())
        {
            IEnergyStorage energyStorage = optional.orElseThrow(IllegalStateException::new);
            return (float) energyStorage.getEnergyStored() / energyStorage.getMaxEnergyStored();
        }
        return 0;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        tooltip.add(new StringTextComponent(TextFormatting.DARK_PURPLE + "Sneak-Click " + TextFormatting.WHITE + "or press " + TextFormatting.DARK_PURPLE + KeyBindings.shrink.getKey().func_237520_d_().getString() + TextFormatting.WHITE + " to active"));
        LazyOptional<IEnergyStorage> optional = stack.getCapability(CapabilityEnergy.ENERGY);
        if (optional.isPresent())
        {
            IEnergyStorage energyStorage = optional.orElseThrow(IllegalStateException::new);
            tooltip.add(new StringTextComponent(energyStorage.getEnergyStored() + " FE / " + energyStorage.getMaxEnergyStored() + " FE"));
        }
    }

    @Override
    public ITextComponent getDisplayName()
    {
        return new TranslationTextComponent(this.getTranslationKey());
    }

    @Nullable
    @Override
    public Container createMenu(int id, PlayerInventory inv, PlayerEntity player)
    {
        return new ShrinkContainer(id, inv);
    }

    public static class EnergyStorageItemImpl extends EnergyStorage
    {
        private final ItemStack stack;

        public EnergyStorageItemImpl(ItemStack stack, int capacity, int maxReceive, int maxExtract)
        {
            super(capacity, maxReceive, maxExtract);
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate)
        {
            if (!canReceive()) return 0;
            int energyStored = getEnergyStored();
            int energyReceived = Math.min(capacity - energyStored, Math.min(this.maxReceive, maxReceive));
            if (!simulate) setEnergyStored(energyStored + energyReceived);
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate)
        {
            if (!canExtract()) return 0;
            int energyStored = getEnergyStored();
            int energyExtracted = Math.min(energyStored, Math.min(this.maxExtract, maxExtract));
            if (!simulate) setEnergyStored(energyStored - energyExtracted);
            return energyExtracted;
        }

        @Override
        public int getEnergyStored()
        {
            return stack.getOrCreateTag().getInt("Energy");
        }

        public void setEnergyStored(int amount)
        {
            stack.getOrCreateTag().putInt("Energy", MathHelper.clamp(amount, 0, this.capacity));
        }
    }
}
