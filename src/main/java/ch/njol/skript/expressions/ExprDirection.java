/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011-2013 Peter Güttinger
 * 
 */

package ch.njol.skript.expressions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Direction;
import ch.njol.util.Kleenean;
import ch.njol.util.Math2;

/**
 * @author Peter Güttinger
 */
@SuppressWarnings("serial")
@Name("Direction")
@Description("A helper expression for the <a href='../classes/#direction'>direction type</a>.")
@Examples({"thrust the player upwards",
		"set the block behind the player to water",
		"loop blocks above the player:",
		"	set {_rand} to a random integer between 1 and 10",
		"	set the block {_rand} meters south east of the loop-block to stone",
		"block in horizontal facing of the clicked entity from the player is air",
		"spawn a creeper 1.5 meters horizontally behind the player",
		"spawn a TNT 5 meters above and 2 meters horizontally behind the player",
		"thrust the last spawned TNT in the horizontal direction of the player with speed 0.2",
		"push the player upwards and horizontally forward at speed 0.5",
		"push the clicked entity in in the direction of the player at speed -0.5",
		"open the inventory of the block 2 blocks below the player to the player",
		"teleport the clicked entity behind the player",
		"grow a regular tree 2 meters horizontally behind the player"})
@Since("1.0 (basic), 2.0 (extended)")
public class ExprDirection extends SimpleExpression<Direction> {
	static {
		// TODO think about parsing statically & dynamically (also in general)
		// "at": see LitAt
		Skript.registerExpression(ExprDirection.class, Direction.class, ExpressionType.COMBINED,
				"[%-number% [(block|meter)[s]] [to the]] (" +
						"2¦north[(-| |)(6¦east|7¦west)][(ward(s|ly|)|er(n|ly|))] [of]" +
						"|3¦south[(-| |)(8¦east|9¦west)][(ward(s|ly|)|er(n|ly|))] [of]" +
						"|(4¦east|5¦west)[(ward(s|ly|)|er(n|ly|))] [of]" +
						"|0¦above|0¦over|(0¦up|1¦down)[ward(s|ly|)]|1¦below|1¦under[neath]|1¦beneath" +
						") [%-direction%]",
				"[%-number% [(block|meter)[s]]] in [the] (0¦direction|1¦horizontal direction|2¦facing|3¦horizontal facing) of %entity/block% (of|from|)",
				"[%-number% [(block|meter)[s]]] in %entity/block%'[s] (0¦direction|1¦horizontal direction|2¦facing|3¦horizontal facing) (of|from|)",
				"[%-number% [(block|meter)[s]]] (0¦in[ ]front [of]|0¦forward[s]|2¦behind|2¦backwards|[to the] (1¦right|-1¦left) [of])",
				"[%-number% [(block|meter)[s]]] horizontal[ly] (0¦in[ ]front [of]|0¦forward[s]|2¦behind|2¦backwards|to the (1¦right|-1¦left) [of])");
	}
	
	private final static BlockFace[] byMark = new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
			BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST};
	
	private Expression<Number> amount;
	
	private Vector direction;
	private ExprDirection next;
	
	private Expression<?> relativeTo;
	boolean horizontal;
	boolean facing;
	
	private double yaw;
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
		amount = (Expression<Number>) exprs[0];
		switch (matchedPattern) {
			case 0:
				direction = new Vector(byMark[parseResult.mark].getModX(), byMark[parseResult.mark].getModY(), byMark[parseResult.mark].getModZ());
				if (exprs[1] != null) {
					if (!(exprs[1] instanceof ExprDirection) || ((ExprDirection) exprs[1]).direction == null)
						return false;
					next = (ExprDirection) exprs[1];
				}
				break;
			case 1:
			case 2:
				relativeTo = exprs[1];
				horizontal = parseResult.mark % 2 != 0;
				facing = parseResult.mark >= 2;
				break;
			case 3:
			case 4:
				yaw = Math.PI / 2 * parseResult.mark;
				horizontal = matchedPattern == 4;
		}
		return true;
	}
	
	@Override
	protected Direction[] get(final Event e) {
		final Number n = amount == null ? 1 : amount.getSingle(e);
		if (n == null)
			return null;
		final double ln = n.doubleValue();
		if (direction != null) {
			final Vector v = direction.clone().multiply(ln);
			ExprDirection d = next;
			while (d != null) {
				final Number n2 = d.amount == null ? 1 : d.amount.getSingle(e);
				if (n2 == null)
					return null;
				v.add(d.direction.clone().multiply(n2.doubleValue()));
				d = d.next;
			}
			return new Direction[] {new Direction(v)};
		} else if (relativeTo != null) {
			final Object o = relativeTo.getSingle(e);
			if (o == null)
				return null;
			if (o instanceof Block) {
				final BlockFace f = Direction.getFacing((Block) o);
				if (f == BlockFace.SELF || horizontal && (f == BlockFace.UP || f == BlockFace.DOWN))
					return new Direction[] {Direction.ZERO};
				return new Direction[] {new Direction(f, ln)};
			} else {
				final Location l = ((Entity) o).getLocation();
				if (!horizontal) {
					if (!facing)
						return new Direction[] {new Direction(l.getDirection().normalize().multiply(ln))};
					final double pitch = Direction.pitchToRadians(l.getPitch());
					assert pitch >= -Math.PI / 2 && pitch <= Math.PI / 2;
					if (pitch > Math.PI / 4)
						return new Direction[] {new Direction(new double[] {0, ln, 0})};
					if (pitch < -Math.PI / 4)
						return new Direction[] {new Direction(new double[] {0, -ln, 0})};
				}
				double yaw = Direction.yawToRadians(l.getYaw());
				if (horizontal && !facing) {
					return new Direction[] {new Direction(new double[] {Math.cos(yaw) * ln, 0, Math.sin(yaw) * ln})};
				}
				yaw = Math2.mod(yaw, 2 * Math.PI);
				if (yaw >= Math.PI / 4 && yaw < 3 * Math.PI / 4)
					return new Direction[] {new Direction(new double[] {0, 0, ln})};
				if (yaw >= 3 * Math.PI / 4 && yaw < 5 * Math.PI / 4)
					return new Direction[] {new Direction(new double[] {-ln, 0, 0})};
				if (yaw >= 5 * Math.PI / 4 && yaw < 7 * Math.PI / 4)
					return new Direction[] {new Direction(new double[] {0, 0, -ln})};
				assert yaw >= 0 && yaw < Math.PI / 4 || yaw >= 7 * Math.PI / 4 && yaw < 2 * Math.PI;
				return new Direction[] {new Direction(new double[] {ln, 0, 0})};
			}
		} else {
			return new Direction[] {new Direction(horizontal ? Direction.IGNORE_PITCH : 0, yaw, ln)};
		}
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
	@Override
	public Class<? extends Direction> getReturnType() {
		return Direction.class;
	}
	
	@Override
	public boolean getAnd() {
		return false;
	}
	
	@Override
	public String toString(final Event e, final boolean debug) {
		return (amount == null ? "" : amount.toString(e, debug) + " meter(s) ") + (direction != null ? Direction.toString(direction) :
				relativeTo != null ? " in " + (horizontal ? "horizontal " : "") + (facing ? "facing" : "direction") + " of " + relativeTo.toString(e, debug) :
						(horizontal ? "horizontally " : "") + Direction.toString(0, yaw, 1));
	}
	
}