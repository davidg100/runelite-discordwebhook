package net.runelite.client.plugins.discordwebhook;

import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.Text;
import javax.inject.Inject;
import java.awt.event.MouseEvent;

public class WintertodtCrateListener extends MouseAdapter
{
    private final Client client;
    private final DiscordWebhookPlugin plugin;

    static String listenerEventType = "";

    @Inject
    private WintertodtCrateListener(Client client, DiscordWebhookPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
    }

    //confirmed this is being recognized
    @Override
    public MouseEvent mouseClicked(MouseEvent e)
    {
        if(e.getButton() == MouseEvent.BUTTON1)
        {
            final MenuEntry[] wtmenuEntries = client.getMenuEntries();
            for (final MenuEntry menuEntry : wtmenuEntries)
            {
                if (Text.removeTags(menuEntry.getOption()).equals("Open") &&
                        Text.removeTags(menuEntry.getTarget()).equals("Supply crate"))
                {
                    listenerEventType = "Wintertodt";
                    break;
                }
            }
        }
        return super.mouseClicked(e);
    }
}