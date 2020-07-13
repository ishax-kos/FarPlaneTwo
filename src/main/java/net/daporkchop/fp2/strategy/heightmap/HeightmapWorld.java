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
import io.netty.buffer.PooledByteBufAllocator;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.fp2.strategy.RenderStrategy;
import net.daporkchop.fp2.strategy.common.IFarPiecePos;
import net.daporkchop.fp2.strategy.common.IFarWorld;
import net.daporkchop.fp2.strategy.heightmap.vanilla.VanillaHeightmapGenerator;
import net.daporkchop.fp2.util.threading.CachedBlockAccess;
import net.daporkchop.lib.binary.netty.PUnpooled;
import net.daporkchop.lib.common.function.io.IORunnable;
import net.daporkchop.lib.common.function.io.IOSupplier;
import net.daporkchop.lib.common.math.BinMath;
import net.daporkchop.lib.common.ref.Ref;
import net.daporkchop.lib.common.ref.ThreadRef;
import net.daporkchop.lib.compression.zstd.Zstd;
import net.daporkchop.lib.compression.zstd.ZstdDeflater;
import net.daporkchop.lib.compression.zstd.ZstdInflater;
import net.daporkchop.lib.primitive.map.LongObjMap;
import net.daporkchop.lib.primitive.map.concurrent.LongObjConcurrentHashMap;
import net.minecraft.world.WorldServer;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

import static net.daporkchop.fp2.server.ServerConstants.*;
import static net.daporkchop.fp2.strategy.heightmap.HeightmapConstants.*;
import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * Representation of a world used by the heightmap rendering strategy.
 *
 * @author DaPorkchop_
 */
@Accessors(fluent = true)
public class HeightmapWorld implements IFarWorld {
    private static final Ref<ZstdDeflater> DEFLATER_CACHE = ThreadRef.soft(() -> Zstd.PROVIDER.deflater(Zstd.PROVIDER.deflateOptions()));
    private static final Ref<ZstdInflater> INFLATER_CACHE = ThreadRef.soft(() -> Zstd.PROVIDER.inflater(Zstd.PROVIDER.inflateOptions()));

    @Getter
    protected final WorldServer world;
    @Getter
    protected final HeightmapGenerator generator;

    protected final Path storageRoot;

    protected final LongObjMap<CompletableFuture<HeightmapPiece>> cache = new LongObjConcurrentHashMap<>();

    public HeightmapWorld(@NonNull WorldServer world) {
        this.world = world;
        this.generator = new VanillaHeightmapGenerator();
        this.generator.init(world);

        this.storageRoot = world.getChunkSaveLocation().toPath().resolve("fp2/" + RenderStrategy.FLAT.name().toLowerCase());
    }

    @Override
    public HeightmapPiece getPieceBlocking(@NonNull IFarPiecePos posIn) {
        HeightmapPiecePos pos = (HeightmapPiecePos) posIn;
        return this.loadFullPiece(BinMath.packXY(pos.x(), pos.z())).join();
    }

    @Override
    public HeightmapPiece getPieceNowOrLoadAsync(@NonNull IFarPiecePos posIn) {
        HeightmapPiecePos pos = (HeightmapPiecePos) posIn;
        return this.loadFullPiece(BinMath.packXY(pos.x(), pos.z())).getNow(null);
    }

    @Override
    public void blockChanged(int x, int y, int z) {
    }

    protected CompletableFuture<HeightmapPiece> loadFullPiece(long key) {
        return this.cache.computeIfAbsent(key, l -> {
            int x = BinMath.unpackX(l);
            int z = BinMath.unpackY(l);
            Path cachePath = this.storageRoot.resolve(String.format("%d/%d.%d.fp2", 0, x, z));

            return CompletableFuture
                    .supplyAsync((IOSupplier<HeightmapPiece>) () -> {
                        //load piece if possible
                        if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
                            try (FileChannel channel = FileChannel.open(cachePath, StandardOpenOption.READ)) {
                                ByteBuf input = PUnpooled.wrap(channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size()), false);
                                if (input.readableBytes() >= 8 && input.readInt() == HEIGHT_STORAGE_VERSION) {
                                    ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(input.readInt());
                                    try {
                                        checkState(INFLATER_CACHE.get().decompress(input, buf));
                                        return new HeightmapPiece(buf);
                                    } finally {
                                        buf.release();
                                    }
                                }
                            }
                        }
                        return null;
                    }, IO_WORKERS)
                    .handleAsync((pieceIn, e) -> {
                        if (e != null) {
                            e.printStackTrace();
                        } else if (pieceIn != null) {
                            return pieceIn;
                        }

                        //generate piece
                        CachedBlockAccess world = ((CachedBlockAccess.Holder) this.world).fp2_cachedBlockAccess();
                        HeightmapPiece piece = new HeightmapPiece(x, z);
                        piece.writeLock().lock();
                        try {
                            this.generator.generateRough(world, piece);
                        } finally {
                            piece.writeLock().unlock();
                        }

                        //the piece was newly generated, so enqueue it to be saved
                        IO_WORKERS.submit((IORunnable) () -> {
                            Files.createDirectories(cachePath.getParent());
                            ByteBuf raw = PooledByteBufAllocator.DEFAULT.ioBuffer();
                            ByteBuf compressed = PooledByteBufAllocator.DEFAULT.ioBuffer();
                            try (FileChannel channel = FileChannel.open(cachePath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                                piece.readLock().lock();
                                try {
                                    piece.write(raw);
                                } finally {
                                    piece.readLock().unlock();
                                }
                                compressed.ensureWritable(8 + Zstd.PROVIDER.compressBound(raw.readableBytes()));
                                compressed.writeInt(HEIGHT_STORAGE_VERSION).writeInt(raw.readableBytes());
                                checkState(DEFLATER_CACHE.get().compress(raw, compressed));
                                compressed.readBytes(channel, compressed.readableBytes());
                                checkState(!compressed.isReadable());
                            } finally {
                                raw.release();
                                compressed.release();
                            }
                        });

                        return piece;
                    }, GENERATION_WORKERS);
        });
    }
}
