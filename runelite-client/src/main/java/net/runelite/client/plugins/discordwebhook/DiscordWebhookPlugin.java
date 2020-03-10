package net.runelite.client.plugins.discordwebhook;
import com.google.common.collect.ImmutableList;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.google.inject.Provides;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.HttpUrl;
import javax.inject.Inject;
import javax.swing.tree.ExpandVetoException;

import net.runelite.client.config.ConfigManager;
import java.awt.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.client.game.ItemManager;

@PluginDescriptor(
        name = "Discord Loot Messager",
        description = "Sends messages in Discord when receiving loot",
        tags =  {"discord","loot","webhook","broadcast"}
)

public class DiscordWebhookPlugin extends Plugin {
    private HttpUrl discordUrl;
    private boolean checkAccount;
    private String userName;
    private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of("You have a funny feeling like you're being followed",
            "You feel something weird sneaking into your backpack",
            "You have a funny feeling like you would have been followed");
    private static final Pattern COX_COMPLETE = Pattern.compile("Your completed Chambers of Xeric count is: ([\\d])+");
    private static final Pattern COX_CM_COMPLETE = Pattern.compile("Your completed Chambers of Xeric Challenge Mode count is: ([\\d])+");
    private static final Pattern TOB_COMPLETE = Pattern.compile("Your completed Theatre of Blood count is: ([\\d])+");
    private static final Pattern UNSIRED_PET_MESSAGE = Pattern.compile("^The Font appears to have revitalised the Unsired!");
    private static final Pattern JAD_GAMBLE_MESSAGE = Pattern.compile("^You lucky. Better train him good else TzTok-Jad");
    private static final Pattern INFERNO_GAMBLE_MESSAGE = Pattern.compile("Luck be a TzHaar tonight. Jal-Nik-Rek is yours.");
    private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed ([0-9]+) ([a-z]+) Treasure Trails.");
    private static final Pattern BOSS_KILL = Pattern.compile("^Your (.*?) kill count is: ([\\d]+)(.*)$");
    private static final Pattern BARROWS_LOOT = Pattern.compile("Your Barrows chest count is: ([\\d]+)");
    private static final Pattern PHOENIX_PET = Pattern.compile("^(.*)+ received received a drop: Phoenix");
    private static final String HERBIBOAR_LOOTED_MESSAGE = "You harvest herbs from the herbiboar, whereupon it escapes.";
    private static final String HERBIBOR_EVENT = "Herbiboar";
    private static final String ICON_BASE_URL = "https://www.osrsbox.com/osrsbox-db/items-icons/";
    private final ArrayList<Integer> uniques = getUniques();
    private boolean gotPet = false;
    private String eventType, lastBoss = "";
    private static final int THEATRE_OF_BLOOD_REGION = 12867;
    private int lastKc;
    private DecimalFormat df = new DecimalFormat("#,###");

    @Inject
    private Client client;

    @Inject
    private DiscordWebhookConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ConfigManager configManager;

    @Override
    protected void startUp()
    {
        discordInit();
    }

