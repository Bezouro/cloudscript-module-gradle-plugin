package br.com.cloudmc.examples.multitarget.runtime;

import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MinicraftRuntimePort extends CloudScriptMinecraftRuntimePort {
    public MinicraftRuntimePort(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        super("minicraft", provider, macro, action);
    }

    @Override
    public List onlinePlayers() {
        List names = iteratorRows("players");
        return names.isEmpty() ? super.onlinePlayers() : names;
    }

    @Override
    public BlockView blockAt(int x, int y, int z) {
        try {
            Method idMethod = provider.getClass().getMethod("actionGetBlockId", int.class, int.class, int.class);
            Method metaMethod = provider.getClass().getMethod("actionGetBlockMetadata", int.class, int.class, int.class);
            int id = ((Number) idMethod.invoke(provider, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z))).intValue();
            int metadata = ((Number) metaMethod.invoke(provider, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z))).intValue();
            return new BlockView(id, metadata, "block");
        } catch (Throwable ignored) {
            return super.blockAt(x, y, z);
        }
    }

    private List iteratorRows(String name) {
        List names = new ArrayList();
        try {
            Method method = provider.getClass().getMethod("actionGetIteratorRows", String.class);
            Object rows = method.invoke(provider, name);
            if (rows instanceof Iterable) {
                for (Object row : (Iterable) rows) {
                    if (row instanceof Map) {
                        Object value = ((Map) row).get("PLAYERNAME");
                        if (value != null && String.valueOf(value).length() > 0) {
                            names.add(String.valueOf(value));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }
}
