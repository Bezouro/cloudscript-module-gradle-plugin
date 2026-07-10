package br.com.cloudmc.examples.multitarget.runtime;

import net.eq2online.macros.core.Macros;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class Desktop18RuntimePort extends CloudScriptMinecraftRuntimePort {
    public Desktop18RuntimePort(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        super("desktop-1.8", provider, macro, action);
    }

    @Override
    public List onlinePlayers() {
        List names = new ArrayList();
        try {
            Collection entries = Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap();
            Iterator it = entries.iterator();
            while (it.hasNext()) {
                NetworkPlayerInfo entry = (NetworkPlayerInfo) it.next();
                if (entry.getGameProfile() != null && entry.getGameProfile().getName() != null) {
                    names.add(entry.getGameProfile().getName());
                }
            }
        } catch (Throwable ignored) {
        }
        return names.isEmpty() ? super.onlinePlayers() : names;
    }

    @Override
    public BlockView blockAt(int x, int y, int z) {
        try {
            IBlockState state = Minecraft.getMinecraft().theWorld.getBlockState(new BlockPos(x, y, z));
            Block block = state.getBlock();
            int id = Block.getIdFromBlock(block);
            int metadata = block.getMetaFromState(state);
            return new BlockView(id, metadata, Macros.getBlockName(block));
        } catch (Throwable ignored) {
            return super.blockAt(x, y, z);
        }
    }
}
