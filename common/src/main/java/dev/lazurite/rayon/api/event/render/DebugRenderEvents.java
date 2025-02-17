package dev.lazurite.rayon.api.event.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lazurite.rayon.impl.util.debug.CollisionObjectDebugger;
import dev.lazurite.rayon.impl.bullet.collision.space.MinecraftSpace;
import dev.lazurite.toolbox.api.event.Event;
import net.minecraft.world.phys.Vec3;

/**
 * The events available through this class are:
 * <ul>
 *     <li><b>Before Render:</b> Called before each frame of the {@link CollisionObjectDebugger}</li>
 * </ul>
 * @since 1.3.0
 */
public class DebugRenderEvents {
    public static final Event<BeforeRender> BEFORE_RENDER = Event.create();

    private DebugRenderEvents() { }

    @FunctionalInterface
    public interface BeforeRender {
        void onRender(Context context);
    }

    public record Context(MinecraftSpace space, VertexConsumer vertices, PoseStack matrices, Vec3 cameraPos, float tickDelta) { }
}

