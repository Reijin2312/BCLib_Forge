package org.betterx.bclib.api.v3.datagen;

import org.betterx.bclib.util.Pair;
import org.betterx.worlds.together.tag.v3.TagRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;

import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

public class TagDataProvider<T> extends TagsProvider<T> {
    @Nullable
    protected final List<String> modIDs;

    protected final TagRegistry<T> tagRegistry;

    private final Set<TagKey<T>> forceWrite;

    /**
     * Constructs a new {@link TagsProvider} with the default computed path.
     *
     * <p>Common implementations of this class are provided.
     *
     * @param tagRegistry
     * @param modIDs           List of ModIDs that are allowed to inlcude data. All Resources in the namespace of the
     *                         mod will be written to the tag. If null all elements get written, and empty list will
     *                         write nothing
     * @param output           the {@link PackOutput} instance
     * @param registriesFuture the backing registry for the tag type
     * @param existingFileHelper the existing file helper
     */
    public TagDataProvider(
            TagRegistry<T> tagRegistry,
            @Nullable List<String> modIDs,
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture,
            @Nullable ExistingFileHelper existingFileHelper
    ) {
        this(tagRegistry, modIDs, Set.of(), output, registriesFuture, existingFileHelper);
    }

    /**
     * Constructs a new {@link TagsProvider} with the default computed path.
     *
     * <p>Common implementations of this class are provided.
     *
     * @param tagRegistry
     * @param modIDs           List of ModIDs that are allowed to inlcude data. All Resources in the namespace of the
     *                         mod will be written to the tag. If null all elements get written, and empty list will
     *                         write nothing
     * @param forceWriteKeys   the keys that should allways get written
     * @param output           the {@link PackOutput} instance
     * @param registriesFuture the backing registry for the tag type
     * @param existingFileHelper the existing file helper
     */
    public TagDataProvider(
            TagRegistry<T> tagRegistry,
            @Nullable List<String> modIDs,
            Set<TagKey<T>> forceWriteKeys,
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture,
            @Nullable ExistingFileHelper existingFileHelper
    ) {
        super(output, tagRegistry.registryKey, registriesFuture, resolveModId(modIDs), existingFileHelper);
        this.tagRegistry = tagRegistry;
        this.modIDs = modIDs;
        this.forceWrite = forceWriteKeys;
    }

    protected boolean shouldAdd(ResourceLocation loc) {
        return modIDs == null || modIDs.contains(loc.getNamespace());
    }

    protected boolean isOptional(TagEntry entry) {
        return entry.verifyIfPresent(id -> false, id -> false);
    }

    @Override
    protected void addTags(HolderLookup.Provider arg) {
        final Map<TagKey<T>, List<Pair<ResourceLocation, TagEntry>>> elementsByTag = new HashMap<>();
        final Map<TagKey<T>, List<Pair<TagKey<T>, TagEntry>>> tagsByTag = new HashMap<>();

        tagRegistry.forEachEntry((tag, locs, tags) -> {
            if (!forceWrite.contains(tag) && locs.isEmpty() && tags.isEmpty()) return;
            elementsByTag.put(tag, locs);
            tagsByTag.put(tag, tags);
        }, (tag, loc) -> forceWrite.contains(tag) || shouldAdd(tag.location()) || this.shouldAdd(loc));

        final Set<TagKey<T>> written = new HashSet<>(elementsByTag.keySet());
        tagsByTag.values().forEach(tags -> tags.forEach(pair -> {
            ResourceLocation id = pair.first.location();
            if (!isOptional(pair.second)
                    && !ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())
                    && shouldAdd(id)) {
                written.add(pair.first);
            }
        }));

        for (TagKey<T> tag : written) {
            final TagsProvider.TagAppender<T> builder = tag(tag);
            elementsByTag.getOrDefault(tag, List.of()).stream()
                         .sorted(Comparator.comparing(a -> a.first.toString()))
                         .forEach(pair -> builder.add(pair.second));
            tagsByTag.getOrDefault(tag, List.of()).stream()
                     .sorted(Comparator.comparing(a -> a.first.location().toString()))
                     .forEach(pair -> builder.add(
                             isOptional(pair.second) || !isKnownTag(pair.first, written)
                                     ? TagEntry.optionalTag(pair.first.location())
                                     : pair.second
                     ));
        }
    }

    protected boolean isKnownTag(TagKey<T> tag, Set<TagKey<T>> written) {
        return written.contains(tag) || ResourceLocation.DEFAULT_NAMESPACE.equals(tag.location().getNamespace());
    }

    private static String resolveModId(@Nullable List<String> modIDs) {
        if (modIDs != null && !modIDs.isEmpty()) {
            return modIDs.get(0);
        }
        return "bclib";
    }
}
