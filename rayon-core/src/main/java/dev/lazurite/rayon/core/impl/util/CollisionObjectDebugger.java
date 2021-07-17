package dev.lazurite.rayon.core.impl.util;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lazurite.rayon.core.api.event.render.DebugRenderEvents;
import dev.lazurite.rayon.core.impl.bullet.collision.space.MinecraftSpace;
import dev.lazurite.rayon.core.impl.bullet.collision.body.MinecraftRigidBody;
import dev.lazurite.rayon.core.impl.bullet.math.Converter;
import dev.lazurite.rayon.core.impl.mixin.client.KeyboardMixin;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;

/**
 * This class handles debug rendering on the client. Press F3+r to render
 * all {@link MinecraftRigidBody} objects present in the {@link MinecraftSpace}.
 * @see KeyboardMixin
 */
@Environment(EnvType.CLIENT)
public final class CollisionObjectDebugger {
    private static final CollisionObjectDebugger instance = new CollisionObjectDebugger();
    private boolean enabled;

    public static CollisionObjectDebugger getInstance() {
        return instance;
    }

    private CollisionObjectDebugger() {
    }

    public boolean toggle() {
        this.enabled = !this.enabled;
        return this.enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void renderSpace(MinecraftSpace space, float tickDelta) {
        var cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        var builder = Tessellator.getInstance().getBuffer();
        var stack = new MatrixStack();

        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        DebugRenderEvents.BEFORE_RENDER.invoker().onRender(new DebugRenderEvents.Context(space, builder, stack, cameraPos, tickDelta));

        for (var body : space.getRigidBodiesByClass(MinecraftRigidBody.class)) {
            var points = body.getCollisionShape().getTriangles();
            var color = body.getOutlineColor();
            var alpha = body.getOutlineAlpha();

            var position = body.isStatic() ?
                    body.getPhysicsLocation(new Vector3f()).subtract(Converter.toBullet(cameraPos)) :
                    body.getFrame().getLocation(new Vector3f(), tickDelta).subtract(Converter.toBullet(cameraPos));

            var rotation = body.isStatic() ?
                    body.getPhysicsRotation(new Quaternion()) :
                    body.getFrame().getRotation(new Quaternion(), tickDelta);

            stack.push();
            stack.translate(position.x, position.y, position.z);
            stack.multiply(Converter.toMinecraft(rotation));

            for (var point : points) {
                builder.vertex(stack.peek().getModel(), point.x, point.y, point.z)
                        .color(color.x, color.y, color.z, alpha).next();
            }

            stack.pop();
        }

        Tessellator.getInstance().draw();
    }
}