/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2020 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.fp2;

import net.daporkchop.fp2.client.ClientEvents;
import net.daporkchop.fp2.client.FP2ResourceReloadListener;
import net.daporkchop.fp2.client.KeyBindings;
import net.daporkchop.fp2.net.client.CPacketDropAllPieces;
import net.daporkchop.fp2.net.client.CPacketRenderMode;
import net.daporkchop.fp2.net.server.SPacketPieceData;
import net.daporkchop.fp2.net.server.SPacketReady;
import net.daporkchop.fp2.net.server.SPacketRenderingStrategy;
import net.daporkchop.fp2.net.server.SPacketUnloadPiece;
import net.daporkchop.fp2.server.ServerEvents;
import net.daporkchop.fp2.strategy.heightmap.client.HeightmapRenderer;
import net.daporkchop.fp2.strategy.voxel.client.VoxelRenderer;
import net.daporkchop.fp2.util.Constants;
import net.daporkchop.fp2.util.threading.ServerThreadExecutor;
import net.daporkchop.ldbjni.LevelDB;
import net.daporkchop.lib.compression.zstd.Zstd;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GLContext;

import javax.swing.JOptionPane;

import static net.daporkchop.fp2.FP2.*;
import static net.daporkchop.fp2.util.Constants.*;

/**
 * @author DaPorkchop_
 */
@Mod(modid = MODID,
        useMetadata = true,
        acceptedMinecraftVersions = "1.12.2")
public class FP2 {
    public static final String MODID = "fp2";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();

        System.setProperty("porklib.native.printStackTraces", "true");
        if (!Zstd.PROVIDER.isNative()) {
            Constants.bigWarning("Native ZSTD could not be loaded! This will have SERIOUS performance implications!");
        }
        if (!LevelDB.PROVIDER.isNative()) {
            Constants.bigWarning("Native leveldb-mcpe could not be loaded! This will have SERIOUS performance implications!");
        }

        this.registerPackets();

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            if (!GLContext.getCapabilities().OpenGL43) {
                JOptionPane.showMessageDialog(null,
                        "Your system does not support OpenGL 4.3!\nRequired by FarPlaneTwo.",
                        null, JOptionPane.ERROR_MESSAGE);
                FMLCommonHandler.instance().exitJava(1, true);
            }

            MinecraftForge.EVENT_BUS.register(new ClientEvents());

            ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new FP2ResourceReloadListener());
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            KeyBindings.register();
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            //load render classes on client thread
            PUnsafe.ensureClassInitialized(HeightmapRenderer.class);
            PUnsafe.ensureClassInitialized(VoxelRenderer.class);
        }
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        ServerThreadExecutor.INSTANCE.startup();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerThreadExecutor.INSTANCE.workOffQueue();
        ServerThreadExecutor.INSTANCE.shutdown();
    }

    protected void registerPackets() {
        int id = 0;
        NETWORK_WRAPPER.registerMessage(CPacketRenderMode.Handler.class, CPacketRenderMode.class, id++, Side.SERVER);
        NETWORK_WRAPPER.registerMessage(CPacketDropAllPieces.Handler.class, CPacketDropAllPieces.class, id++, Side.SERVER);
        NETWORK_WRAPPER.registerMessage(SPacketReady.Handler.class, SPacketReady.class, id++, Side.CLIENT);
        NETWORK_WRAPPER.registerMessage(SPacketRenderingStrategy.Handler.class, SPacketRenderingStrategy.class, id++, Side.CLIENT);
        NETWORK_WRAPPER.registerMessage(SPacketPieceData.Handler.class, SPacketPieceData.class, id++, Side.CLIENT);
        NETWORK_WRAPPER.registerMessage(SPacketUnloadPiece.Handler.class, SPacketUnloadPiece.class, id++, Side.CLIENT);
    }
}
