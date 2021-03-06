/*
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 */
package handling.channel.handler;

import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import client.inventory.Item;
import client.inventory.MapleInventoryType;
import client.MapleClient;
import client.MapleCharacter;
import constants.GameConstants;
import client.inventory.ItemLoader;
import database.DatabaseConnection;
import handling.world.World;
import java.util.Map;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MerchItemPackage;
import tools.FilePrinter;
import tools.Pair;
import tools.StringUtil;
import tools.packet.PlayerShopPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.CField.NPCPacket;
import tools.packet.CWvsContext;

public class HiredMerchantHandler {

    public static final boolean UseHiredMerchant(final MapleClient c, final boolean packet) {
        if (c.getPlayer().getMap() != null && c.getPlayer().getMap().allowPersonalShop()) {
            final byte state = checkExistance(c.getPlayer().getAccountID(), c.getPlayer().getId());

            switch (state) {
                case 1:
                    c.getPlayer().dropMessage(1, "H»y l¬y v±t ph¯m tö Keo ki½t.");
                    break;
                case 0:
                    boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
                    if (!merch) {
                        if (c.getChannelServer().isShutdown()) {
                            c.getPlayer().dropMessage(1, "The server is about to shut down.");
                            return false;
                        }
                        if (packet) {
                            c.getSession().write(PlayerShopPacket.sendTitleBox());
                        }
                        return true;
                    } else {
                        c.getPlayer().dropMessage(1, "Vui lÆng «óng c÷a hàng hi½n t¢i và th÷ l¢i.");
                    }
                    break;
                default:
                    c.getPlayer().dropMessage(1, "L×i không xác «Ånh.");
                    break;
            }
        } else {
            c.getSession().close();
        }
        return false;
    }

