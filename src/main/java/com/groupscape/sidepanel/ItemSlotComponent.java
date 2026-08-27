package com.groupscape.sidepanel;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

/**
 * One inventory/equipment slot: paints its item icon scaled to fit (native sprite sizes vary and
 * don't otherwise shrink to the slot's box), opens the item's OSRS Wiki page on click, and shows
 * a name/GE/HA tooltip on hover - fetched via {@link ClientThread} since {@code
 * ItemManager#getItemComposition} isn't safe to call from the Swing EDT this repaints on (see the
 * equipment bonus panel's own history with exactly that crash).
 */
class ItemSlotComponent extends JComponent {
    private static final String WIKI_LOOKUP_URL = "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=";

    private final ItemManager itemManager;
    private final ClientThread clientThread;

    private AsyncBufferedImage sprite;
    private int itemId;
    private int lastQuantity = -1;
    private int requestToken;

    ItemSlotComponent(ItemManager itemManager, ClientThread clientThread) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (itemId > 0) {
                    LinkBrowser.browse(WIKI_LOOKUP_URL + itemId);
                }
            }
        });
    }

    void setItem(int id, int quantity) {
        if (id == itemId && quantity == lastQuantity) {
            return;
        }
        itemId = id;
        lastQuantity = quantity;
        sprite = null;
        int token = ++requestToken;
        setToolTipText(null);
        setCursor(id > 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();

        if (id <= 0) {
            return;
        }

        sprite = itemManager.getImage(id, quantity, quantity > 1);
        sprite.onLoaded(this::repaint);

        clientThread.invoke(() -> {
            if (token != requestToken) {
                return;
            }
            ItemComposition comp = itemManager.getItemComposition(id);
            int gePrice = itemManager.getItemPrice(id);
            int haPrice = comp.getHaPrice();
            String tooltip = buildTooltip(comp.getName(), quantity, gePrice, haPrice);
            SwingUtilities.invokeLater(() -> {
                if (token == requestToken) {
                    setToolTipText(tooltip);
                }
            });
        });
    }

    private static String buildTooltip(String name, int quantity, int gePrice, int haPrice) {
        StringBuilder html = new StringBuilder("<html>").append(name);
        if (quantity > 1) {
            html.append(" (").append(String.format("%,d", quantity)).append(")");
        }
        html.append("<br>GE: ").append(String.format("%,d", gePrice));
        html.append("<br>HA: ").append(String.format("%,d", haPrice));
        html.append("</html>");
        return html.toString();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0) {
            return;
        }

        double scale = Math.min(getWidth() / (double) sprite.getWidth(), getHeight() / (double) sprite.getHeight());
        int w = (int) Math.round(sprite.getWidth() * scale);
        int h = (int) Math.round(sprite.getHeight() * scale);
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(sprite, x, y, w, h, this);
        g2.dispose();
    }
}
