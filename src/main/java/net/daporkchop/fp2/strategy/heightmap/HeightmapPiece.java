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

package net.daporkchop.fp2.strategy.heightmap;

import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.daporkchop.fp2.strategy.RenderMode;
import net.daporkchop.fp2.strategy.base.AbstractFarPiece;
import net.daporkchop.fp2.strategy.common.IFarPiece;
import net.daporkchop.fp2.util.Constants;
import net.daporkchop.lib.common.misc.string.PStrings;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

import java.nio.IntBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static net.daporkchop.fp2.util.Constants.T_VOXELS;
import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * A "piece" containing the data used by the heightmap rendering mode.
 *
 * @author DaPorkchop_
 */
@Getter
public class HeightmapPiece extends AbstractFarPiece<HeightmapPos> {
    public static final int ENTRY_SIZE = 4;
    public static final int ENTRY_COUNT = T_VOXELS * T_VOXELS;

    public static final int TOTAL_SIZE = ENTRY_COUNT * ENTRY_SIZE;

    private static int index(int x, int z) {
        checkArg(x >= 0 && x < T_VOXELS && z >= 0 && z < T_VOXELS, "coordinates out of bounds (x=%d, z=%d)", x, z);
        return (x * T_VOXELS + z) * 4;
    }

    protected final IntBuffer data = Constants.createIntBuffer(ENTRY_COUNT * 4);

    public HeightmapPiece(@NonNull HeightmapPos pos) {
        super(pos, RenderMode.HEIGHTMAP);
    }

    public HeightmapPiece(@NonNull ByteBuf src) {
        super(src, RenderMode.HEIGHTMAP);
    }

    @Override
    protected void readBody(@NonNull ByteBuf src) {
        for (int i = 0; i < TOTAL_SIZE; i++) {
            this.data.put(i, src.readInt());
        }
    }

    @Override
    protected void writeBody(@NonNull ByteBuf dst) {
        for (int i = 0; i < TOTAL_SIZE; i++) {
            dst.writeInt(this.data.get(i));
        }
    }

    public int height(int x, int z) {
        return this.data.get(index(x, z) + 0);
    }

    public int block(int x, int z) {
        return this.data.get(index(x, z) + 1) & 0x00FFFFFF;
    }

    public int light(int x, int z) {
        return this.data.get(index(x, z) + 1) >>> 24;
    }

    public int biome(int x, int z) {
        return this.data.get(index(x, z) + 2) & 0xFF;
    }

    public int waterLight(int x, int z) {
        return (this.data.get(index(x, z) + 2) >>> 8) & 0xFF;
    }

    public int waterBiome(int x, int z) {
        return (this.data.get(index(x, z) + 2) >>> 16) & 0xFF;
    }

    public HeightmapPiece set(int x, int z, int height, IBlockState state, int light, Biome biome, int waterLight, Biome waterBiome) {
        return this.set(x, z, height, Block.getStateId(state), light, Biome.getIdForBiome(biome), waterLight, Biome.getIdForBiome(waterBiome));
    }

    public HeightmapPiece set(int x, int z, int height, int state, int light, int biome, int waterLight, int waterBiome) {
        int base = index(x, z);

        this.data.put(base + 0, height)
                .put(base + 1, (light << 24) | state)
                .put(base + 2, (waterBiome << 16) | (waterLight << 8) | biome)
                .put(base + 3, 0);
        this.markDirty();
        return this;
    }

    public void copy(int srcX, int srcZ, HeightmapPiece src, int x, int z)  {
        int srcBase = index(srcX, srcZ);
        int base = index(x, z);
        for (int i = 0; i < 4; i++) {
            this.data.put(base + i, src.data.get(srcBase + i));
        }
    }
}