    @Provides
    DiscordWebhookConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DiscordWebhookConfig.class);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
            case LOGGED_IN:
                checkAccount = true;
                break;
        }
    }

    // handle a pet message
    // not tested eventTypes correctly assigning
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        final String message = event.getMessage();

        // tested correctly on zulrah
        final Matcher lookKc = BOSS_KILL.matcher(Text.removeTags(message));
        if(lookKc.find()) {
            lastKc = Integer.parseInt(lookKc.group(2));
            lastBoss = lookKc.group(1);
            eventType = "Boss";
        }

        // tested correctly
        final Matcher lookBarrows = BARROWS_LOOT.matcher(Text.removeTags(message));
        if(lookBarrows.find()) {
            lastKc = Integer.parseInt(lookBarrows.group(1));
        }

        if (message.equals(HERBIBOAR_LOOTED_MESSAGE)) {
            eventType = HERBIBOR_EVENT;
        }

        // watch for Mining event
        if((message.startsWith("You manage to mine some")) || (message.startsWith("You manage to quarry some"))
                || message.startsWith("You just mined a")) {
            eventType = "Mining";
        }

        // check for Woodcutting
        if (message.startsWith("You get some") && (message.endsWith("logs.") || message.endsWith("mushrooms."))) {
            eventType = "Woodcutting";
        }

        // check for Hunter chinchompas
        if(message.startsWith("You've caught a") && message.endsWith("chinchompa")) {
            eventType = "ChinHunter";
        }

        // check for Thieving
        if(message.startsWith("You pick the") || message.startsWith("You steal a")
                || message.equalsIgnoreCase("An elemental force emanating from the garden teleports you away.")) {
            // need pp room area check
            eventType = "Thieving";
        }

        // check for Fishing
        if (message.contains("You catch a") || message.contains("You catch some") ||
                message.equals("Your cormorant returns with its catch.")) {
            eventType = "Fishing";
        }

        // check for RuneCraft
        if(message.startsWith("You bind the temple's power into") && message.endsWith("runes.")) {
            // need zeah rc area check
            eventType = "Runecrafting";
        }

        // check for active Todt
        if(message.equalsIgnoreCase("You have gained a supply create!")) {
            eventType = "Wintertodt";
        }

        // check for Agility

        // check for Farming

        // Check if message is for a clue scroll reward
        final Matcher mClue = CLUE_SCROLL_PATTERN.matcher(Text.removeTags(message));
        String clueKc = "";
        if (mClue.find())
        {
            final String type = mClue.group(1).toLowerCase();
            switch (type)
            {
                case "beginner":
                    eventType = "Clue Scroll (Beginner)";
                    break;
                case "easy":
                    eventType = "Clue Scroll (Easy)";
                    break;
                case "medium":
                    eventType = "Clue Scroll (Medium)";
                    break;
                case "hard":
                    eventType = "Clue Scroll (Hard)";
                    break;
                case "elite":
                    eventType = "Clue Scroll (Elite)";
                    break;
                case "master":
                    eventType = "Clue Scroll (Master)";
                    clueKc = mClue.group(2).toLowerCase();
            }
        }

        final Matcher cox = COX_COMPLETE.matcher(Text.removeTags(message));
        if(cox.find()) {
            lastKc = Integer.parseInt(cox.group(1));
            lastBoss = "Chambers of Xeric";
            eventType = "Boss";
        }

        final Matcher coxCM = COX_CM_COMPLETE.matcher(Text.removeTags(message));
        if(coxCM.find()) {
            lastKc = Integer.parseInt(coxCM.group(1));
            lastBoss = "Chambers of Xeric (Challenge Mode)";
            eventType = "Boss";
        }

        final Matcher tob = TOB_COMPLETE.matcher(Text.removeTags(message));
        if(tob.find()) {
            lastKc = Integer.parseInt(tob.group(1));
            lastBoss = "Theatre of Blood";
            eventType = "Boss";
        }
        //System.out.println("e: " + eventType);
        final ImmutableList<String> NEST_MESSAGES = ImmutableList.of("A bird's nest",
                "A bird's nest falls into",
                "A bird's nest falls out of the tree");

        // recognize a pet message
        if (PET_MESSAGES.stream().anyMatch(message::contains)) {
            gotPet = true;
            userName = client.getLocalPlayer().getName();
            String itemImage = ICON_BASE_URL;
            if(eventType == "Boss") {
                String petStr = userName + " has received a pet " + lastBoss + "!";
                itemImage = ICON_BASE_URL + getPetId(lastBoss) + ".png";
                discordMessage1Field(petStr, "KC: ", String.valueOf(lastKc), false, itemImage);
                gotPet = false;
            }
            switch(eventType) {
                case "Mining":
                    itemImage += getPetId("Rock golem") + ".png";
                    gotSkillPet("Rock Golem", itemImage);
                    break;
                case "Woodcutting":
                    itemImage += getPetId("Beaver") + ".png";
                    gotSkillPet("Beaver", itemImage);
                    break;
                case "ChinHunter":
                    itemImage += getPetId("Baby chinchompa") + ".png";
                    gotSkillPet("Chinchompa", itemImage);
                    break;
                case "Thieving":
                    itemImage += getPetId("Rocky") + ".png";
                    gotSkillPet("Rocky", itemImage);
                    break;
                case "Fishing":
                    itemImage += getPetId("Heron") + ".png";
                    gotSkillPet("Heron", itemImage);
                    break;
                case "Runecraft":
                    itemImage += getPetId("Rift Guardian") + ".png";
                    gotSkillPet("Rift Guardian", itemImage);
                    break;
                case HERBIBOR_EVENT:
                    String herbKc = getKc("Herbiboar");
                    String descStr = userName + " received the Herbiboar pet!";
                    itemImage += getPetId("herbiboar") + ".png";
                    discordMessage1Field(descStr, "KC: ", herbKc, false, itemImage);
                    gotPet = false;
                    break;
                case "Clue Scroll (Master)":
                    String bhStr = userName + " received the Bloodhound pet!";
                    itemImage += getPetId("Bloodhound") + "png";
                    discordMessage1Field(bhStr,"KC: ",clueKc, false, itemImage);
                    gotPet = false;
                default:
                    break;
            }

            // check for Phoenix pet
            final Matcher mPhoenix = PHOENIX_PET.matcher(Text.removeTags(message));
            if(mPhoenix.find()) {
                String msgUser = mPhoenix.group(1);
                if(userName == msgUser) {
                    itemImage = ICON_BASE_URL + getPetId("Wintertodt") + ".png";
                    gotSkillPet("Phoenix", itemImage);
                }
            }

            // check for BH Gamble message

        }
    }

    // tested correctly on barrows
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        final ItemContainer container;
        String npcKc = "";
        switch (event.getGroupId()) {
            case (WidgetID.BARROWS_REWARD_GROUP_ID):
                eventType = "Barrows";
                container = client.getItemContainer(InventoryID.BARROWS_REWARD);
                break;
            case (WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID):
                eventType = "Chambers of Xeric";
                container = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);
                if(gotPet) {

                }
                break;
            case (WidgetID.THEATRE_OF_BLOOD_GROUP_ID):
                int region = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
                if (region != THEATRE_OF_BLOOD_REGION)
                {
                    return;
                }
                eventType = "Theatre of Blood";
                container = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);

                if(gotPet) {

                }
                break;
            case (WidgetID.CLUE_SCROLL_REWARD_GROUP_ID):
                // event type should be set via ChatMessage for clue scrolls.
                // Clue Scrolls use same InventoryID as Barrows
                container = client.getItemContainer(InventoryID.BARROWS_REWARD);
                break;
            default:
                return;
        }
        userName = client.getLocalPlayer().getName();
        Item[] containerItems = container.getItems();
        Collection<ItemStack> itemIds = new ArrayList();
        LocalPoint lp = LocalPoint.fromWorld(client, 0, 0);
        for (Item i : containerItems) {
            ItemStack temp = new ItemStack(i.getId(), i.getQuantity(), lp);
            itemIds.add(temp);
        }
        for(ItemStack i : itemIds) {
            ItemComposition ic = itemManager.getItemComposition(i.getId());
            Integer itemPrice = 0;
            if(ic.isTradeable()) {
                itemPrice = ic.getPrice();
            }
            Integer totalLootValue = itemPrice * i.getQuantity();
            if (totalLootValue >= config.getMinLootValue() || (i.getId() == 22386)) {
                String descStr = userName + " has received " + ic.getName() + " from "
                        + eventType + "!";
                String itemImage = ICON_BASE_URL + i.getId() + ".png";
                if (totalLootValue >= config.getMinLootValue()) {
                    discordMessage2Fields(descStr, "Value: ", df.format(totalLootValue), true, "KC: ", df.format(lastKc), true, itemImage);
                }
                // if metamorphic dust
                if (i.getId() == 22386) {
                    discordMessage1Field(descStr, "KC: ", npcKc, false, itemImage);
                }
            }
        }
    }

    @Subscribe
    public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
    {
        userName = client.getLocalPlayer().getName();
        final String npcName = npcLootReceived.getNpc().getName();
        final Collection<ItemStack> itemstack = npcLootReceived.getItems();
        int npcKc = lastKc;
        Integer minVal = config.getMinLootValue();
        for(ItemStack i : itemstack) {
            Integer itemID = i.getId();
            ItemComposition ic = itemManager.getItemComposition(itemID);
            Integer itemPrice = 0;
            try {
                itemPrice = priceLookup(ic.getName());
            }
            catch (NullPointerException e) {
                itemPrice = 0;
            }

            Integer totalStackVal = itemPrice * i.getQuantity();
            if((uniques.contains(itemID)) || (totalStackVal >= minVal)) {
                String descStr = userName + " has received " + ic.getName() + " from "
                        + npcName + "!";
                String itemImage = ICON_BASE_URL + itemID + ".png";
                if(npcKc == 0) {
                    if(ic.isTradeable()) {
                        discordMessage1Field(descStr, "Value: ", df.format(totalStackVal), false, itemImage);
                    }
                    else {
                        discordMessage1Field(descStr, "", "", false, itemImage);
                    }
                }
                else {
                    if(totalStackVal > 0) {
                        discordMessage2Fields(descStr, "Value: ", df.format(totalStackVal), true, "KC: ", String.valueOf(npcKc), true, itemImage);
                    }
                    else {
                        discordMessage1Field(descStr, "KC: ", String.valueOf(npcKc), false, itemImage);
                    }
                }
            }
        }
    }

    private void discordInit()
    {
        discordUrl = HttpUrl.parse(config.getDiscordUrl());
    }

    private static Collection<ItemStack> stack(Collection<ItemStack> items)
    {
        final List<ItemStack> list = new ArrayList<>();

        for (final ItemStack item : items)
        {
            int quantity = 0;
            for (final ItemStack i : list)
            {
                if (i.getId() == item.getId())
                {
                    quantity = i.getQuantity();
                    list.remove(i);
                    break;
                }
            }
            if (quantity > 0)
            {
                list.add(new ItemStack(item.getId(), item.getQuantity() + quantity, item.getLocation()));
            }
            else
            {
                list.add(item);
            }
        }

        return list;
    }

    // tested correctly on Banker text detect -> discord message
    // untested: kcs
    @Subscribe
    public void onGameTick(GameTick tick) {
        Widget npcDialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
        if (npcDialog != null) {
            String npcText = Text.sanitizeMultilineText(npcDialog.getText()); //remove color and linebreaks
            // check for Abyssal Orphan
            final Matcher mUnsiredOrphan = UNSIRED_PET_MESSAGE.matcher(npcText);
            if(mUnsiredOrphan.find()) {
                userName = client.getLocalPlayer().getName();
                String descStr = userName + " received the Abyssal Orphan pet!";
                String itemImage = ICON_BASE_URL + getPetId("Abyssal sire") +".png";
                discordMessage1Field(descStr,"KC: ",getKc("Abyssal Sire"), false, itemImage);
                gotPet = false;
            }

            // check for Jad pet gamble
            final Matcher mJadPet = JAD_GAMBLE_MESSAGE.matcher(npcText);
            if(mJadPet.find()) {
                userName = client.getLocalPlayer().getName();
                String descStr = userName + " received the TzTokJad pet from a Fire Cape gamble!";
                String itemImage = ICON_BASE_URL + getPetId("TzTok-jad") +".png";
                discordMessage1Field(descStr,"KC: ",getKc("TzTok-Jad"), false,"");
                gotPet = false;
            }

            // check for Inferno gamble
            final Matcher mInfernoPet = INFERNO_GAMBLE_MESSAGE.matcher(npcText);
            if(mInfernoPet.find()) {
                userName = client.getLocalPlayer().getName();
                String descStr = userName + " received the Jal-Nib-Rek pet from an Infernal Cape gamble!";
                String itemImage = ICON_BASE_URL + getPetId("Tzkal-Zuk") +".png";
                discordMessage1Field(descStr,"KC: ",getKc("TzKal-Zuk"), false,"");
                gotPet = false;
            }

        }
    }

    private void discordMessage1Field(String descStr, String field1Name, String field1Desc, boolean field1Line, String iconUrl)
    {
        DiscordWebhook dwh = new DiscordWebhook(discordUrl.toString());
        dwh.setTts(true);
        dwh.addEmbed(new DiscordWebhook.EmbedObject()
                .setDescription(descStr)
                .setColor(Color.red)
                .addField(field1Name,field1Desc,field1Line)
                .setImage(iconUrl));
        try {
            dwh.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void discordMessage2Fields(String descStr, String field1Name, String field1Desc, boolean field1Line, String field2Name, String field2Desc, boolean field2Line, String iconUrl)
    {
        DiscordWebhook dwh = new DiscordWebhook(discordUrl.toString());
        dwh.setTts(true);
        dwh.addEmbed(new DiscordWebhook.EmbedObject()
                .setDescription(descStr)
                .setColor(Color.red)
                .addField(field1Name,field1Desc,field1Line)
                .addField(field2Name,field2Desc,field2Line)
                .setImage(iconUrl));
        try {
            dwh.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getKc(String boss)
    {
        Integer killCount = configManager.getConfiguration("killcount." + client.getUsername().toLowerCase(),
                boss, int.class);
        return String.valueOf(killCount == null ? 0 : killCount);
    }

    // hardcoded unique drops that are 1/1000 or rare, and untradeables on the Collection Log
    private ArrayList<Integer> getUniques()
    {
        ArrayList<Integer> HOLDING_SET = new ArrayList<>();
        // Alchemical Hydra items
        ArrayList<Integer> hydra = new ArrayList<>();
        hydra.add(22971); // Hydra's fang
        hydra.add(22972);
        hydra.add(22973); // Hydra's eye
        hydra.add(22974);
        hydra.add(22969); // Hydra's heart
        hydra.add(22970);
        hydra.add(23077); // Alchemical hydra heads
        hydra.add(23078);
        hydra.add(20849); // Dragon thrownaxe
        hydra.add(20850);
        hydra.add(21207);
        hydra.add(22804); // Dragon knife
        hydra.add(22805);
        hydra.add(22812);
        hydra.add(22813);
        HOLDING_SET.addAll(hydra);

        // Cerberus items
        ArrayList<Integer> cerberus = new ArrayList<>();
        cerberus.add(13245); // Jar of souls
        cerberus.add(14016);
        HOLDING_SET.addAll(cerberus);

        // Corporeal beast items
        ArrayList<Integer> corp = new ArrayList<>();
        corp.add(12819); // Elysian sigil
        corp.add(15332);
        corp.add(12823); // Spectral sigil
        corp.add(15334);
        corp.add(12827); // Arcane sigil
        corp.add(15336);
        HOLDING_SET.addAll(corp);

        // Grotesque Guardians items
        ArrayList<Integer> guardians = new ArrayList<>();
        guardians.add(21730); // Black tourmaline core
        guardians.add(21732);
        guardians.add(21745); // Jar of stone
        guardians.add(21747);
        HOLDING_SET.addAll(guardians);

        // Kalphite Queen items
        ArrayList<Integer> kq = new ArrayList<>();
        kq.add(7981); // Kq head
        kq.add(14906);
        kq.add(22671); // Kq head (tattered)
        kq.add(22672);
        kq.add(12885); // Jar of sand
        kq.add(17047);
        HOLDING_SET.addAll(kq);

        // King Black Dragon items
        ArrayList<Integer> kbd = new ArrayList<>();
        kbd.add(7980); // KBD heads
        kbd.add(14905);
        kbd.add(11920); // Dragon pickaxe
        kbd.add(14766);
        kbd.add(11286); // Draconic visage
        kbd.add(16834);
        HOLDING_SET.addAll(kbd);

        // Kraken items
        ArrayList<Integer> kraken = new ArrayList<>();
        kraken.add(12007); // Jar of dirt
        kraken.add(14015);
        HOLDING_SET.addAll(kraken);

        // Skotizo items
        ArrayList<Integer> skotizo = new ArrayList<>();
        skotizo.add(19701); // Jar of darkness
        skotizo.add(19703);
        skotizo.add(21275); // Dark claw
        HOLDING_SET.addAll(skotizo);

        // Vorkath items
        ArrayList<Integer> vorkath = new ArrayList<>();
        vorkath.add(2425); // Vorkath's head
        vorkath.add(21907);
        vorkath.add(21908);
        vorkath.add(11286); // Draconic Visage
        vorkath.add(16834);
        vorkath.add(22006); // Skeletal Visage
        vorkath.add(22008);
        vorkath.add(22106); // Jar of decay
        vorkath.add(22108);
        vorkath.add(22111); // Dragonbone necklace
        vorkath.add(22113);
        HOLDING_SET.addAll(vorkath);

        // Zulrah items
        ArrayList<Integer> zulrah = new ArrayList<>();
        zulrah.add(13200); // Tanzanite mutagen
        zulrah.add(15320);
        zulrah.add(13201); // Magma mutagen
        zulrah.add(15321);
        zulrah.add(12936); // Jar of swamp
        zulrah.add(15325);
        HOLDING_SET.addAll(zulrah);

        ArrayList<Integer> UNIQUE_ITEM_IDS = (ArrayList<Integer>) HOLDING_SET.stream().collect(Collectors.toList());
        return UNIQUE_ITEM_IDS;
    }

    public void gotSkillPet(String petName, String itemUrl)
    {
        userName = client.getLocalPlayer().getName();
        String descStr = userName + " received the " + petName + " pet!";
        discordMessage1Field(descStr, "", "", false, itemUrl);
        gotPet = false;
    }

    private Integer priceLookup(String itemName)
    {
        List<ItemPrice> results = itemManager.search(itemName);

        ItemPrice item = retrieveFromList(results, itemName);

        int itemId = item.getId();
        int itemPrice = 0;
        if(item.getPrice() > 0) {
            itemPrice = item.getPrice();
        }

        return Integer.valueOf(itemPrice);
    }

    private ItemPrice retrieveFromList(List<ItemPrice> items, String originalInput)
    {
        ItemPrice shortest = null;
        for (ItemPrice item : items)
        {
            if (item.getName().toLowerCase().equals(originalInput.toLowerCase()))
            {
                return item;
            }
            if (shortest == null || item.getName().length() < shortest.getName().length())
            {
                shortest = item;
                shortest.setPrice(0);
            }
        }

        return shortest;
    }

    public String getPetId(String name)
    {
        switch(name.toLowerCase()) {
            case "abyssal sire":
                return "13262";
            case "tztok-jad":
                return "13225";
            case "giant mole":
                return "12646";
            case "callisto":
                return "13178";
            case "cerberus":
                return "13247";
            case "alchemical hydra":
                return "22746";
            case "tzkal-zuk":
                return "21291";
            case "kalphite queen":
                return "12647";
            //
            case "tob":
                return "22473";
            //
            case "grotesques":
                return "21748";
            case "olmlet":
                return "20851";
            case "chaos elemental":
                return "11995";
            case "chaos fanatic":
                return "11995";
            case "dagannoth prime":
                return "12644";
            case "dagannoth rex":
                return "12645";
            case "dagannoth supreme":
                return "12643";
            case "corporeal beast":
                return "12816";
            case "general graardor":
                return "12650";
            case "k'ril tsutsaroth":
                return "12652";
            case "kraken":
                return "12655";
            case "kree'arra":
                return "12649";
            case "thermonuclear smoke devil":
                return "15299";
            case "zulrah":
                return "12921";
            case "commander zilyana":
                return "12651";
            case "king black dragon":
                return "12653";
            case "scorpia":
                return "13181";
            case "skotizo":
                return "21273";
            case "venenatis":
                return "13177";
            case "vet'ion":
                return "13179";
            case "vorkath":
                return "21992";
            case "baby chin":
                return "13323";
            case "beaver":
                return "13322";
            case "giant squirrel":
                return "20659";
            case "heron":
                return "13320";
            case "rift guardian":
                return "20665";
            case "rock golem":
                return "13321";
            case "rocky":
                return "20663";
            case "tangleroot":
                return "20661";
            case "bloodhound":
                return "19730";
            case "chompy chick":
                return "13071";
            case "herbiboar":
                return "21509";
            case "penance queen":
                return "12703";
            case "wintertodt":
                return "20693";
            default:
                return "";
        }
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned itemSpawned)
    {
        Integer region = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
        // Blast Mining regions
        if((region == 5692) || (region == 5948)) {
            Integer itemId = itemSpawned.getItem().getId();
        }
    }

    @Subscribe
    public void onExperienceChanged(ExperienceChanged event)
    {
        String sk = event.getSkill().getName();
        switch (sk) {
            case "Agility":
                eventType = "Agility";
                break;
            case "Farming":
                eventType = "Farming";
                break;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSp)
    {
        NPC nep = npcSp.getNpc();
        String name = nep.getName();
        if(gotPet) {
            try {
                if (name.startsWith("Hell")) {
                    NPCComposition comp = nep.getComposition();
                    System.out.println("hellpuppy test" + nep.getName() + " " + nep.getId() + " " + comp.isMinimapVisible());
                }
            }
            catch (NullPointerException npe) {

            }
        }
    }
}