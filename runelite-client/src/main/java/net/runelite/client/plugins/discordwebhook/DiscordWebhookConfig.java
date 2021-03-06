/*
 * Copyright (c) 2018, Forsco <https://github.com/forsco>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.runelite.client.plugins.discordwebhook;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("discordwebhook")
public interface DiscordWebhookConfig extends Config
{
    @ConfigItem(
            name = "Webhook URL",
            keyName = "discordUrl",
            description = "Enter your Discord Webhook URL",
            position = 0
    )
    default String getDiscordUrl()
    {
        return "https://discordapp.com/api/webhooks/582977707227349003/G7yXwcTKE_eZKYlsu0DjpGYN1XkFrRccJCcE_xjjgLPCcCIdPWY7FjLoz3koZghcXBkh";
    }

    @ConfigItem(
            name = "Log loot received",
            keyName = "logLootType",
            description = "configure what received loot is logged",
            position = 1
    )
    default LootLogType lootLogType()
    {
        return LootLogType.NONE;
    }

    @ConfigItem(
            name = "Log > GE Value",
            keyName = "minLootValue",
            description = "Configures the minimum value which loot received is logged",
            position = 2
    )
    default int getMinLootValue()
    {
        return 0;
    }

    @ConfigItem(
            name = "Log clue scrolls",
            keyName = "logClues",
            description = "Configures logging clue scrolls",
            position = 4
    )
    default boolean isLoggingClues()
    {
        return false;
    }

    @ConfigItem(
            name = "Log pets received",
            keyName = "logPets",
            description = "Configures logging pets received",
            position = 10
    )
    default boolean isLoggingPets()
    {
        return false;
    }
}