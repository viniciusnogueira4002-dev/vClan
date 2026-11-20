package org.vine.vclans.hook;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import org.vine.vclans.util.ColorUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Ponte reflexiva para DragonMineZ no Mohist 1.20.1:
 * - Leitura/escrita exata de TP (suporta double de Resources e fallback BigInteger/float)
 * - Sincronização leve via ResourceSyncS2C (fallback StatsSyncS2C)
 * - Listener de DMZEvent.TPGainEvent no MinecraftForge.EVENT_BUS para aplicar o Multiplicador de TP do Clã.
 */
public class DragonMineZHook {

    private final VClans plugin;
    private final Logger logger;
    private final boolean enabled;

    private final Object statsCapabilityInstance;
    private final Method statsProviderGetMethod;
    private final Method lazyOptionalResolveMethod;
    private final Method statsDataGetResourcesMethod;
    private final Class<?> serverPlayerClass;

    private final Method getTpExactMethod;
    private final Method setTpExactDoubleMethod;
    private final Method setTpExactBigIntMethod;
    private final Method removeTpExactBigIntMethod;
    private final Method getTpLegacyMethod;
    private final Method setTpLegacyFloatMethod;
    private final Method addTpLegacyMethod;
    private final Method removeTpLegacyMethod;

    private final Constructor<?> resourceSyncCtor;
    private final Constructor<?> statsSyncCtor;
    private final Method networkSendMethod;

    private Object forgeEventBus;
    private Consumer<Object> tpGainConsumer;
    private volatile boolean listenerActive = false;

