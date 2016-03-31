/*
 * MegaMek -
 * Copyright (C) 2007 Ben Mazur (bmazur@sev.org)
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
package megamek.client.bot;

import java.util.Enumeration;
import java.util.StringTokenizer;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.client.bot.princess.BehaviorSettingsFactory;
import megamek.client.bot.princess.ChatCommands;
import megamek.client.bot.princess.Princess;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.IGame;
import megamek.common.IPlayer;
import megamek.common.event.GamePlayerChatEvent;
import megamek.common.logging.LogLevel;
import megamek.common.util.StringUtil;
import megamek.server.Server;
import megamek.server.commands.DefeatCommand;
import megamek.server.commands.JoinTeamCommand;

public class ChatProcessor {

    protected boolean shouldBotAcknowledgeDefeat(String message, BotClient bot) {
        boolean result = false;
        if (!StringUtil.isNullOrEmpty(message) &&
            (message.contains("declares individual victory at the end of the turn.") //$NON-NLS-1$
             || message.contains("declares team victory at the end of the turn."))) { //$NON-NLS-1$
            String[] splitMessage = message.split(" "); //$NON-NLS-1$
            int i = 1;
            String name = splitMessage[i];
            while (!splitMessage[i + 1].equals("declares")) { //$NON-NLS-1$
                name += " " + splitMessage[i + 1]; //$NON-NLS-1$
                i++;
            }
            for (IPlayer p : bot.getGame().getPlayersVector()) {
                if (p.getName().equals(name)) {
                    if (p.isEnemyOf(bot.getLocalPlayer())) {
                        bot.sendChat("/defeat"); //$NON-NLS-1$
                        result = true;
                    }
                    break;
                }
            }
        }
        return result;
    }

    protected boolean shouldBotAcknowledgeVictory(String message, BotClient bot) {
        boolean result = false;

        if (!StringUtil.isNullOrEmpty(message) &&
            (message.contains(DefeatCommand.wantsDefeat))) {
            String[] splitMessage = message.split(" "); //$NON-NLS-1$
            int i = 1;
            String name = splitMessage[i];
            while (!splitMessage[i + 1].equals("wants") //$NON-NLS-1$
                   && !splitMessage[i + 1].equals("admits")) { //$NON-NLS-1$
                name += " " + splitMessage[i + 1]; //$NON-NLS-1$
                i++;
            }
            for (IPlayer p : bot.getGame().getPlayersVector()) {
                if (p.getName().equals(name)) {
                    if (p.isEnemyOf(bot.getLocalPlayer())) {
                        bot.sendChat("/victory"); //$NON-NLS-1$
                        result = true;
                    }
                    break;
                }
            }
        }

        return result;
    }

    public void processChat(GamePlayerChatEvent ge, BotClient bot) {
        if (bot.getLocalPlayer() == null) {
            return;
        }

        String message = ge.getMessage();
        if (shouldBotAcknowledgeDefeat(message, bot)) {
            return;
        }
        if (shouldBotAcknowledgeVictory(message, bot)) {
            return;
        }

        // Check for end of message.
        StringTokenizer st = new StringTokenizer(ge.getMessage(), ":"); //$NON-NLS-1$
        if (!st.hasMoreTokens()) {
            return;
        }
        String name = st.nextToken().trim();
        // who is the message from?
        Enumeration<IPlayer> e = bot.getGame().getPlayers();
        IPlayer p = null;
        while (e.hasMoreElements()) {
            p = e.nextElement();
            if (name.equalsIgnoreCase(p.getName())) {
                break;
            }
        }
        if (name.equals(Server.ORIGIN)) {
            String msg = st.nextToken();
            if (msg.contains(JoinTeamCommand.SERVER_VOTE_PROMPT_MSG)) {
                bot.sendChat("/allowTeamChange"); //$NON-NLS-1$
            }
            return;
        } else if (p == null) {
            return;
        }
        if (bot instanceof TestBot) {
            additionalTestBotCommands(st, (TestBot) bot, p);
        } else if (bot instanceof Princess) {
            additionalPrincessCommands(ge, (Princess) bot);
        }
    }

    private void additionalTestBotCommands(StringTokenizer st, TestBot tb,
                                           IPlayer p) {
        try {
            if (st.hasMoreTokens()
                && st.nextToken().trim()
                     .equalsIgnoreCase(tb.getLocalPlayer().getName())) {
                if (!p.isEnemyOf(tb.getLocalPlayer())) {
                    if (st.hasMoreTokens()) {
                        String command = st.nextToken().trim();
                        boolean understood = false;
                        // should create a command factory and a
                        // command object...
                        if (command.equalsIgnoreCase("echo")) { //$NON-NLS-1$
                            understood = true;
                        }
                        if (command.equalsIgnoreCase("calm down")) { //$NON-NLS-1$
                            for (Entity entity : tb.getEntitiesOwned()) {
                                CEntity cen = tb.centities.get(entity);
                                if (cen.strategy.attack > 1) {
                                    cen.strategy.attack = 1;
                                }
                            }
                            understood = true;
                        } else if (command.equalsIgnoreCase("be aggressive")) { //$NON-NLS-1$
                            for (Entity entity : tb.getEntitiesOwned()) {
                                CEntity cen = tb.centities.get(entity);
                                cen.strategy.attack = Math.min(
                                        cen.strategy.attack * 1.2, 1.5);
                            }
                            understood = true;
                        } else if (command.equalsIgnoreCase("attack")) { //$NON-NLS-1$
                            int x = Integer.parseInt(st.nextToken().trim());
                            int y = Integer.parseInt(st.nextToken().trim());
                            Entity en = tb.getGame().getFirstEntity(new Coords(
                                    x - 1, y - 1));
                            if (en != null) {
                                if (en.isEnemyOf(tb.getEntitiesOwned().get(0))) {
                                    CEntity cen = tb.centities.get(en);
                                    cen.strategy.target += 3;
                                    System.out.println(cen.entity
                                                               .getShortName()
                                                       + " " + cen.strategy.target); //$NON-NLS-1$
                                    understood = true;
                                }
                            }
                        }
                        if (understood) {
                            tb.sendChat(Messages.getString("ChatProcessor.BotUnderstood") + p.getName()); //$NON-NLS-1$
                        }
                    }
                } else {
                    tb.sendChat(Messages.getString("ChatProcessor.BotCanNotDo") + p.getName()); //$NON-NLS-1$
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private IPlayer getPlayer(IGame game, String playerName) {
        Enumeration<IPlayer> players = game.getPlayers();
        while (players.hasMoreElements()) {
            IPlayer testPlayer = players.nextElement();
            if (playerName.equalsIgnoreCase(testPlayer.getName())) {
                return testPlayer;
            }
        }
        return null;
    }

    protected void additionalPrincessCommands(GamePlayerChatEvent chatEvent, Princess princess) {
        final String METHOD_NAME = "additionalPrincessCommands(GamePlayerChatEvent, Princess, IPlayer)"; //$NON-NLS-1$

        // Commands should be sent in this format:
        //   <botName>: <command> : <arguments>

        StringTokenizer tokenizer = new StringTokenizer(chatEvent.getMessage(), ":"); //$NON-NLS-1$
        if (tokenizer.countTokens() < 3) {
            return;
        }

        String msg = "Received message: \"" + chatEvent.getMessage() + "\".\tMessage Type: " + chatEvent.getEventName(); //$NON-NLS-1$ //$NON-NLS-2$
        princess.log(getClass(), METHOD_NAME, LogLevel.INFO, msg);

        // First token should be who sent the message.
        String from = tokenizer.nextToken().trim();

        // Second token should be the player name the message is directed to.
        String sentTo = tokenizer.nextToken().trim();
        IPlayer princessPlayer = princess.getLocalPlayer();
        if (princessPlayer == null) {
            princess.log(getClass(), METHOD_NAME, LogLevel.ERROR, "Princess Player is NULL."); //$NON-NLS-1$
            return;
        }
        String princessName = princessPlayer.getName(); // Make sure the command is directed at the Princess player.
        if (!princessName.equalsIgnoreCase(sentTo)) {
            return;
        }

        // The third token should be the actual command.
        String command = tokenizer.nextToken().trim();
        if (command.length() < 2) {
            princess.sendChat(Messages.getString("ChatProcessor.BotDoNotRecognizeCommand")); //$NON-NLS-1$
        }

        // Any remaining tokens should be the command arguments.
        String[] arguments = null;
        if (tokenizer.hasMoreElements()) {
            arguments = tokenizer.nextToken().trim().split(" "); //$NON-NLS-1$
        }

        // Make sure the speaker is a real player.
        IPlayer speakerPlayer = chatEvent.getPlayer();
        if (speakerPlayer == null) {
            speakerPlayer = getPlayer(princess.getGame(), from);
            if (speakerPlayer == null) {
                princess.log(getClass(), METHOD_NAME, LogLevel.ERROR, "speakerPlayer is NULL."); //$NON-NLS-1$
                return;
            }
        }

        // Change verbosity level.
        if (command.toLowerCase().startsWith(ChatCommands.VERBOSE.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotNoLogSpecified"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }
            LogLevel newLevel = LogLevel.getLogLevel(arguments[0].trim());
            if (newLevel == null) {
                msg = Messages.getString("ChatProcessor.BotInvalidVerbosity") + arguments[0]; //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg);
                princess.sendChat(msg);
                return;
            }
            princess.setVerbosity(newLevel);
            msg = Messages.getString("ChatProcessor.BotVerbositySetTo") + princess.getVerbosity().toString(); //$NON-NLS-1$
            princess.log(getClass(), METHOD_NAME, LogLevel.DEBUG, msg);
            princess.sendChat(msg);
            return;
        }

        // Tell me what behavior you are using.
        if (command.toLowerCase().startsWith(ChatCommands.SHOW_BEHAVIOR.getAbbreviation())) {
            msg = Messages.getString("ChatProcessor.BotCurrentBehavior") + princess.getBehaviorSettings().getDescription(); //$NON-NLS-1$
            princess.sendChat(msg);
            princess.log(getClass(), METHOD_NAME, LogLevel.INFO, msg);
        }

        // List the available commands.
        if (command.toLowerCase().startsWith(ChatCommands.LIST__COMMANDS.getAbbreviation())) {
            StringBuilder out = new StringBuilder("Princess Chat Commands"); //$NON-NLS-1$
            for (ChatCommands cmd : ChatCommands.values()) {
                out.append("\n").append(cmd.getSyntax()).append(" :: ").append(cmd.getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
            }
            princess.sendChat(out.toString());
        }

        // Make sure the command came from my team.
        int speakerTeam = speakerPlayer.getTeam();
        int princessTeam = princessPlayer.getTeam();
        if (princessTeam != speakerTeam) {
            return;
        }

        // If instructed to, flee.
        if (command.toLowerCase().startsWith(ChatCommands.FLEE.getAbbreviation())) {
            msg = Messages.getString("ChatProcessor.BotReceivedFleeOrder"); //$NON-NLS-1$
            princess.log(getClass(), METHOD_NAME, LogLevel.DEBUG, msg);
            princess.sendChat(Messages.getString("ChatProcessor.BotRunAway")); //$NON-NLS-1$
            princess.setFallBack(true, msg);
            return;
        }

        // Load a new behavior.
        if (command.toLowerCase().startsWith(ChatCommands.BEHAVIOR.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotNoBehavior"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }
            String behaviorName = arguments[0].trim();
            BehaviorSettings newBehavior = BehaviorSettingsFactory.getInstance().getBehavior(behaviorName);
            if (newBehavior == null) {
                msg = Messages.getString("ChatProcessor.BotBehavior") + behaviorName + Messages.getString("ChatProcessor.BotBehaviorDoesNotExist"); //$NON-NLS-1$ //$NON-NLS-2$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg);
                princess.sendChat(msg);
                return;
            }
            princess.setBehaviorSettings(newBehavior);
            msg = Messages.getString("ChatProcessor.BotBehaviorChanged") + princess.getBehaviorSettings().getDescription(); //$NON-NLS-1$
            princess.sendChat(msg);
            return;
        }

        // Adjust fall shame.
        if (command.toLowerCase().startsWith(ChatCommands.CAUTION.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxCaution"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String adjustment = arguments[0];
            int currentFallShame = princess.getBehaviorSettings().getFallShameIndex();
            int newFallShame = currentFallShame;
            newFallShame += princess.calculateAdjustment(adjustment);
            princess.getBehaviorSettings().setFallShameIndex(newFallShame);
            msg = Messages.getString("ChatProcessor.BotCautionChangedFrom") + currentFallShame + Messages.getString("ChatProcessor.BotCautionChangedTo") + //$NON-NLS-1$ //$NON-NLS-2$
                  princess.getBehaviorSettings().getFallShameIndex();
            princess.sendChat(msg);
        }

        // Adjust self preservation.
        if (command.toLowerCase().startsWith(ChatCommands.AVOID.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxAvoid"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String adjustment = arguments[0];
            int currentSelfPreservation = princess.getBehaviorSettings().getSelfPreservationIndex();
            int newSelfPreservation = currentSelfPreservation;
            newSelfPreservation += princess.calculateAdjustment(adjustment);
            princess.getBehaviorSettings().setSelfPreservationIndex(newSelfPreservation);
            msg = Messages.getString("ChatProcessor.BotSelfPreservationChangedFrom") + currentSelfPreservation + Messages.getString("ChatProcessor.BotSelfPreservationChangedTo") + //$NON-NLS-1$ //$NON-NLS-2$
                  princess.getBehaviorSettings().getSelfPreservationIndex();
            princess.sendChat(msg);
        }

        // Adjust aggression.
        if (command.toLowerCase().startsWith(ChatCommands.AGGRESSION.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxAggression"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String adjustment = arguments[0];
            int currentAggression = princess.getBehaviorSettings().getHyperAggressionIndex();
            int newAggression = currentAggression;
            newAggression += princess.calculateAdjustment(adjustment);
            princess.getBehaviorSettings().setHyperAggressionIndex(newAggression);
            msg = Messages.getString("ChatProcessor.BotAggressionChanged") + currentAggression + Messages.getString("ChatProcessor.BotAggressionChangedTo") + //$NON-NLS-1$ //$NON-NLS-2$
                  princess.getBehaviorSettings().getHyperAggressionIndex();
            princess.sendChat(msg);
        }

        // Adjust herd mentality.
        if (command.toLowerCase().startsWith(ChatCommands.HERDING.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxHerding"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String adjustment = arguments[0];
            int currentHerding = princess.getBehaviorSettings().getHerdMentalityIndex();
            int newHerding = currentHerding;
            newHerding += princess.calculateAdjustment(adjustment);
            princess.getBehaviorSettings().setHerdMentalityIndex(newHerding);
            msg = Messages.getString("ChatProcessor.BotHerdingChangedFrom") + currentHerding + Messages.getString("ChatProcessor.BotHerdingChangedTo") + //$NON-NLS-1$ //$NON-NLS-2$
                  princess.getBehaviorSettings().getHerdMentalityIndex();
            princess.sendChat(msg);
        }

        // Adjust bravery.
        if (command.toLowerCase().startsWith(ChatCommands.BRAVERY.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxBrave"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String adjustment = arguments[0];
            int currentBravery = princess.getBehaviorSettings().getBraveryIndex();
            int newBravery = currentBravery;
            newBravery += princess.calculateAdjustment(adjustment);
            princess.getBehaviorSettings().setBraveryIndex(newBravery);
            msg = Messages.getString("ChatProcessor.BotBraveryChanged") + currentBravery + Messages.getString("ChatProcessor.BotBraveryChangedTo") + //$NON-NLS-1$ //$NON-NLS-2$
                  princess.getBehaviorSettings().getBraveryIndex();
            princess.sendChat(msg);
        }

        // Specify a "strategic" building target.
        if (command.toLowerCase().startsWith(ChatCommands.TARGET.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxTarget"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            String hex = arguments[0];
            if (hex.length() != 4 || !StringUtil.isPositiveInteger(hex)) {
                msg = Messages.getString("ChatProcessor.BotInvalidHex") + hex; //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            int x = Integer.parseInt(hex.substring(0, 2)) - 1;
            int y = Integer.parseInt(hex.substring(2, 4)) - 1;
            Coords coords = new Coords(x, y);
            if (!princess.getGame().getBoard().contains(coords)) {
                msg = Messages.getString("ChatProcessor.BotNoHexOnBoard") + hex; //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            princess.addStrategicBuildingTarget(coords);
            msg = Messages.getString("ChatProcessor.BotHex") + hex + Messages.getString("ChatProcessor.BotAddedToTargetList"); //$NON-NLS-1$ //$NON-NLS-2$
            princess.sendChat(msg);
        }

        // Specify a priority unit target.
        if (command.toLowerCase().startsWith(ChatCommands.PRIORITIZE.getAbbreviation())) {
            if (arguments == null || arguments.length == 0) {
                msg = Messages.getString("ChatProcessor.BotInvalidSyntaxPriority"); //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }
            String id = arguments[0];
            if (!StringUtil.isPositiveInteger(id)) {
                msg = Messages.getString("ChatProcessor.BotInvalidUnitId") + id; //$NON-NLS-1$
                princess.log(getClass(), METHOD_NAME, LogLevel.WARNING, msg + "\n" + chatEvent.getMessage()); //$NON-NLS-1$
                princess.sendChat(msg);
                return;
            }

            princess.getBehaviorSettings().addPriorityUnit(id);
            msg = Messages.getString("ChatProcessor.BotUnit") + id + Messages.getString("ChatProcessor.BotAddedToPriorityTargetList"); //$NON-NLS-1$ //$NON-NLS-2$
            princess.sendChat(msg);
        }
    }
}
