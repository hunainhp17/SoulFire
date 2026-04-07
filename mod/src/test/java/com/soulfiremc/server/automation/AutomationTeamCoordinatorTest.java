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

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomationTeamCoordinatorTest {
  @Test
  void explorationOffsetsSkipTheCurrentCell() {
    var offsets = AutomationTeamCoordinator.explorationOffsets(2);

    assertEquals(24, offsets.size());
    assertTrue(offsets.stream().noneMatch(offset -> offset[0] == 0 && offset[1] == 0));
  }

  @Test
  void explorationFallbackMovesToAnotherColumn() {
    var focus = new Vec3(0.2, 46.0, 0.7);
    var firstOffset = AutomationTeamCoordinator.explorationOffsets(1).getFirst();
    var target = AutomationTeamCoordinator.explorationTargetForOffset(0, 0, focus, 80, firstOffset[0], firstOffset[1]);

    assertTrue((int) Math.floor(target.x) != (int) Math.floor(focus.x)
      || (int) Math.floor(target.z) != (int) Math.floor(focus.z));
  }
}