    public DragonMineZHook(VClans plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        Object capInst = null;
        Method provGet = null;
        Method lazyResolve = null;
        Method getResources = null;
        Class<?> spClass = null;

        Method tpExactGet = null;
        Method tpExactSetDouble = null;
        Method tpExactSetBigInt = null;
        Method tpExactRemBigInt = null;
        Method tpLegacyGet = null;
        Method tpLegacySetFloat = null;
        Method tpLegacyAdd = null;
        Method tpLegacyRem = null;

        Constructor<?> resSync = null;
        Constructor<?> stSync = null;
        Method netSend = null;
        boolean ok = false;

        try {
            Class<?> statsCapClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
            Field instanceField = statsCapClass.getField("INSTANCE");
            capInst = instanceField.get(null);

            Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            spClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> mcPlayerClass = Class.forName("net.minecraft.world.entity.player.Player");

            try {
                Class<?> statsProviderClass = Class.forName("com.dragonminez.common.stats.StatsProvider");
                provGet = statsProviderClass.getMethod("get", capabilityClass, entityClass);
            } catch (Throwable ignored) {
                provGet = null;
            }
            if (provGet == null) {
                Class<?> capProviderClass = Class.forName("net.minecraftforge.common.capabilities.ICapabilityProvider");
                provGet = findMethodOrNull(capProviderClass, "getCapability", capabilityClass);
            }

            Class<?> lazyOptionalClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            lazyResolve = lazyOptionalClass.getMethod("resolve");

            Class<?> statsDataClass = Class.forName("com.dragonminez.common.stats.StatsData");
            getResources = statsDataClass.getMethod("getResources");

            Class<?> resourcesClass = Class.forName("com.dragonminez.common.stats.character.Resources");
            tpExactGet = findMethodOrNull(resourcesClass, "getTrainingPointsExact");
            tpExactSetDouble = findMethodOrNull(resourcesClass, "setTrainingPointsExact", double.class);
            tpExactSetBigInt = findMethodOrNull(resourcesClass, "setTrainingPointsExact", BigInteger.class);
            tpExactRemBigInt = findMethodOrNull(resourcesClass, "removeTrainingPointsExact", BigInteger.class);

            tpLegacyGet = findMethodOrNull(resourcesClass, "getTrainingPoints");
            tpLegacySetFloat = findMethodOrNull(resourcesClass, "setTrainingPoints", float.class);
            tpLegacyAdd = findFirstMethod(resourcesClass, "addTrainingPoints",
                    new Class<?>[]{float.class, boolean.class},
                    new Class<?>[]{float.class},
                    new Class<?>[]{int.class, boolean.class},
                    new Class<?>[]{int.class});
            tpLegacyRem = findFirstMethod(resourcesClass, "removeTrainingPoints",
                    new Class<?>[]{float.class},
                    new Class<?>[]{int.class});

            try {
                Class<?> resSyncClass = Class.forName("com.dragonminez.common.network.S2C.ResourceSyncS2C");
                resSync = findAssignableConstructor(resSyncClass, spClass, mcPlayerClass);
            } catch (Throwable ignored) {}

            try {
                Class<?> syncClass = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C");
                stSync = findAssignableConstructor(syncClass, spClass, mcPlayerClass);
            } catch (Throwable ignored) {}

            Class<?> netHandlerClass = Class.forName("com.dragonminez.common.network.NetworkHandler");
            netSend = findFirstMethod(netHandlerClass, "sendToTrackingEntityAndSelf",
                    new Class<?>[]{Object.class, entityClass},
                    new Class<?>[]{Object.class, spClass});
            if (netSend == null) {
                netSend = findFirstMethod(netHandlerClass, "sendToPlayer",
                        new Class<?>[]{Object.class, spClass});
            }

            ok = capInst != null && provGet != null && lazyResolve != null && getResources != null
                    && (tpExactGet != null || tpLegacyGet != null);
        } catch (Throwable t) {
            logger.info("[vClans] DragonMineZ não detectado no servidor (integração de TP desativada): " + t.getClass().getSimpleName());
        }

        this.statsCapabilityInstance = capInst;
        this.statsProviderGetMethod = provGet;
        this.lazyOptionalResolveMethod = lazyResolve;
        this.statsDataGetResourcesMethod = getResources;
        this.serverPlayerClass = spClass;

        this.getTpExactMethod = tpExactGet;
        this.setTpExactDoubleMethod = tpExactSetDouble;
        this.setTpExactBigIntMethod = tpExactSetBigInt;
        this.removeTpExactBigIntMethod = tpExactRemBigInt;
        this.getTpLegacyMethod = tpLegacyGet;
        this.setTpLegacyFloatMethod = tpLegacySetFloat;
        this.addTpLegacyMethod = tpLegacyAdd;
        this.removeTpLegacyMethod = tpLegacyRem;

        this.resourceSyncCtor = resSync;
        this.statsSyncCtor = stSync;
        this.networkSendMethod = netSend;
        this.enabled = ok;

        if (ok) {
            registerTpGainListener();
            logger.info("[vClans] DragonMineZHook ativado (suporte a Resources.double + ResourceSyncS2C + TPGainEvent).");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerTpGainListener() {
        try {
            Class<?> minecraftForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            this.forgeEventBus = minecraftForgeClass.getField("EVENT_BUS").get(null);

            Class<?> tpGainEventClass = Class.forName("com.dragonminez.common.events.DMZEvent$TPGainEvent");
            Class<?> priorityClass = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Object lowPriority = Enum.valueOf((Class<Enum>) priorityClass, "LOW");

            Method getPlayerMethod = tpGainEventClass.getMethod("getPlayer");
            Method getTpGainMethod = tpGainEventClass.getMethod("getTpGain");
            Method setTpGainMethod = tpGainEventClass.getMethod("setTpGain", int.class);

            this.listenerActive = true;
            this.tpGainConsumer = event -> {
                if (!listenerActive || event == null || !Bukkit.isPrimaryThread()) {
                    return;
                }
                try {
                    Object dmzPlayer = getPlayerMethod.invoke(event);
                    if (dmzPlayer == null || (serverPlayerClass != null && !serverPlayerClass.isInstance(dmzPlayer))) {
                        return;
                    }
                    int baseTp = (int) getTpGainMethod.invoke(event);
                    if (baseTp <= 0) {
                        return;
                    }

                    Player bukkitPlayer = toBukkitPlayer(dmzPlayer);
                    if (bukkitPlayer == null || !bukkitPlayer.isOnline()) {
                        return;
                    }

                    if (plugin.getClanManager() == null || plugin.getBankUpgradeManager() == null) {
                        return;
                    }
                    Clan clan = plugin.getClanManager().getClanByPlayer(bukkitPlayer);
                    if (clan == null || clan.getUpgrades().getTpMultiplierLevel() <= 0) {
                        return;
                    }

                    double factor = plugin.getBankUpgradeManager().getTpMultiplierFactor(clan);
                    if (factor <= 1.0) {
                        return;
                    }

                    int boostedTp = (int) Math.min(Integer.MAX_VALUE, Math.round(baseTp * factor));
                    int extra = boostedTp - baseTp;
                    if (extra > 0) {
                        setTpGainMethod.invoke(event, boostedTp);
                        if (plugin.getConfig().getBoolean("farm-rewards.actionbar-enabled", true)) {
                            double bonusPct = plugin.getBankUpgradeManager().getBonusValue(
                                    UpgradeType.TP_MULTIPLIER, clan.getUpgrades().getTpMultiplierLevel());
                            sendActionBar(bukkitPlayer, "&#55FF55+" + boostedTp + " TP &8(&#FFAA00+"
                                    + Math.round(bonusPct) + "% Clã&8)");
                        }
                    }
                } catch (Throwable ignored) {}
            };

            Method addListener = forgeEventBus.getClass().getMethod("addListener",
                    priorityClass, boolean.class, Class.class, Consumer.class);
            addListener.invoke(forgeEventBus, lowPriority, false, tpGainEventClass, tpGainConsumer);
        } catch (Throwable t) {
            logger.warning("[vClans] Não foi possível registrar listener de DMZEvent.TPGainEvent: " + t.getMessage());
        }
    }

    public void cleanup() {
        this.listenerActive = false;
        if (forgeEventBus != null && tpGainConsumer != null) {
            try {
                Method unregister = forgeEventBus.getClass().getMethod("unregister", Object.class);
                unregister.invoke(forgeEventBus, tpGainConsumer);
            } catch (Throwable ignored) {}
        }
        this.tpGainConsumer = null;
        this.forgeEventBus = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getPlayerTp(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return 0L;
        }
        try {
            Object serverPlayer = getServerPlayer(player);
            Object resources = getResources(serverPlayer);
            if (resources == null) return 0L;

            if (getTpExactMethod != null) {
                Object val = getTpExactMethod.invoke(resources);
                if (val instanceof BigInteger bi) {
                    if (bi.signum() <= 0) return 0L;
                    if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) return Long.MAX_VALUE;
                    return bi.longValue();
                }
                if (val instanceof Number num) {
                    double d = num.doubleValue();
                    if (!Double.isFinite(d) || d <= 0.0) return 0L;
                    if (d >= Long.MAX_VALUE) return Long.MAX_VALUE;
                    return (long) Math.floor(d);
                }
            }

            if (getTpLegacyMethod != null) {
                Object val = getTpLegacyMethod.invoke(resources);
                if (val instanceof Number num) {
                    return Math.max(0L, num.longValue());
                }
            }
        } catch (Throwable t) {
            logger.warning("[vClans] Erro ao ler TP do jogador " + player.getName() + ": " + t.getMessage());
        }
        return 0L;
    }

    public boolean withdrawPlayerTp(Player player, long amount) {
        if (!enabled || !Bukkit.isPrimaryThread() || player == null || !player.isOnline() || amount <= 0L) {
            return false;
        }
        try {
            Object serverPlayer = getServerPlayer(player);
            Object resources = getResources(serverPlayer);
            if (resources == null) return false;

            long current = getPlayerTp(player);
            if (current < amount) {
                return false;
            }

            if (setTpExactDoubleMethod != null && getTpExactMethod != null) {
                Object raw = getTpExactMethod.invoke(resources);
                double curDouble = raw instanceof Number n ? n.doubleValue() : current;
                if (curDouble < amount) return false;
                setTpExactDoubleMethod.invoke(resources, Math.max(0.0, curDouble - (double) amount));
            } else if (removeTpExactBigIntMethod != null) {
                removeTpExactBigIntMethod.invoke(resources, BigInteger.valueOf(amount));
            } else if (setTpExactBigIntMethod != null && getTpExactMethod != null) {
                BigInteger exactCurrent = (BigInteger) getTpExactMethod.invoke(resources);
                BigInteger next = exactCurrent.subtract(BigInteger.valueOf(amount)).max(BigInteger.ZERO);
                setTpExactBigIntMethod.invoke(resources, next);
            } else if (setTpLegacyFloatMethod != null) {
                setTpLegacyFloatMethod.invoke(resources, (float) Math.max(0L, current - amount));
            } else if (removeTpLegacyMethod != null) {
                Class<?> paramType = removeTpLegacyMethod.getParameterTypes()[0];
                if (paramType == float.class) {
                    removeTpLegacyMethod.invoke(resources, (float) amount);
                } else {
                    removeTpLegacyMethod.invoke(resources, (int) Math.min(Integer.MAX_VALUE, amount));
                }
            } else {
                return false;
            }

            syncStats(serverPlayer);
            return true;
        } catch (Throwable t) {
            logger.warning("[vClans] Erro ao retirar TP de " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    public boolean addPlayerTp(Player player, long amount) {
        if (!enabled || !Bukkit.isPrimaryThread() || player == null || !player.isOnline() || amount <= 0L) {
            return false;
        }
        try {
            Object serverPlayer = getServerPlayer(player);
            Object resources = getResources(serverPlayer);
            if (resources == null) return false;

            // Escrita direta via setTrainingPointsExact para não disparar TPGainEvent em loop
            if (setTpExactDoubleMethod != null && getTpExactMethod != null) {
                Object raw = getTpExactMethod.invoke(resources);
                double curDouble = raw instanceof Number n ? n.doubleValue() : 0.0;
                setTpExactDoubleMethod.invoke(resources, curDouble + (double) amount);
            } else if (setTpExactBigIntMethod != null && getTpExactMethod != null) {
                BigInteger exactCurrent = (BigInteger) getTpExactMethod.invoke(resources);
                if (exactCurrent == null) exactCurrent = BigInteger.ZERO;
                setTpExactBigIntMethod.invoke(resources, exactCurrent.add(BigInteger.valueOf(amount)));
            } else if (addTpLegacyMethod != null) {
                Class<?>[] params = addTpLegacyMethod.getParameterTypes();
                if (params.length == 2 && params[0] == float.class && params[1] == boolean.class) {
                    addTpLegacyMethod.invoke(resources, (float) amount, false);
                } else if (params.length == 1 && params[0] == float.class) {
                    addTpLegacyMethod.invoke(resources, (float) amount);
                } else if (params.length == 2 && params[0] == int.class && params[1] == boolean.class) {
                    addTpLegacyMethod.invoke(resources, (int) Math.min(Integer.MAX_VALUE, amount), false);
                } else {
                    addTpLegacyMethod.invoke(resources, (int) Math.min(Integer.MAX_VALUE, amount));
                }
            } else {
                return false;
            }

            syncStats(serverPlayer);
            return true;
        } catch (Throwable t) {
            logger.warning("[vClans] Erro ao adicionar TP para " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    private Player toBukkitPlayer(Object mcEntity) {
        try {
            Method getBukkitEntity = mcEntity.getClass().getMethod("getBukkitEntity");
            Object bukkit = getBukkitEntity.invoke(mcEntity);
            if (bukkit instanceof Player p) {
                return p;
            }
        } catch (Throwable ignored) {}

        try {
            Method mUuid = findMethodOrNull(mcEntity.getClass(), "m_20148_");
            if (mUuid == null) {
                mUuid = findMethodOrNull(mcEntity.getClass(), "getUUID");
            }
            if (mUuid != null) {
                Object uuidObj = mUuid.invoke(mcEntity);
                if (uuidObj instanceof UUID uuid) {
                    return Bukkit.getPlayer(uuid);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object getServerPlayer(Player bukkitPlayer) throws Exception {
        Method getHandle = bukkitPlayer.getClass().getMethod("getHandle");
        return getHandle.invoke(bukkitPlayer);
    }

    private Object getResources(Object serverPlayer) throws Exception {
        if (serverPlayer == null) return null;
        Object lazyOpt = Modifier.isStatic(statsProviderGetMethod.getModifiers())
                ? statsProviderGetMethod.invoke(null, statsCapabilityInstance, serverPlayer)
                : statsProviderGetMethod.invoke(serverPlayer, statsCapabilityInstance);
        if (lazyOpt == null) return null;
        Optional<?> resolved = (Optional<?>) lazyOptionalResolveMethod.invoke(lazyOpt);
        if (resolved == null || resolved.isEmpty()) return null;
        Object statsData = resolved.get();
        return statsDataGetResourcesMethod.invoke(statsData);
    }

    private void syncStats(Object serverPlayer) {
        if (networkSendMethod == null || serverPlayer == null) {
            return;
        }
        try {
            Constructor<?> ctor = resourceSyncCtor != null ? resourceSyncCtor : statsSyncCtor;
            if (ctor == null) return;
            Object packet = ctor.newInstance(serverPlayer);
            networkSendMethod.invoke(null, packet, serverPlayer);
        } catch (Throwable t) {
            logger.warning("[vClans] Falha ao sincronizar TP (ResourceSyncS2C/StatsSyncS2C): " + t.getMessage());
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtil.color(message)));
        } catch (Exception ignored) {}
    }

    private static Method findMethodOrNull(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findFirstMethod(Class<?> clazz, String name, Class<?>[]... signatures) {
        for (Class<?>[] sig : signatures) {
            Method m = findMethodOrNull(clazz, name, sig);
            if (m != null) return m;
        }
        return null;
    }

    private static Constructor<?> findAssignableConstructor(Class<?> clazz, Class<?>... candidateTypes) {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 1) continue;
            for (Class<?> candidate : candidateTypes) {
                if (params[0].isAssignableFrom(candidate)) {
                    return ctor;
                }
            }
        }
        return null;
    }
}
