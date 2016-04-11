package megamek.client.bot.princess;

/**
 * @author Deric Page (deric.page@nisc.coop) (ext 2335)
 * @version %Id%
 * @since 10/24/2014 9:57 AM
 */
public enum ChatCommands {
    FLEE("fl", "princessName: flee", Messages.getString("ChatCommands.BotFleeCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                     Messages.getString("ChatCommands.BotFleeCmdDescriptionCont")), //$NON-NLS-1$
    VERBOSE("ve", "princessName: verbose : <error/warning/info/debug>", Messages.getString("ChatCommands.BotVerbosityCmdDescription")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    BEHAVIOR("be", "princessName: behavior : behaviorName", Messages.getString("ChatCommands.BotBehaviorCmdDescription")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    CAUTION("ca", "princessName: caution : <+/->", Messages.getString("ChatCommands.BotCautionCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                   Messages.getString("ChatCommands.BotCautionCmdDescriptionCont")), //$NON-NLS-1$
    AVOID("av", "princessName: avoid : <+/->", Messages.getString("ChatCommands.BotAvoidCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                               Messages.getString("ChatCommands.BotAvoidCmdDescriptionCont")), //$NON-NLS-1$
    AGGRESSION("ag", "princessName: aggression : <+/->", Messages.getString("ChatCommands.BotAggressionCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                         Messages.getString("ChatCommands.BotAggressionCmdDescriptionCont")), //$NON-NLS-1$
    HERDING("he", "princessName: herd : <+/->", Messages.getString("ChatCommands.BotHerdingCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                Messages.getString("ChatCommands.BotHerdingCmdDescriptionCont")), //$NON-NLS-1$
    BRAVERY("br", "princessName: brave : <+/->", Messages.getString("ChatCommands.BotBraveryCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                 Messages.getString("ChatCommands.BotBraveryCmdDescriptionCont")), //$NON-NLS-1$
    TARGET("ta", "princessName: target : hexNumber", Messages.getString("ChatCommands.BotTargetHexCmdDescription")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    PRIORITIZE("pr", "princessName: prioritize : unitId", Messages.getString("ChatCommands.BotPrioritizeCmdDescription") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                          Messages.getString("ChatCommands.BotPrioritizeCmdDescriptionCont")), //$NON-NLS-1$
    SHOW_BEHAVIOR("sh", "princessName: showBehavior", Messages.getString("ChatCommands.BotShowBehaviorCmdDescription")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    LIST__COMMANDS("li", "princessName: listCommands", Messages.getString("ChatCommands.BotListCmdDescription")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private final String abbreviation;
    private final String syntax;
    private final String description;

    ChatCommands(String abbreviation, String syntax, String description) {
        this.abbreviation = abbreviation;
        this.syntax = syntax;
        this.description = description;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getSyntax() {
        return syntax;
    }

    public String getDescription() {
        return description;
    }
}
