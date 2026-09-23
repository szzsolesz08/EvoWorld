package dev.evoworld.core.world;

import dev.evoworld.core.resource.ResourceNode;
import java.util.List;

/**
 * What is in a chunk of the world that nobody has visited yet.
 *
 * <p>The seam between "the world exists" and "the world is generated". A
 * {@link World} with a terrain fills a chunk in the moment an agent comes near
 * enough to see it; a world without one is a fixed map, filled in advance.
 *
 * <p>An implementation must be a <em>function</em> of the chunk: asked twice,
 * in any order, at any point in a run, it answers the same. That is what makes
 * a world endless and still the same world every time it is played.
 */
@FunctionalInterface
public interface Terrain {

    List<ResourceNode> generate(Chunk chunk);
}
