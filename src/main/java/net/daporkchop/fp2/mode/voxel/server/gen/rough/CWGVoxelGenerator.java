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

package net.daporkchop.fp2.mode.voxel.server.gen.rough;

import lombok.NonNull;
import net.daporkchop.fp2.mode.api.server.gen.IFarGeneratorRough;
import net.daporkchop.fp2.mode.voxel.VoxelData;
import net.daporkchop.fp2.mode.voxel.VoxelPos;
import net.daporkchop.fp2.mode.voxel.piece.VoxelPieceBuilder;
import net.daporkchop.fp2.mode.voxel.server.gen.AbstractVoxelGenerator;
import net.daporkchop.lib.noise.NoiseSource;
import net.daporkchop.lib.noise.engine.PerlinNoiseEngine;
import net.daporkchop.lib.random.impl.FastPRandom;
import net.minecraft.world.WorldServer;

import java.util.Arrays;

/**
 * @author DaPorkchop_
 */
public class CWGVoxelGenerator extends AbstractVoxelGenerator<Void> implements IFarGeneratorRough<VoxelPos, VoxelPieceBuilder> {
    //protected Ref<CWGContext> ctx;
    protected NoiseSource noise;

    @Override
    public void init(@NonNull WorldServer world) {
        super.init(world);
        //this.ctx = ThreadRef.soft(() -> new CWGContext(world, 1, 2));

        this.noise = new PerlinNoiseEngine(new FastPRandom(world.getSeed())).scaled(1.0d / 16.0d);
    }

    @Override
    public void generate(@NonNull VoxelPos pos, @NonNull VoxelPieceBuilder piece) {
        int level = pos.level();
        int baseX = pos.blockX();
        int baseY = pos.blockY();
        int baseZ = pos.blockZ();

        //CWGContext ctx = this.ctx.get();
        //ctx.init(baseX >> 4, baseY >> 4, baseZ >> 4, level);
        double[][] densityMap = DMAP_CACHE.get();

        for (int x = DMAP_MIN; x < DMAP_MAX; x++) {
            for (int y = DMAP_MIN; y < DMAP_MAX; y++) {
                for (int z = DMAP_MIN; z < DMAP_MAX; z++) {
                    densityMap[0][densityIndex(x, y, z)] = baseY + y <= this.seaLevel ? -1.0d : 1.0d;
                }
            }
        }
        this.noise.get(densityMap[1], baseX + DMAP_MIN, baseY + DMAP_MIN, baseZ + DMAP_MIN, 1.0d, 1.0d, 1.0d, DMAP_SIZE, DMAP_SIZE, DMAP_SIZE);

        this.buildMesh(baseX, baseY, baseZ, level, piece, densityMap, null);
    }

    @Override
    protected void populateVoxelBlockData(int blockX, int blockY, int blockZ, VoxelData data, Void param, double nx, double ny, double nz) {
    }

    @Override
    public boolean supportsLowResolution() {
        return true;
    }

    @Override
    public boolean isLowResolutionInaccurate() {
        return true;
    }
}
