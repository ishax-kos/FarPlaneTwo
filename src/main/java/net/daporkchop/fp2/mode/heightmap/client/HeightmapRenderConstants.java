/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 DaPorkchop_
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

package net.daporkchop.fp2.mode.heightmap.client;

import lombok.experimental.UtilityClass;
import net.daporkchop.lib.unsafe.PUnsafe;

import static net.daporkchop.fp2.client.gl.OpenGL.*;

/**
 * Constant values used throughout the heightmap render code.
 *
 * @author DaPorkchop_
 */
@UtilityClass
class HeightmapRenderConstants {
    //
    // off-heap structs layouts
    //TODO: figure out how to keep intellij from rearranging this area when reformatting
    //

    /*
     * struct Pos { // 12 bytes
     *   int tileX;
     *   int tileZ;
     *   int level;
     * };
     */

    public final long _POS_TILEX_OFFSET = 0L;
    public final long _POS_TILEZ_OFFSET = _POS_TILEX_OFFSET + INT_SIZE;
    public final long _POS_LEVEL_OFFSET = _POS_TILEZ_OFFSET + INT_SIZE;

    public final long _POS_SIZE = _POS_LEVEL_OFFSET + BYTE_SIZE;

    public int _pos_tileX(long pos) {
        return PUnsafe.getInt(pos + _POS_TILEX_OFFSET);
    }

    public void _pos_tileX(long pos, int tileX) {
        PUnsafe.putInt(pos + _POS_TILEX_OFFSET, tileX);
    }

    public int _pos_tileZ(long pos) {
        return PUnsafe.getInt(pos + _POS_TILEZ_OFFSET);
    }

    public void _pos_tileZ(long pos, int tileZ) {
        PUnsafe.putInt(pos + _POS_TILEZ_OFFSET, tileZ);
    }

    public int _pos_level(long pos) {
        return PUnsafe.getInt(pos + _POS_LEVEL_OFFSET);
    }

    public void _pos_level(long pos, int level) {
        PUnsafe.putInt(pos + _POS_LEVEL_OFFSET, level);
    }

    /*
     * struct Tile {
     *   Pos pos;
     *   RenderData renderData;
     * };
     */

    public final long _TILE_POS_OFFSET = 0L;
    public final long _TILE_RENDERDATA_OFFSET = _TILE_POS_OFFSET + _POS_SIZE;

    public long _tile_pos(long tile) {
        return tile + _TILE_POS_OFFSET;
    }

    public long _tile_renderData(long tile) {
        return tile + _TILE_RENDERDATA_OFFSET;
    }
}
