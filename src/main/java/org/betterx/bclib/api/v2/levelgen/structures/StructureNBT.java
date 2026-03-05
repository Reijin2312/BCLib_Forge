package org.betterx.bclib.api.v2.levelgen.structures;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.util.BlocksHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import com.google.common.collect.Maps;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class StructureNBT {
    public final ResourceLocation location;
    protected StructureTemplate structure;


    protected StructureNBT(ResourceLocation location) {
        this.location = location;
        this.structure = null;
    }

    protected StructureNBT(ResourceLocation location, StructureTemplate structure) {
        this.location = location;
        this.structure = structure;
    }

    public static Rotation getRandomRotation(RandomSource random) {
        return Rotation.getRandom(random);
    }

    public static Mirror getRandomMirror(RandomSource random) {
        return Mirror.values()[random.nextInt(3)];
    }

    private static final Map<ResourceLocation, StructureNBT> STRUCTURE_CACHE = Maps.newHashMap();

    public static StructureNBT create(ResourceLocation location) {
        return STRUCTURE_CACHE.computeIfAbsent(location, r -> new StructureNBT(r));
    }

    public boolean generateCentered(ServerLevelAccessor world, BlockPos pos, Rotation rotation, Mirror mirror) {
        StructureTemplate structure = getStructure(world);
        BlockPos newPos = getCenteredPos(pos, rotation, mirror, structure);
        if (newPos == null) return false;
        StructurePlaceSettings data = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        structure.placeInWorld(
                world,
                newPos,
                newPos,
                data,
                world.getRandom(),
                BlocksHelper.SET_SILENT
        );
        return true;
    }

    public boolean generateAt(ServerLevelAccessor world, BlockPos pos, Rotation rotation, Mirror mirror) {
        StructureTemplate structure = getStructure(world);
        if (structure == null) return false;
        StructurePlaceSettings data = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        structure.placeInWorld(
                world,
                pos,
                pos,
                data,
                world.getRandom(),
                BlocksHelper.SET_SILENT
        );
        return true;
    }

    @Nullable
    private BlockPos getCenteredPos(BlockPos pos, Rotation rotation, Mirror mirror) {
        return getCenteredPos(pos, rotation, mirror, getStructure());
    }

    @Nullable
    private BlockPos getCenteredPos(
            BlockPos pos,
            Rotation rotation,
            Mirror mirror,
            @Nullable StructureTemplate structure
    ) {
        if (structure == null) {
            BCLib.LOGGER.error("No structure: " + location.toString());
            return null;
        }

        MutableBlockPos blockpos2 = new MutableBlockPos().set(structure.getSize());
        if (mirror == Mirror.FRONT_BACK)
            blockpos2.setX(-blockpos2.getX());
        if (mirror == Mirror.LEFT_RIGHT)
            blockpos2.setZ(-blockpos2.getZ());
        blockpos2.set(blockpos2.rotate(rotation));
        return pos.offset(-blockpos2.getX() >> 1, 0, -blockpos2.getZ() >> 1);
    }

    private static final Map<ResourceLocation, StructureTemplate> READER_CACHE = Maps.newHashMap();

    private static StructureTemplate readStructureFromJar(ResourceLocation resource) {
        if (READER_CACHE.containsKey(resource)) {
            return READER_CACHE.get(resource);
        }
        StructureTemplate template = _readStructureFromJar(resource);
        READER_CACHE.put(resource, template);
        return template;
    }

    private static String getStructurePath(ResourceLocation resource) {
        return "data/" + resource.getNamespace() + "/structures/" + resource.getPath();
    }

    private static StructureTemplate _readStructureFromJar(ResourceLocation resource) {
        try {
            String resourcePath = getStructurePath(resource) + ".nbt";
            InputStream inputstream = StructureNBT.class.getResourceAsStream("/" + resourcePath);
            if (inputstream == null) {
                ClassLoader classLoader = StructureNBT.class.getClassLoader();
                if (classLoader != null) {
                    inputstream = classLoader.getResourceAsStream(resourcePath);
                }
            }
            if (inputstream == null) {
                ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
                if (contextLoader != null) {
                    inputstream = contextLoader.getResourceAsStream(resourcePath);
                }
            }
            if (inputstream == null) {
                if (BCLib.isDatagen()) {
                    BCLib.LOGGER.warning("Missing structure during datagen: " + resource);
                    return new StructureTemplate();
                }
                BCLib.LOGGER.error("Missing structure: " + resource);
                return null;
            }
            return readStructureFromStream(inputstream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    protected synchronized StructureTemplate getStructure() {
        if (structure == null) {
            structure = readStructureFromJar(location);
        }
        return structure;
    }

    @Nullable
    protected synchronized StructureTemplate getStructure(ServerLevelAccessor world) {
        if (structure == null) {
            structure = world.getLevel().getStructureManager().getOrCreate(location);
            if (structure == null) {
                structure = readStructureFromJar(location);
            }
        }
        return structure;
    }

    private static BoundingBox emptyBox(BlockPos pos) {
        return new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Returns a list of all structures found at the given resource location.
     *
     * @param resource       The resource location to search.
     * @param recursionDepth The maximum recursion depth or 0 to indicate no limitation
     * @return A list of all structures found at the given resource location.
     */
    public static List<StructureNBT> createResourcesFrom(ResourceLocation resource, int recursionDepth) {
        String ns = resource.getNamespace();
        String nm = resource.getPath();

        final String resourceFolder = getStructurePath(resource);
        final URL url = MinecraftServer.class.getClassLoader().getResource(resourceFolder);
        if (url != null) {
            final URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                BCLib.LOGGER.error("Unable to load Resources: ", e);
                return null;
            }
            Path myPath;
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = null;
                try {
                    fileSystem = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException notLoaded) {
                    try {
                        fileSystem = FileSystems.newFileSystem(uri, new HashMap<>());
                    } catch (IOException e) {
                        BCLib.LOGGER.error("Unable to load Filesystem: ", e);
                        return null;
                    }
                }

                myPath = fileSystem.getPath(resourceFolder);
            } else {
                myPath = Paths.get(uri);
            }
            if (Files.isDirectory(myPath)) {
                try {
                    // /bclib place nbt minecraft:village/plains 0 southOf 0 -60 -0 controller
                    return Files.walk(myPath, recursionDepth <= 0 ? Integer.MAX_VALUE : recursionDepth)
                                .filter(p -> Files.isRegularFile(p))
                                .map(p -> {
                                    if (p.isAbsolute())
                                        return Path.of(uri).relativize(p).toString();
                                    else {
                                        return p.toString().replace(resourceFolder, "").replaceAll("^/+", "");
                                    }
                                })
                                .filter(s -> s.endsWith(".nbt"))
                                .map(s -> new ResourceLocation(
                                        ns,
                                        (nm.isEmpty() ? "" : (nm + "/")) + s.substring(0, s.length() - 4)
                                ))
                                .sorted(Comparator.comparing(ResourceLocation::toString))
                                .map(r -> {
                                    BCLib.LOGGER.info("Loading Structure: " + r);
                                    try {
                                        return StructureNBT.create(r);
                                    } catch (Exception e) {
                                        BCLib.LOGGER.error("Unable to load Structure " + r, e);
                                    }
                                    return null;
                                })
                                .toList();
                } catch (IOException e) {
                    BCLib.LOGGER.error("Unable to load Resources: ", e);
                    return null;
                }
            }
        }
        return null;
    }

    private static StructureTemplate readStructureFromStream(InputStream stream) throws IOException {
        if (stream == null) {
            return new StructureTemplate();
        }
        CompoundTag nbttagcompound = NbtIo.readCompressed(stream);

        StructureTemplate template = new StructureTemplate();

        template.load(BuiltInRegistries.BLOCK.asLookup(), nbttagcompound);

        return template;
    }

    public BlockPos getSize(Rotation rotation) {
        StructureTemplate structure = getStructure();
        if (structure == null) {
            return BlockPos.ZERO;
        }
        if (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180)
            return new BlockPos(structure.getSize());
        else {
            Vec3i size = structure.getSize();
            int x = size.getX();
            int z = size.getZ();
            return new BlockPos(z, size.getY(), x);
        }
    }

    public String getName() {
        return location.getPath();
    }

    public BoundingBox getBoundingBox(BlockPos pos, Rotation rotation, Mirror mirror) {
        StructureTemplate structure = getStructure();
        if (structure == null) {
            return emptyBox(pos);
        }
        return structure.getBoundingBox(new StructurePlaceSettings().setRotation(rotation).setMirror(mirror), pos);
    }

    public BoundingBox getCenteredBoundingBox(BlockPos pos, Rotation rotation, Mirror mirror) {
        StructureTemplate structure = getStructure();
        BlockPos centeredPos = getCenteredPos(pos, rotation, mirror, structure);
        if (structure == null || centeredPos == null) {
            return emptyBox(pos);
        }
        return structure.getBoundingBox(
                new StructurePlaceSettings().setRotation(rotation).setMirror(mirror),
                centeredPos
        );
    }
}