    private static byte checkExistance(final int accid, final int cid) {
        Connection con = DatabaseConnection.getConnection();
        try {
            try (PreparedStatement ps = con.prepareStatement("SELECT * from hiredmerch where accountid = ? OR characterid = ?")) {
                ps.setInt(1, accid);
                ps.setInt(2, cid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ps.close();
                        rs.close();
                        return 1;
                    }
                }
            }
            return 0;
        } catch (SQLException se) {
            return -1;
        }
    }
    
    public static void displayMerch(MapleClient c) {
        final int conv = c.getPlayer().getConversation();
        boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
        if (merch) {
            c.getPlayer().dropMessage(1, "Vui lÆng «óng c÷a hàng hi½n t¢i và th÷ l¢i.");
            c.getPlayer().setConversation(0);
        } else if (c.getChannelServer().isShutdown()) {
            c.getPlayer().dropMessage(1, "Máy chç «ang t¤t.");
            c.getPlayer().setConversation(0);
        } else if (conv == 3) { // Hired Merch
            final MerchItemPackage pack = loadItemFrom_Database(c.getPlayer().getAccountID());

            if (pack == null) {
                c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "I don't think you have any items or money to retrieve here.\r\nThis is where you retrieve the items and mesos that you couldn't get from your Hired Merchant. You'll also need to see me as the character that opened the Personal Store.", "00 00", (byte) 0));
                c.getPlayer().setConversation(0);
            } else if (pack.getItems().size() <= 0) { //error fix for complainers.
                if (!check(c.getPlayer(), pack)) {
                    c.getSession().write(PlayerShopPacket.merchItem_Message((byte) 0x21));
                    return;
                }
                if (deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId())) {
                    //c.getPlayer().fakeRelog();
                    c.getPlayer().gainMeso(pack.getMesos(), false);
                    c.getSession().write(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                    c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "I see that you forgot something here right?\r\nHere is your money sir " + pack.getMesos(), "00 00", (byte) 0));
                    c.getPlayer().setConversation(0);
                } else {
                    c.getPlayer().dropMessage(1, "L×i không xác «Ånh.");
                }
                c.getPlayer().setConversation(0);
            } else {
               c.getSession().write(PlayerShopPacket.merchItemStore_ItemData(pack));
                MapleInventoryManipulator.checkSpace(c, conv, conv, null);
                for (final Item item : pack.getItems()) {
                    if(c.getPlayer().getInventory(GameConstants.getInventoryType(item.getItemId())).isFull()){
                        c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "Sir, if you want your items back please clean up your inventory before you come here!", "00 00", (byte) 0));
                        c.getPlayer().setConversation(0);
                        break;
                    }
                    MapleInventoryManipulator.addFromDrop(c, item, true);
                    deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId());
                    c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "I saved your items sir, next time don't forget them, have a nice day.", "00 00", (byte) 0));
                    c.getPlayer().setConversation(0);
                }
                
            }
        }
        c.getSession().write(CWvsContext.enableActions());
    }

    public static void displayMerch2(MapleClient c) {
        final int conv = c.getPlayer().getConversation();
        boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
        
        if (merch) {
            c.getPlayer().dropMessage(1, "Vui lÆng «óng c÷a hàng hi½n t¢i và th÷ l¢i.");
            c.getPlayer().setConversation(0);
        } else if (c.getChannelServer().isShutdown()) {
            c.getPlayer().dropMessage(1, "Máy chç «ang t¤t.");
            c.getPlayer().setConversation(0);
        } else if (conv == 3) { // Hired Merch
            final MerchItemPackage pack = loadItemFrom_Database(c.getPlayer().getAccountID());

            if (pack == null) {
                c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "Tôi không nghÄ r¥ng b¢n có b¬t kÿ v±t ph¯m ho¶c ti¹n «º l¬y ä «ây. Ðây là nÛi b¢n truy xu¬t các mîc và mesos mà b¢n không thº nh±n «ïæc tö Ngïßi bán «» thuê cça m¾nh. B¢n cëng s³ c®n ph¡i xem tôi là nhân v±t «» mä C÷a hàng cá nhân.", "00 00", (byte) 0));
                c.getPlayer().setConversation(0);
            } else if (pack.getItems().size() <= 0) { //error fix for complainers.
                if (!check(c.getPlayer(), pack)) {
                    c.getSession().write(PlayerShopPacket.merchItem_Message((byte) 0x21));
                    return;
                }
                if (deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId())) {
                    c.getPlayer().fakeRelog();
                    c.getPlayer().gainMeso(pack.getMesos(), true);
                    c.getSession().write(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                    c.getPlayer().setConversation(0);
                } else {
                    c.getPlayer().dropMessage(1, "L×i không xác «Ånh.");
                }
                c.getPlayer().setConversation(0);
            } else {
                c.getSession().write(PlayerShopPacket.merchItemStore_ItemData(pack));
                MapleInventoryManipulator.checkSpace(c, conv, conv, null);
                for (final Item item : pack.getItems()) {
                    if (c.getPlayer().getInventory(GameConstants.getInventoryType(item.getItemId())).isFull()) {
                        c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "Vui lÆng làm trÑng túi hành lü cça b¢n.", "00 00", (byte) 0));
                        c.getPlayer().setConversation(0);
                        break;
                    }
                    String itemName = MapleItemInformationProvider.getInstance().getName(item.getItemId());
                    MapleInventoryManipulator.addFromDrop(c, item, true);
                    deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId());
                    FilePrinter.printError(FilePrinter.FREDRICK + c.getPlayer().getName() + ".txt", c.getPlayer().getName() + " nhan duoc " + item.getQuantity() + " " + itemName + " " + item.getItemId()+ " va "+  pack.getMesos()+" Mesos tu Keo Kiet\r\n");	        				

                    //c.getPlayer().fakeRelog();
                    c.getSession().write(NPCPacket.getNPCTalk(9030000, (byte) 0, "Các v±t ph¯m cça b¢n «» «ïæc xác nh±n quy¹n sä høu.", "00 00", (byte) 0));
                    c.getPlayer().setConversation(0);
                }

            }
        }
        c.getSession().write(CWvsContext.enableActions());
    }

    public static final void MerchantItemStore(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer() == null) {
            return;
        }
        final byte operation = slea.readByte();
        if (operation == 27 || operation == 28) { // Request, Take out
            requestItems(c, operation == 27);
        } else if (operation == 30) { // Exit
            c.getPlayer().setConversation(0);
        }
    }

    private static void requestItems(final MapleClient c, final boolean request) {
        if (c.getPlayer().getConversation() != 3) {
            return;
        }
        boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
        if (merch) {
            c.getPlayer().dropMessage(1, "Vui lÆng «óng c÷a hàng hi½n t¢i và th÷ l¢i.");
            c.getPlayer().setConversation(0);
            return;
        }
        final MerchItemPackage pack = loadItemFrom_Database(c.getPlayer().getAccountID());
        if (pack == null) {
            c.getPlayer().dropMessage(1, "L×i không xác «Ånh.");
            return;
        } else if (c.getChannelServer().isShutdown()) {
            c.getPlayer().dropMessage(1, "Máy chç «ang t¤t.");
            c.getPlayer().setConversation(0);
            return;
        }
        final int days = StringUtil.getDaysAmount(pack.getSavedTime(), System.currentTimeMillis()); // max 100%
        final double percentage = days / 100.0;
        final int fee = (int) Math.ceil(percentage * pack.getMesos()); // if no mesos = no tax
        if (request && days > 0 && percentage > 0 && pack.getMesos() > 0 && fee > 0) {
            c.getSession().write(PlayerShopPacket.merchItemStore((byte) 38, days, fee));
            return;
        }
        if (fee < 0) { // impossible
            c.getSession().write(PlayerShopPacket.merchItem_Message(33));
            return;
        }
        if (c.getPlayer().getMeso() < fee) {
            c.getSession().write(PlayerShopPacket.merchItem_Message(35));
            return;
        }
        if (!check(c.getPlayer(), pack)) {
            c.getSession().write(PlayerShopPacket.merchItem_Message(36));
            return;
        }
        if (deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId())) {
            if (fee > 0) {
                c.getPlayer().gainMeso(-fee, true);
            }
            c.getPlayer().gainMeso(pack.getMesos(), false);
            for (Item item : pack.getItems()) {
                MapleInventoryManipulator.addFromDrop(c, item, false);
            }
            c.getSession().write(PlayerShopPacket.merchItem_Message(32));
        } else {
            c.getPlayer().dropMessage(1, "L×i không xác «Ånh.");
        }
    }

    private static boolean check(final MapleCharacter chr, final MerchItemPackage pack) {
        if (chr.getMeso() + pack.getMesos() < 0) {
            return false;
        }
        byte eq = 0, use = 0, setup = 0, etc = 0, cash = 0;
        for (Item item : pack.getItems()) {
            final MapleInventoryType invtype = GameConstants.getInventoryType(item.getItemId());
            if (invtype == MapleInventoryType.EQUIP) {
                eq++;
            } else if (invtype == MapleInventoryType.USE) {
                use++;
            } else if (invtype == MapleInventoryType.SETUP) {
                setup++;
            } else if (invtype == MapleInventoryType.ETC) {
                etc++;
            } else if (invtype == MapleInventoryType.CASH) {
                cash++;
            }
            if (MapleItemInformationProvider.getInstance().isPickupRestricted(item.getItemId()) && chr.haveItem(item.getItemId(), 1)) {
                return false;
            }
        }
        return chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() >= eq && chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() >= use && chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() >= setup && chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() >= etc && chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() >= cash;
    }

    private static boolean deletePackage(final int accid, final int packageid, final int chrId) {
        final Connection con = DatabaseConnection.getConnection();

        try {
            try (PreparedStatement ps = con.prepareStatement("DELETE from hiredmerch where accountid = ? OR packageid = ? OR characterid = ?")) {
                ps.setInt(1, accid);
                ps.setInt(2, packageid);
                ps.setInt(3, chrId);
                ps.executeUpdate();
            }
            ItemLoader.HIRED_MERCHANT.saveItems(null, packageid);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static final void showFredrick(MapleClient c) {
        final MerchItemPackage pack = HiredMerchantHandler.loadItemFrom_Database(c.getPlayer().getAccountID());
        c.getSession().write(PlayerShopPacket.merchItemStore_ItemData(pack));
    }

    private static MerchItemPackage loadItemFrom_Database(final int accountid) {
        final Connection con = DatabaseConnection.getConnection();

        try {
            ResultSet rs;

            final int packageid;
            final MerchItemPackage pack;
            try (PreparedStatement ps = con.prepareStatement("SELECT * from hiredmerch where accountid = ?")) {
                ps.setInt(1, accountid);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    ps.close();
                    rs.close();
                    return null;
                }   packageid = rs.getInt("PackageId");
                pack = new MerchItemPackage();
                pack.setPackageid(packageid);
                pack.setMesos(rs.getInt("Mesos"));
                pack.setSavedTime(rs.getLong("time"));
            }
            rs.close();

            Map<Long, Pair<Item, MapleInventoryType>> items = ItemLoader.HIRED_MERCHANT.loadItems(false, packageid);
            if (items != null) {
                List<Item> iters = new ArrayList<>();
                for (Pair<Item, MapleInventoryType> z : items.values()) {
                    iters.add(z.left);
                }
                pack.setItems(iters);
            }

            return pack;
        } catch (SQLException e) {
            return null;
        }
    }
}