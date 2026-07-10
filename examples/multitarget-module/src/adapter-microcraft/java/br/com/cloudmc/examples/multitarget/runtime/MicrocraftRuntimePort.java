package br.com.cloudmc.examples.multitarget.runtime;

import br.com.bezouro.microcraft.world.BlockState;
import com.bezouro.modules.cloudscript.microcraft.MicrocraftRuntime;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MicrocraftRuntimePort extends CloudScriptMinecraftRuntimePort {
    public MicrocraftRuntimePort(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        super("microcraft", provider, macro, action);
    }

    @Override
    public List onlinePlayers() {
        List names = new ArrayList();
        List rows = provider.actionGetIteratorRows("players");
        for (Object row : rows) {
            if (row instanceof Map) {
                Object value = ((Map) row).get("PLAYERNAME");
                if (value != null && String.valueOf(value).length() > 0) {
                    names.add(String.valueOf(value));
                }
            }
        }
        return names.isEmpty() ? super.onlinePlayers() : names;
    }

    @Override
    public BlockView blockAt(int x, int y, int z) {
        BlockState block = MicrocraftRuntime.blockAt(x, y, z);
        return new BlockView(block.id(), block.metadata(), block.toString());
    }
}
