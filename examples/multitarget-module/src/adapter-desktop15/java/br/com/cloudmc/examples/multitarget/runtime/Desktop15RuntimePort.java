package br.com.cloudmc.examples.multitarget.runtime;

import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Desktop15RuntimePort extends CloudScriptMinecraftRuntimePort {
    public Desktop15RuntimePort(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        super("desktop-1.5", provider, macro, action);
    }

    @Override
    public List onlinePlayers() {
        List names = new ArrayList();
        try {
            Object world = Minecraft.getMinecraft().theWorld;
            Object playerEntities = readField(world, "playerEntities");
            if (playerEntities instanceof Iterable) {
                for (Object player : (Iterable) playerEntities) {
                    String name = playerName(player);
                    if (name.length() > 0) {
                        names.add(name);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return names.isEmpty() ? super.onlinePlayers() : names;
    }

    @Override
    public BlockView blockAt(int x, int y, int z) {
        try {
            Object world = Minecraft.getMinecraft().theWorld;
            int id = intCall(world, "getBlockId", x, y, z);
            int metadata = intCall(world, "getBlockMetadata", x, y, z);
            return new BlockView(id, metadata, "block");
        } catch (Throwable ignored) {
            return super.blockAt(x, y, z);
        }
    }

    private static int intCall(Object target, String methodName, int x, int y, int z) throws Exception {
        Method method = target.getClass().getMethod(methodName, int.class, int.class, int.class);
        Object value = method.invoke(target, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object readField(Object target, String name) throws Exception {
        if (target == null) return null;
        Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private static String playerName(Object player) {
        if (player == null) return "";
        for (String methodName : new String[] { "getEntityName", "getCommandSenderName" }) {
            try {
                Method method = player.getClass().getMethod(methodName);
                Object value = method.invoke(player);
                return value == null ? "" : String.valueOf(value);
            } catch (Throwable ignored) {
            }
        }
        try {
            Object value = readField(player, "username");
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
