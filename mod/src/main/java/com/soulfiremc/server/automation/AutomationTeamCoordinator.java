/*
 * SoulFire
 * Copyright (C) 2026  AlexProgrammerDE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.soulfiremc.server.automation;

import com.soulfiremc.server.InstanceManager;
import com.soulfiremc.server.bot.BotConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class AutomationTeamCoordinator {
  private static final long BLOCK_EXPIRY_MILLIS = 15L * 60L * 1000L;
  private static final long BOT_EXPIRY_MILLIS = 5L * 60L * 1000L;
  private static final long CLAIM_EXPIRY_MILLIS = 45_000L;
  private static final long EYE_SAMPLE_EXPIRY_MILLIS = 10L * 60L * 1000L;
  private static final double MIN_STRONGHOLD_DISTANCE = 128.0;
  private static final double MAX_STRONGHOLD_DISTANCE = 30_000.0;

  private final InstanceManager instanceManager;
  private final Map<ResourceKey<Level>, Map<Long, SharedBlock>> sharedBlocks = new HashMap<>();
  private final Map<UUID, BotSnapshot> botSnapshots = new HashMap<>();
  private final Map<String, Claim> claims = new HashMap<>();
  private final List<EyeSample> eyeSamples = new ArrayList<>();

  public AutomationTeamCoordinator(InstanceManager instanceManager) {
    this.instanceManager = instanceManager;
  }

  public InstanceManager instanceManager() {
    return instanceManager;
  }

  public synchronized void tick() {
    prune(System.currentTimeMillis());
  }

  public synchronized void observe(BotConnection bot,
                                   AutomationWorldMemory worldMemory,
                                   String status,
                                   @Nullable String phase) {
    var player = bot.minecraft().player;
    var level = bot.minecraft().level;
    if (player == null || level == null) {
      return;
    }

    var now = System.currentTimeMillis();
    prune(now);

    var snapshot = botSnapshots.computeIfAbsent(bot.accountProfileId(), _ -> new BotSnapshot(bot.accountProfileId(), bot.accountName()));
    snapshot.observe(player.position(), level.dimension(), player.isAlive(), status, phase, now);

    var sharedDimensionBlocks = sharedBlocks.computeIfAbsent(level.dimension(), _ -> new HashMap<>());
    for (var block : worldMemory.rememberedBlocks()) {
      if (!isSharedInteresting(block.state())) {
        continue;
      }

      sharedDimensionBlocks.put(block.pos().asLong(), new SharedBlock(block.pos(), block.state(), now));
    }
  }

  public synchronized void noteProgress(BotConnection bot) {
    var snapshot = botSnapshots.get(bot.accountProfileId());
    if (snapshot != null) {
      snapshot.noteProgress(System.currentTimeMillis());
    }
  }

  public synchronized void releaseClaims(BotConnection bot) {
    var owner = bot.accountProfileId();
    claims.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
  }

  public synchronized void releaseBot(BotConnection bot) {
    releaseClaims(bot);
    botSnapshots.remove(bot.accountProfileId());
  }

  public synchronized Optional<SharedBlock> findNearestBlock(BotConnection bot,
                                                             ResourceKey<Level> dimension,
                                                             Predicate<BlockState> predicate) {
    return origin(bot).flatMap(origin -> findNearestBlock(origin, dimension, predicate));
  }

  public synchronized Optional<SharedBlock> findNearestBlock(Vec3 origin,
                                                             ResourceKey<Level> dimension,
                                                             Predicate<BlockState> predicate) {
    var now = System.currentTimeMillis();
    prune(now);

    return sharedBlocks.getOrDefault(dimension, Map.of())
      .values()
      .stream()
      .filter(block -> predicate.test(block.state()))
      .min((a, b) -> Double.compare(a.pos().distToCenterSqr(origin), b.pos().distToCenterSqr(origin)));
  }

  public synchronized Optional<SharedBlock> findNearestPortal(BotConnection bot, ResourceKey<Level> dimension) {
    return findNearestBlock(bot, dimension, state -> state.getBlock() == Blocks.NETHER_PORTAL);
  }

  public synchronized Optional<SharedBlock> findNearestFortressHint(BotConnection bot) {
    return findNearestBlock(bot, Level.NETHER, AutomationTeamCoordinator::isFortressMarker);
  }

  public synchronized boolean claimBlock(BotConnection bot,
                                         String purpose,
                                         ResourceKey<Level> dimension,
                                         BlockPos pos,
                                         long leaseMillis) {
    return claim(bot.accountProfileId(), "block:%s:%s:%d".formatted(purpose, dimension.identifier(), pos.asLong()), pos.getCenter(), leaseMillis);
  }

  public synchronized boolean claimEntity(BotConnection bot,
                                          String purpose,
                                          UUID entityId,
                                          long leaseMillis) {
    return claim(bot.accountProfileId(), "entity:%s:%s".formatted(purpose, entityId), null, leaseMillis);
  }

  public synchronized Vec3 assignExplorationTarget(BotConnection bot,
                                                   ResourceKey<Level> dimension,
                                                   String purpose,
                                                   Vec3 focus,
                                                   int spacing) {
    var now = System.currentTimeMillis();
    prune(now);

    var anchorX = floorToGrid(focus.x, spacing);
    var anchorZ = floorToGrid(focus.z, spacing);
    for (var offset : spiralOffsets(7)) {
      var dx = offset[0];
      var dz = offset[1];
      var target = new Vec3(anchorX + dx * spacing + 0.5, focus.y, anchorZ + dz * spacing + 0.5);
      var key = "explore:%s:%s:%d:%d:%d:%d".formatted(dimension.identifier(), purpose, anchorX, anchorZ, dx, dz);
      if (claim(bot.accountProfileId(), key, target, CLAIM_EXPIRY_MILLIS)) {
        return target;
      }
    }

    return focus;
  }

  public synchronized void reportEyeSample(BotConnection bot, Vec3 origin, Vec3 direction) {
    var flattened = new Vec3(direction.x, 0.0, direction.z);
    if (flattened.lengthSqr() < 1.0e-6) {
      return;
    }

    var now = System.currentTimeMillis();
    prune(now);

    eyeSamples.removeIf(sample ->
      sample.botId().equals(bot.accountProfileId())
        && sample.origin().distanceToSqr(origin) < 16 * 16);
    eyeSamples.add(new EyeSample(bot.accountProfileId(), origin, flattened.normalize(), now));
  }

  public synchronized Optional<Vec3> strongholdEstimate() {
    var now = System.currentTimeMillis();
    prune(now);

    var frames = sharedBlocks.getOrDefault(Level.OVERWORLD, Map.of())
      .values()
      .stream()
      .filter(block -> block.state().getBlock() == Blocks.END_PORTAL_FRAME || block.state().getBlock() == Blocks.END_PORTAL)
      .map(SharedBlock::pos)
      .toList();
    if (!frames.isEmpty()) {
      var sumX = 0.0;
      var sumY = 0.0;
      var sumZ = 0.0;
      for (var pos : frames) {
        sumX += pos.getX() + 0.5;
        sumY += pos.getY() + 0.5;
        sumZ += pos.getZ() + 0.5;
      }
      return Optional.of(new Vec3(sumX / frames.size(), sumY / frames.size(), sumZ / frames.size()));
    }

    if (eyeSamples.size() == 1) {
      var sample = eyeSamples.getFirst();
      return Optional.of(sample.origin().add(sample.direction().scale(768.0)));
    }

    var intersections = new ArrayList<Vec3>();
    for (int i = 0; i < eyeSamples.size(); i++) {
      for (int j = i + 1; j < eyeSamples.size(); j++) {
        var intersection = intersect(eyeSamples.get(i), eyeSamples.get(j));
        if (intersection != null) {
          intersections.add(intersection);
        }
      }
    }

    if (intersections.isEmpty()) {
      return Optional.empty();
    }

    var sumX = 0.0;
    var sumZ = 0.0;
    for (var intersection : intersections) {
      sumX += intersection.x;
      sumZ += intersection.z;
    }
    return Optional.of(new Vec3(sumX / intersections.size(), 32.0, sumZ / intersections.size()));
  }

  public synchronized Optional<Vec3> lastKnownDeathPosition(BotConnection bot) {
    return Optional.ofNullable(botSnapshots.get(bot.accountProfileId()))
      .flatMap(BotSnapshot::lastDeathPosition);
  }

  public synchronized int deathCount(BotConnection bot) {
    return Optional.ofNullable(botSnapshots.get(bot.accountProfileId()))
      .map(BotSnapshot::deathCount)
      .orElse(0);
  }

  public synchronized Collection<BotStatus> botStatuses() {
    return botSnapshots.values().stream()
      .map(snapshot -> new BotStatus(
        snapshot.botId(),
        snapshot.accountName(),
        snapshot.dimension,
        snapshot.position,
        snapshot.status,
        snapshot.phase,
        snapshot.deathCount,
        snapshot.lastProgressMillis))
      .toList();
  }

  private boolean claim(UUID owner, String key, @Nullable Vec3 target, long leaseMillis) {
    var now = System.currentTimeMillis();
    prune(now);

    var existing = claims.get(key);
    if (existing != null && existing.expiresAtMillis() > now && !existing.owner().equals(owner)) {
      return false;
    }

    claims.put(key, new Claim(key, owner, target, now + Math.max(5_000L, leaseMillis)));
    return true;
  }

  private Optional<Vec3> origin(BotConnection bot) {
    var player = bot.minecraft().player;
    if (player != null) {
      return Optional.of(player.position());
    }

    return Optional.ofNullable(botSnapshots.get(bot.accountProfileId()))
      .map(BotSnapshot::position);
  }

  private void prune(long now) {
    sharedBlocks.values().forEach(dimensionBlocks ->
      dimensionBlocks.values().removeIf(block -> now - block.lastSeenMillis() > BLOCK_EXPIRY_MILLIS));
    sharedBlocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    botSnapshots.values().removeIf(snapshot -> now - snapshot.lastSeenMillis > BOT_EXPIRY_MILLIS);
    claims.values().removeIf(claim -> claim.expiresAtMillis() <= now);
    eyeSamples.removeIf(sample -> now - sample.recordedAtMillis() > EYE_SAMPLE_EXPIRY_MILLIS);
  }

  private static boolean isSharedInteresting(BlockState state) {
    var block = state.getBlock();
    return AutomationWorldMemory.isInterestingBlock(state)
      || block == Blocks.OBSIDIAN
      || block == Blocks.CRYING_OBSIDIAN
      || block == Blocks.GOLD_BLOCK
      || block == Blocks.NETHERRACK
      || block == Blocks.MAGMA_BLOCK
      || block == Blocks.NETHER_BRICKS
      || block == Blocks.NETHER_BRICK_FENCE
      || block == Blocks.NETHER_BRICK_STAIRS
      || block == Blocks.NETHER_WART_BLOCK
      || block == Blocks.SPAWNER
      || block == Blocks.BEDROCK
      || block == Blocks.DRAGON_EGG
      || block == Blocks.END_STONE;
  }

  private static boolean isFortressMarker(BlockState state) {
    var block = state.getBlock();
    return block == Blocks.SPAWNER
      || block == Blocks.NETHER_BRICKS
      || block == Blocks.NETHER_BRICK_FENCE
      || block == Blocks.NETHER_BRICK_STAIRS;
  }

  private static int floorToGrid(double value, int spacing) {
    return Math.floorDiv((int) Math.floor(value), spacing) * spacing;
  }

  private static List<int[]> spiralOffsets(int maxRadius) {
    var offsets = new ArrayList<int[]>();
    offsets.add(new int[]{0, 0});
    for (int radius = 1; radius <= maxRadius; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        offsets.add(new int[]{dx, -radius});
        offsets.add(new int[]{dx, radius});
      }
      for (int dz = -radius + 1; dz <= radius - 1; dz++) {
        offsets.add(new int[]{-radius, dz});
        offsets.add(new int[]{radius, dz});
      }
    }
    return offsets;
  }

  private static @Nullable Vec3 intersect(EyeSample first, EyeSample second) {
    var det = first.direction().x * second.direction().z - first.direction().z * second.direction().x;
    if (Math.abs(det) < 0.15) {
      return null;
    }

    var delta = second.origin().subtract(first.origin());
    var firstDistance = (delta.x * second.direction().z - delta.z * second.direction().x) / det;
    var secondDistance = (delta.x * first.direction().z - delta.z * first.direction().x) / det;
    if (firstDistance < 0.0 || secondDistance < 0.0) {
      return null;
    }

    var intersection = first.origin().add(first.direction().scale(firstDistance));
    var distance = intersection.distanceTo(first.origin());
    if (distance < MIN_STRONGHOLD_DISTANCE || distance > MAX_STRONGHOLD_DISTANCE) {
      return null;
    }

    return new Vec3(intersection.x, 32.0, intersection.z);
  }

  public record SharedBlock(BlockPos pos, BlockState state, long lastSeenMillis) {
  }

  public record BotStatus(UUID botId,
                          String accountName,
                          @Nullable ResourceKey<Level> dimension,
                          @Nullable Vec3 position,
                          String status,
                          @Nullable String phase,
                          int deathCount,
                          long lastProgressMillis) {
  }

  private record Claim(String key, UUID owner, @Nullable Vec3 target, long expiresAtMillis) {
  }

  private record EyeSample(UUID botId, Vec3 origin, Vec3 direction, long recordedAtMillis) {
  }

  private static final class BotSnapshot {
    private final UUID botId;
    private final String accountName;
    private @Nullable ResourceKey<Level> dimension;
    private @Nullable Vec3 position;
    private @Nullable Vec3 lastDeathPosition;
    private String status = "idle";
    private @Nullable String phase;
    private boolean alive = true;
    private int deathCount;
    private long lastSeenMillis;
    private long lastProgressMillis;

    private BotSnapshot(UUID botId, String accountName) {
      this.botId = botId;
      this.accountName = accountName;
    }

    private void observe(Vec3 position,
                         ResourceKey<Level> dimension,
                         boolean alive,
                         String status,
                         @Nullable String phase,
                         long now) {
      if (this.alive && !alive) {
        deathCount++;
        lastDeathPosition = position;
      }

      this.position = position;
      this.dimension = dimension;
      this.alive = alive;
      this.status = status;
      this.phase = phase;
      this.lastSeenMillis = now;
      if (lastProgressMillis == 0L) {
        lastProgressMillis = now;
      }
    }

    private void noteProgress(long now) {
      lastProgressMillis = now;
    }

    private UUID botId() {
      return botId;
    }

    private String accountName() {
      return accountName;
    }

    private @Nullable Vec3 position() {
      return position;
    }

    private Optional<Vec3> lastDeathPosition() {
      return Optional.ofNullable(lastDeathPosition);
    }

    private int deathCount() {
      return deathCount;
    }
  }
}
