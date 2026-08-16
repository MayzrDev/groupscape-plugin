package com.groupscape;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * One-click character link: clicking "Link this character" opens the website's {@code /link}
 * page carrying this character's account hash + RSN. The site's already-authenticated session
 * is what proves which account it links to, so the plugin has nothing to POST and no
 * pass/fail result to show here — confirmation happens entirely in the browser tab it opens.
 */
public class GroupScapePanel extends PluginPanel {
    private final JButton linkCharacterButton = new JButton("Link this character");
    private final JLabel linkHintLabel = new JLabel();

    private Runnable linkCharacterListener;

    GroupScapePanel() {
        super();
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        Font small = FontManager.getRunescapeSmallFont();

        JLabel heading = new JLabel("Character");
        heading.setFont(small);
        heading.setForeground(Color.LIGHT_GRAY);

        linkHintLabel.setFont(small);
        linkHintLabel.setForeground(Color.LIGHT_GRAY);
        linkHintLabel.setHorizontalAlignment(SwingConstants.LEFT);
        linkHintLabel.setText(
                "<html>Opens GroupScape in your browser to link this character to your account.</html>");

        linkCharacterButton.addActionListener(e -> {
            if (linkCharacterListener != null) {
                linkCharacterListener.run();
            }
        });

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARK_GRAY_COLOR);
        section.add(heading);
        section.add(linkHintLabel);
        section.add(linkCharacterButton);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.add(section, BorderLayout.NORTH);

        add(content, BorderLayout.NORTH);
    }

    /** Called once at startup with the callback to invoke when the user clicks "Link this character". */
    void setLinkCharacterListener(Runnable listener) {
        this.linkCharacterListener = listener;
    }
}
