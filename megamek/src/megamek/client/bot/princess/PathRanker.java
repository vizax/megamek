/*
 * MegaMek - Copyright (C) 2000-2011 Ben Mazur (bmazur@sev.org)
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.client.bot.princess;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import megamek.client.ui.SharedUtility;
import megamek.common.Aero;
import megamek.common.Infantry;
import megamek.common.Building;
import megamek.common.Compute;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.IGame;
import megamek.common.MovePath;
import megamek.common.MoveStep;
import megamek.common.TargetRoll;
import megamek.common.Targetable;
import megamek.common.logging.LogLevel;
import megamek.common.options.OptionsConstants;
import megamek.common.util.StringUtil;

public abstract class PathRanker {

    private Princess owner;

    public PathRanker(Princess princess) {
        owner = princess;
    }

    /**
     * Gives the "utility" of a path; a number representing how good it is.
     * Rankers that extend this class should override this function
     */
    public RankedPath rankPath(MovePath path, IGame game) {
        double fallTolerance = getOwner().getBehaviorSettings().getFallShameIndex() / 10d;
        Entity me = path.getEntity();
        int homeDistance = distanceToHomeEdge(me.getPosition(), getOwner().getHomeEdge(), game);
        int maxWeaponRange = me.getMaxWeaponRange();
        List<Entity> enemies = getOwner().getEnemyEntities();
        List<Entity> friends = getOwner().getFriendEntities();
        Coords allyCenter = calcAllyCenter(me.getId(), friends, game);

        return rankPath(path, game, maxWeaponRange, fallTolerance, homeDistance, enemies, allyCenter);
    }

    abstract RankedPath rankPath(MovePath path, IGame game, int maxRange, double fallTolerance, int distanceHome,
                               List<Entity> enemies, Coords friendsCoords);

    public ArrayList<RankedPath> rankPaths(List<MovePath> movePaths, IGame game, int maxRange,
                                           double fallTollerance, int startingHomeDistance,
                                           List<Entity> enemies, List<Entity> friends) {
        final String METHOD_NAME = "rankPaths(ArrayList<MovePath>, IGame)"; //$NON-NLS-1$
        getOwner().methodBegin(getClass(), METHOD_NAME);

        try {
            // No point in ranking an empty list.
            if (movePaths.isEmpty()) {
                return new ArrayList<>();
            }

            // Let's try to whittle down this list.
            List<MovePath> validPaths = validatePaths(movePaths, game, maxRange, fallTollerance, startingHomeDistance);
            getOwner().log(getClass(), METHOD_NAME, LogLevel.DEBUG, Messages.getString("PathRanker.BotValidated") + validPaths.size() + Messages.getString("PathRanker.BotOutOf") + //$NON-NLS-1$ //$NON-NLS-2$
                                                               movePaths.size() + Messages.getString("PathRanker.BotPossiblePaths")); //$NON-NLS-1$

            Coords allyCenter = calcAllyCenter(movePaths.get(0).getEntity().getId(), friends, game);

            ArrayList<RankedPath> returnPaths = new ArrayList<>(validPaths.size());
            final BigDecimal numberPaths = new BigDecimal(validPaths.size());
            BigDecimal count = BigDecimal.ZERO;
            BigDecimal interval = new BigDecimal(5);
            for (MovePath path : validPaths) {
                count = count.add(BigDecimal.ONE);
                returnPaths.add(rankPath(path, game, maxRange, fallTollerance, startingHomeDistance, enemies,
                                         allyCenter));
                BigDecimal percent = count.divide(numberPaths, 2, RoundingMode.DOWN).multiply(new BigDecimal(100))
                                          .round(new MathContext(0, RoundingMode.DOWN));
                if ((percent.compareTo(interval) >= 0)
                    && (LogLevel.INFO.getLevel() <= getOwner().getVerbosity().getLevel())) {
                    getOwner().sendChat(Messages.getString("PathRanker.BotElipse") + percent.intValue() + Messages.getString("PathRanker.BotPercentComplete")); //$NON-NLS-1$ //$NON-NLS-2$
                    interval = percent.add(new BigDecimal(5));
                }
            }
            return returnPaths;
        } finally {
            getOwner().methodEnd(getClass(), METHOD_NAME);
        }
    }

    private List<MovePath> validatePaths(List<MovePath> startingPathList, IGame game, int maxRange,
                                         double fallTolerance, int startingHomeDistance) {
        final String METHOD_NAME = "validatePaths(List<MovePath>, IGame, Targetable, int, double, int, int)"; //$NON-NLS-1$
        LogLevel logLevel = LogLevel.DEBUG;

        if (startingPathList.isEmpty()) {
            // Nothing to validate here, might as well return the empty list
            // straight away.
            return startingPathList;
        }

        Entity mover = startingPathList.get(0).getEntity();

        // No support yet for Aero units.
        if (mover instanceof Aero) {
            return startingPathList;
        }

        Targetable closestTarget = findClosestEnemy(mover, mover.getPosition(), game);
        int startingTargetDistance = (closestTarget == null ?
                                      Integer.MAX_VALUE :
                                      closestTarget.getPosition().distance(mover.getPosition()));

        List<MovePath> returnPaths = new ArrayList<>(startingPathList.size());
        boolean inRange = (maxRange >= startingTargetDistance);
        HomeEdge homeEdge = getOwner().getHomeEdge();
        boolean fleeing = getOwner().isFallingBack(mover);
        //Infantry with zero move or with field guns cannot move and shoot, so we want to take that into account for path ranking.
        boolean isZeroMoveInfantry = mover instanceof Infantry && (mover.getWalkMP() == 0 || ((Infantry)mover).hasFieldGun());
        if (isZeroMoveInfantry) {
            startingPathList.add(new MovePath(game, mover)); //If we can't move and still fire, we want to consider not moving.
        }

        for (MovePath path : startingPathList) {
            StringBuilder msg = new StringBuilder("Validating Path: ").append(path.toString()); //$NON-NLS-1$

            try {
                Coords finalCoords = path.getFinalCoords();

                // If fleeing, skip any paths that don't get me closer to home.
                if (fleeing && (distanceToHomeEdge(finalCoords, homeEdge, game) >= startingHomeDistance)) {
                    logLevel = LogLevel.INFO;
                    msg.append(Messages.getString("PathRanker.BotRunningWrongDirection")); //$NON-NLS-1$
                    continue;
                }

                // Make sure I'm trying to get/stay in range of a target.
                Targetable closestToEnd = findClosestEnemy(mover, finalCoords, game);
                String validation = validRange(finalCoords, closestToEnd, startingTargetDistance, maxRange, inRange);
                if (!StringUtil.isNullOrEmpty(validation)) {
                    msg.append(Messages.getString("PathRanker.9")).append(validation); //$NON-NLS-1$
                    continue;
                }

                // Don't move on/through buildings that will not support our weight.
                if (willBuildingCollapse(path, game)) {
                    logLevel = LogLevel.INFO;
                    msg.append(Messages.getString("PathRanker.BotBuildingWillCollapse")); //$NON-NLS-1$
                    continue;
                }

                // Skip any path where I am too likely to fail my piloting roll.
                double chance = getMovePathSuccessProbability(path, msg);
                if (chance < fallTolerance) {
                    logLevel = LogLevel.INFO;
                    msg.append(Messages.getString("PathRanker.BotLikelyFacePlant")); //$NON-NLS-1$
                    continue;
                }

                // If all the above checks have passed, this is a valid path.
                msg.append(Messages.getString("PathRanker.BotValid")); //$NON-NLS-1$
                returnPaths.add(path);

            } finally {
                getOwner().log(getClass(), METHOD_NAME, logLevel, msg.toString());
            }
        }

        // If we've eliminated all valid paths, we'll just have to pick from the best of the invalid paths.
        if (returnPaths.isEmpty()) {
            return startingPathList;
        }

        return returnPaths;
    }

    public RankedPath getBestPath(List<RankedPath> ps) {
        final String METHOD_NAME = "getBestPath(ArrayList<Rankedpath>)"; //$NON-NLS-1$
        getOwner().methodBegin(PathRanker.class, METHOD_NAME);

        try {
            if (ps.size() == 0) {
                return null;
            }
            return Collections.max(ps);
        } finally {
            getOwner().methodEnd(PathRanker.class, METHOD_NAME);
        }
    }


    /**
     * Performs initialization to help speed later calls of rankPath for this
     * unit on this turn. Rankers that extend this class should override this
     * function
     */
    public void initUnitTurn(Entity unit, IGame game) {
    }

    /**
     * Find the closest enemy to a unit with a path
     */
    Entity findClosestEnemy(Entity me, Coords position, IGame game) {
        final String METHOD_NAME = "findClosestEnemy(Entity, Coords, IGame)"; //$NON-NLS-1$
        getOwner().methodBegin(PathRanker.class, METHOD_NAME);

        try {
            int range = 9999;
            Entity closest = null;
            List<Entity> enemies = getOwner().getEnemyEntities();
            for (Entity e : enemies) {
                // Skip airborne aero units as they're further away than they seem and hard to catch.
                if (e instanceof Aero && e.isAirborne()) {
                    continue;
                }

                // If a unit has not moved, assume it will move away from me.
                int unmovedDistMod = 0;
                if (e.isSelectableThisTurn() && !e.isImmobile()) {
                    unmovedDistMod = e.getWalkMP(true, false, false);
                }

                if ((position.distance(e.getPosition()) + unmovedDistMod) < range) {
                    range = position.distance(e.getPosition());
                    closest = e;
                }
            }
            return closest;
        } finally {
            getOwner().methodEnd(PathRanker.class, METHOD_NAME);
        }
    }

    /**
     * Returns the probability of success of a movepath
     */
    public double getMovePathSuccessProbability(MovePath movePath, StringBuilder msg) {
        MovePath pathCopy = movePath.clone();
        List<TargetRoll> pilotingRolls = getPSRList(pathCopy);
        double successProbability = 1.0;
        msg.append(Messages.getString("PathRanker.BotCalcMovePathSuccess")); //$NON-NLS-1$
        for (TargetRoll roll : pilotingRolls) {

            // Skip the getting up check.  That's handled when checking for being immobile.
            if (roll.getDesc().toLowerCase().contains("getting up")) { //$NON-NLS-1$
                continue;
            }
            if (roll.getDesc().toLowerCase().contains("careful stand")) { //$NON-NLS-1$
                continue;
            }
            boolean naturalAptPilot = movePath.getEntity().getCrew().getOptions()
                                              .booleanOption(OptionsConstants.PILOT_APTITUDE_GUNNERY);
            if (naturalAptPilot) {
                msg.append(Messages.getString("PathRanker.BotPilotHasNatAptPiloting")); //$NON-NLS-1$
            }

            msg.append(Messages.getString("PathRanker.BotRoll")).append(roll.getDesc()).append(" ").append(roll.getValue()); //$NON-NLS-1$ //$NON-NLS-2$
            double odds = Compute.oddsAbove(roll.getValue(), naturalAptPilot) / 100;
            msg.append(" (").append(NumberFormat.getPercentInstance().format(odds)).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
            successProbability *= odds;
        }

        // Account for MASC
        if (pathCopy.hasActiveMASC()) {
            msg.append(Messages.getString("PathRanker.BotMasc")); //$NON-NLS-1$
            int target = pathCopy.getEntity().getMASCTarget();
            msg.append(target);
            // todo Does Natural Aptitude Piloting apply to this?  I assume not.
            double odds = Compute.oddsAbove(target) / 100;
            msg.append(" (").append(NumberFormat.getPercentInstance().format(odds)).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
            successProbability *= odds;
        }
        msg.append(Messages.getString("PathRanker.BotTotal")).append(NumberFormat.getPercentInstance().format(successProbability)); //$NON-NLS-1$

        return successProbability;
    }

    protected List<TargetRoll> getPSRList(MovePath path) {
        return SharedUtility.getPSRList(path);
    }

    public int distanceToHomeEdge(Coords position, HomeEdge homeEdge, IGame game) {
        final String METHOD_NAME = "distanceToHomeEdge(Coords, HomeEdge, IGame)"; //$NON-NLS-1$
        getOwner().methodBegin(getClass(), METHOD_NAME);

        try {
            Coords edgeCoords;
            int boardHeight = game.getBoard().getHeight();
            int boardWidth = game.getBoard().getWidth();
            StringBuilder msg = new StringBuilder(Messages.getString("PathRanker.BotGettingHomeEdge")); //$NON-NLS-1$
            if (HomeEdge.NORTH.equals(homeEdge)) {
                msg.append(Messages.getString("PathRanker.BotNorth")); //$NON-NLS-1$
                edgeCoords = new Coords(position.getX(), 0);
            } else if (HomeEdge.SOUTH.equals(homeEdge)) {
                msg.append(Messages.getString("PathRanker.BotSouth")); //$NON-NLS-1$
                edgeCoords = new Coords(position.getX(), boardHeight);
            } else if (HomeEdge.WEST.equals(homeEdge)) {
                msg.append(Messages.getString("PathRanker.BotWest")); //$NON-NLS-1$
                edgeCoords = new Coords(0, position.getY());
            } else if (HomeEdge.EAST.equals(homeEdge)) {
                msg.append(Messages.getString("PathRanker.BotEast")); //$NON-NLS-1$
                edgeCoords = new Coords(boardWidth, position.getY());
            } else {
                msg.append(Messages.getString("PathRanker.BotDefault")); //$NON-NLS-1$
                getOwner().log(getClass(), METHOD_NAME, LogLevel.WARNING, Messages.getString("PathRanker.BotInvalidHomeEdgeDefaultNorth")); //$NON-NLS-1$
                edgeCoords = new Coords(boardWidth / 2, 0);
            }
            msg.append(edgeCoords.toFriendlyString());

            int distance = edgeCoords.distance(position);
            msg.append(Messages.getString("PathRanker.BotDistanceEquals")).append(NumberFormat.getInstance().format(distance)); //$NON-NLS-1$

            getOwner().log(getClass(), METHOD_NAME, LogLevel.DEBUG, msg.toString());
            return distance;
        } finally {
            getOwner().methodEnd(getClass(), METHOD_NAME);
        }
    }

    private String validRange(Coords finalCoords, Targetable target, int startingTargetDistance, int maxRange,
                              boolean inRange) {
        if (target == null) {
            return null;
        }

        // If I am not currently in range, discard any path that takes me further away from my target.
        int finalDistanceToTarget = finalCoords.distance(target.getPosition());
        if (!inRange) {
            if (finalDistanceToTarget > startingTargetDistance) {
                return Messages.getString("PathRanker.BotNotInRangeMovingAway"); //$NON-NLS-1$
            }

        } else { // If I am in range, discard any path that takes me out of range.
            if (finalDistanceToTarget > maxRange) {
                return Messages.getString("PathRanker.BotInRangeMovingOut"); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Check the path being moved to see if there is a danger of building collapse.  Allows a margin of error of 10
     * tons in case someone decides to shoot at the building.  If jumping, only the landing point is checked.  For
     * all other move types, the entire path is checked.
     * todo reread the rules on basement collapse
     * todo skip basement check if random basement option is turned off
     * todo incorporate test for building damage just from moving through building
     *
     * @param path The {@link MovePath} being traversed.
     * @param game The {@link IGame} being played.
     * @return True if there is a building in our path that might collapse.
     */
    protected boolean willBuildingCollapse(MovePath path, IGame game) {
        // If we're jumping onto a building, make sure it can support our weight.
        if (path.isJumping()) {
            Coords finalCoords = path.getFinalCoords();
            Building building = game.getBoard().getBuildingAt(finalCoords);
            if (building == null) {
                return false;
            }

            // Give ourselves a 10-ton margin of error in case someone shoots at the building.
            float mass = path.getEntity().getWeight() + 10;
            return (mass > building.getCurrentCF(finalCoords));
        }

        // If we're not jumping, check each building to see if it will collapse if it has a basement.
        float mass = path.getEntity().getWeight() + 10;
        Enumeration<MoveStep> steps = path.getSteps();
        while (steps.hasMoreElements()) {
            MoveStep step = steps.nextElement();
            Building building = game.getBoard().getBuildingAt(step.getPosition());
            if (building == null) {
                continue;
            }

            if (mass > building.getCurrentCF(step.getPosition())) {
                return true;
            }
        }
        return false;
    }

    public Coords calcAllyCenter(int myId, List<Entity> friends, IGame game) {
        if ((friends == null) || friends.isEmpty()) {
            return null;
        }

        int xTotal = 0;
        int yTotal = 0;
        int friendOnBoardCount = 0;

        for (Entity friend : friends) {
            if (friend.getId() == myId) {
                continue;
            }

            // Skip any friends not on the board.
            if (friend.isOffBoard()) {
                continue;
            }
            Coords friendPosition = friend.getPosition();
            if ((friendPosition == null) || !game.getBoard().contains(friendPosition)) {
                continue;
            }

            xTotal += friendPosition.getX();
            yTotal += friendPosition.getY();
            friendOnBoardCount++;
        }

        if (friendOnBoardCount == 0) {
            return null;
        }

        int xCenter = Math.round(xTotal / friendOnBoardCount);
        int yCenter = Math.round(yTotal / friendOnBoardCount);
        Coords center = new Coords(xCenter, yCenter);

        if (!game.getBoard().contains(center)) {
            getOwner().log(getClass(), "calcAllyCenter(int, List<Entity>, IGame)", LogLevel.ERROR, Messages.getString("PathRanker.BotCenterOfAllyGroup") + //$NON-NLS-1$ //$NON-NLS-2$
                                                                                              center.toFriendlyString() + Messages.getString("PathRanker.BotNotWithinBoard")); //$NON-NLS-1$
            return null;
        }

        return center;
    }

    protected Princess getOwner() {
        return owner;
    }
}
