package dev.lazurite.rayon.core.impl.physics.space;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import dev.lazurite.rayon.core.api.PhysicsElement;
import dev.lazurite.rayon.core.api.event.ElementCollisionEvents;
import dev.lazurite.rayon.core.api.event.PhysicsSpaceEvents;
import dev.lazurite.rayon.core.impl.physics.space.body.BlockRigidBody;
import dev.lazurite.rayon.core.impl.physics.space.body.ElementRigidBody;
import dev.lazurite.rayon.core.impl.physics.space.body.MinecraftRigidBody;
import dev.lazurite.rayon.core.impl.physics.space.environment.EntityComponent;
import dev.lazurite.rayon.core.impl.physics.space.environment.FluidComponent;
import dev.lazurite.rayon.core.impl.physics.space.environment.TerrainComponent;
import dev.lazurite.rayon.core.impl.physics.space.environment.WorldComponent;
import dev.lazurite.rayon.core.impl.util.Clock;
import dev.lazurite.rayon.core.impl.util.SpaceStorage;
import dev.lazurite.rayon.core.impl.physics.PhysicsThread;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * This is the main physics simulation used by Rayon. It loops using the {@link MinecraftSpace#step} method
 * where all {@link WorldComponent} objects are applied just before queuing the actual simulation step
 * as an asynchronous task. This way, the only thing that runs asynchronously is bullet itself. All of the setup,
 * input, or otherwise user defined behavior happens on the game logic thread.
 * <br><br>
 * It is also worth noting that another
 * simulation step will not be performed if the last step has taken longer than 50ms and is still executing upon the
 * next tick. This really only happens if you are dealing with an ungodly amount of rigid bodies or your computer is
 * simply a :tiny_potato:
 * @see PhysicsThread
 * @see PhysicsSpaceEvents
 */
public class MinecraftSpace extends PhysicsSpace implements PhysicsCollisionListener {
    private static final int MAX_PRESIM_STEPS = 10;

    private final List<WorldComponent> worldComponents;
    private final PhysicsThread thread;
    private final World world;
    private final Clock clock;
    private int presimSteps;

    private volatile boolean stepping;

    /**
     * Allows users to retrieve the {@link MinecraftSpace} associated
     * with any given {@link World} object (client or server).
     * @param world the world to get the physics space from
     * @return the {@link MinecraftSpace}
     */
    public static MinecraftSpace get(World world) {
        return ((SpaceStorage) world).getSpace();
    }

    public MinecraftSpace(PhysicsThread thread, World world, BroadphaseType broadphase) {
        super(broadphase);
        this.thread = thread;
        this.world = world;
        this.clock = new Clock();
        this.worldComponents = new ArrayList<>();
        this.addCollisionListener(this);
        this.setGravity(new Vector3f(0, -9.807f, 0)); // m/s/s

        this.addWorldComponent(new EntityComponent());
        this.addWorldComponent(new FluidComponent());
        this.addWorldComponent(new TerrainComponent());
    }

    public MinecraftSpace(PhysicsThread thread, World world) {
        this(thread, world, BroadphaseType.DBVT);
    }

    /**
     * This method performs the following steps:
     * <ul>
     *     <li>Fires world step events in {@link PhysicsSpaceEvents}.</li>
     *     <li>Steps {@link ElementRigidBody}s.</li>
     *     <li>Applies air drag force to {@link MinecraftRigidBody}s.</li>
     *     <li>Loads blocks into the simulation around {@link MinecraftRigidBody}s using {@link TerrainComponent}.</li>
     *     <li>Steps the simulation asynchronously.</li>
     *     <li>Triggers all collision events.</li>
     * </ul>
     *
     * Additionally, none of the above steps execute when either the world is empty
     * (no {@link PhysicsRigidBody}s) or when the game is paused.
     *
     * @param shouldStep whether or not to fully step the simulation
     * @see TerrainComponent
     * @see PhysicsSpaceEvents
     */
    public void step(BooleanSupplier shouldStep) {
        getRigidBodiesByClass(ElementRigidBody.class).forEach(ElementRigidBody::updateFrame);

        System.out.println("IS STEPP: " + stepping);

        if (shouldStep.getAsBoolean()) {
            this.stepping = true;

            /* World Step Event */
            PhysicsSpaceEvents.STEP.invoker().onStep(this);

            /* Step all elements */
            getRigidBodiesByClass(ElementRigidBody.class).forEach(rigidBody -> rigidBody.getElement().step(this));

            /* Apply all world components */
            getWorldComponents().forEach(worldComponent -> worldComponent.apply(this));

            /* Step Simulation Asynchronously */
            CompletableFuture.runAsync(() -> {
                if (presimSteps > MAX_PRESIM_STEPS) {
                    update(clock.getAndReset(), 5);
                } else ++presimSteps;
            }, getWorkerThread()).thenRunAsync(() -> {
                this.distributeEvents();
                this.stepping = false;
            }, getWorkerThread().getParentExecutor());
        }
    }

    public void addWorldComponent(WorldComponent worldComponent) {
        this.worldComponents.add(worldComponent);
    }

    public List<WorldComponent> getWorldComponents() {
        return new ArrayList<>(worldComponents);
    }

    public void addPhysicsElement(PhysicsElement element) {
        var rigidBody = element.getRigidBody();

        if (!rigidBody.isInWorld()) {
            element.reset();
            addCollisionObject(rigidBody);
        }

        rigidBody.activate();
    }

    public void removePhysicsElement(PhysicsElement element) {
        if (element.getRigidBody().isInWorld()) {
            removeCollisionObject(element.getRigidBody());
        }
    }

    public boolean isServer() {
        return getWorkerThread().getParentExecutor() instanceof MinecraftServer;
    }

    public boolean isStepping() {
        return this.stepping;
    }

    public boolean canStep() {
        return !isStepping() && (isInPresim() || !isEmpty());
    }

    public boolean isInPresim() {
        return presimSteps < MAX_PRESIM_STEPS;
    }

    public <T> List<T> getRigidBodiesByClass(Class<T> type) {
        var out = new ArrayList<T>();

        for (var body : getRigidBodyList()) {
            if (type.isAssignableFrom(body.getClass())) {
                out.add(type.cast(body));
            }
        }

        return out;
    }

    public PhysicsThread getWorkerThread() {
        return this.thread;
    }

    public World getWorld() {
        return this.world;
    }

    /**
     * Trigger all collision events (e.g. block/element or element/element).
     * @param event the event context
     */
    @Override
    public void collision(PhysicsCollisionEvent event) {
        float impulse = event.getAppliedImpulse();

        /* Element on Element */
        if (event.getObjectA() instanceof ElementRigidBody rigidBodyA && event.getObjectB() instanceof ElementRigidBody rigidBodyB) {
            ElementCollisionEvents.ELEMENT_COLLISION.invoker().onCollide(rigidBodyA.getElement(), rigidBodyB.getElement(), impulse);

        /* Block on Element */
        } else if (event.getObjectA() instanceof BlockRigidBody block && event.getObjectB() instanceof ElementRigidBody rigidBody) {
            ElementCollisionEvents.BLOCK_COLLISION.invoker().onCollide(rigidBody.getElement(), block, impulse);

        /* Element on Block */
        } else if (event.getObjectA() instanceof ElementRigidBody rigidBody && event.getObjectB() instanceof BlockRigidBody block) {
            ElementCollisionEvents.BLOCK_COLLISION.invoker().onCollide(rigidBody.getElement(), block, impulse);
        }
    }
}