package dev.lazurite.rayon.api;

import dev.lazurite.rayon.impl.bullet.collision.body.EntityRigidBody;
import dev.lazurite.rayon.impl.bullet.collision.body.shape.MinecraftShape;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Use this interface to create a physics entity.
 * @see PhysicsElement
 */
public interface EntityPhysicsElement extends PhysicsElement<Entity> {
    static boolean is(Entity entity) {
        return entity instanceof EntityPhysicsElement element && element.getRigidBody() != null;
    }

    static EntityPhysicsElement get(Entity entity) {
        return (EntityPhysicsElement) entity;
    }

    @Override @Nullable
    EntityRigidBody getRigidBody();

    @Override
    default MinecraftShape.Convex createShape() {
        final var box = cast().getBoundingBox();
        return MinecraftShape.convex(box.contract(box.getXsize() * 0.25, box.getYsize() * 0.25, box.getZsize() * 0.25));
    }

    default boolean skipVanillaEntityCollisions() {
        return false;
    }
}
