package org.anthropocene.htf.launcher;

import org.anthropocene.htf.Main;
import org.anthropocene.htf.core.Paths;
import org.anthropocene.htf.core.Settings;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * The launcher: news, Play, NASA data download, settings, and the game console.
 * Run without a display (or with --nogui) and it starts the game directly.
 */
public final class Launcher {
    static final String VERSION = "1.0.0";
    static final Color ACCENT = new Color(0x4DA3FF), ACCENT_DARK = new Color(0x2563B0), NAVY = new Color(0x0B1320), PANEL = new Color(0x111B2B);

    private final LauncherConfig cfg = LauncherConfig.load();
    private final Settings settings = Settings.load();
    private final GameProcess game = new GameProcess();
    private final JTextArea console = new JTextArea();
    private JButton play;
    private JFrame frame;

    public static void main(String[] args) {
        boolean noGui = GraphicsEnvironment.isHeadless() || List.of(args).contains("--nogui");
        if (noGui) { Main.main(args); return; }
        SwingUtilities.invokeLater(() -> new Launcher().show());
    }

    private void show() {
        try {
            com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(java.util.Map.of("@accentColor", "#4DA3FF", "@background", "#0B1320", "@foreground", "#EEF4FB"));
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("TabbedPane.tabHeight", 38);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        } catch (RuntimeException ignored) { /* fall back to the default look */ }
        frame = new JFrame("Hold the Field Launcher " + VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(banner(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Play", playTab());
        tabs.addTab("NASA Data", dataTab());
        tabs.addTab("Settings", settingsTab());
        tabs.addTab("Console", consoleTab());
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusBar(), BorderLayout.SOUTH);
        frame.setSize(860, 580);
        frame.setMinimumSize(new Dimension(700, 480));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        devShot(tabs);
    }

    /** Developer hook: -Dhtf.shot=DIR writes a PNG of each tab, then exits. */
    private void devShot(JTabbedPane tabs) {
        String dir = System.getProperty("htf.shot");
        if (dir == null) return;
        new javax.swing.Timer(900, e -> {
            try {
                java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    tabs.setSelectedIndex(i);
                    tabs.paintImmediately(0, 0, tabs.getWidth(), tabs.getHeight());
                    frame.getRootPane().paintImmediately(0, 0, frame.getWidth(), frame.getHeight());
                    Thread.sleep(700);
                    BufferedImage img = new Robot().createScreenCapture(new Rectangle(frame.getLocationOnScreen(), frame.getSize()));
                    javax.imageio.ImageIO.write(img, "png", java.nio.file.Path.of(dir, "launcher-" + i + "-" + tabs.getTitleAt(i).replace(' ', '-') + ".png").toFile());
                }
            } catch (Exception ex) { ex.printStackTrace(); }
            System.exit(0);
        }) {{ setRepeats(false); }}.start();
    }

    // ------------------------------------------------------------------ banner

    private JComponent banner() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setPaint(new GradientPaint(0, 0, new Color(0x16263F), 0, h, new Color(0x6E5B52)));   // dusk sky
                g.fillRect(0, 0, w, h);
                g.setPaint(new RadialGradientPaint(w * 0.78f, h * 0.86f, h * 1.1f, new float[]{0f, 1f}, new Color[]{new Color(0xFFB36B, false), new Color(0xFFB36B & 0xFFFFFF, false)}));
                g.setColor(new Color(255, 190, 120, 70));
                g.fillOval((int) (w * 0.78) - 150, (int) (h * 0.86) - 150, 300, 300);
                // layered hills
                int[] cols = {0x1A2A44, 0x14223A, 0x0F1B2E};
                for (int layer = 0; layer < 3; layer++) {
                    Path2D hill = new Path2D.Double();
                    hill.moveTo(0, h);
                    for (int x = 0; x <= w; x += 8) {
                        double y = h * (0.52 + layer * 0.12) - Math.sin(x * 0.011 + layer * 2.1) * 12 * (1 + layer * 0.2) - Math.sin(x * 0.027 + layer) * 6;
                        hill.lineTo(x, y);
                    }
                    hill.lineTo(w, h);
                    g.setColor(new Color(cols[layer]));
                    g.fill(hill);
                }
                // paddy rows
                g.setColor(new Color(0x0D1727));
                g.fillRect(0, (int) (h * 0.86), w, h);
                g.setColor(new Color(0xE6BE4B, false));
                g.setColor(new Color(230, 190, 75, 55));
                for (int i = 0; i < 6; i++) g.drawLine(0, (int) (h * 0.88) + i * 3, w, (int) (h * 0.88) + i * 3);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                g.setColor(ACCENT);
                g.drawString("NASA SPACE APPS CHALLENGE 2026  /  FIELD SHIFT", 32, 26);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
                g.setColor(new Color(0, 0, 0, 120));
                g.drawString("Hold the ", 34, 72);
                g.setColor(Color.WHITE);
                g.drawString("Hold the ", 32, 70);
                int tw = g.getFontMetrics().stringWidth("Hold the ");
                g.setColor(new Color(0xE6BE4B));
                g.drawString("Field", 32 + tw, 70);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                g.setColor(new Color(0xB4C2D6));
                g.drawString("Version " + VERSION + "   |   Team Anthropocene", 34, 92);
            }
        };
        p.setPreferredSize(new Dimension(100, 106));
        return p;
    }

    // ------------------------------------------------------------------ tabs

    private JComponent playTab() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JTextArea news = new JTextArea(readNews());
        news.setEditable(false); news.setLineWrap(true); news.setWrapStyleWord(true);
        news.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        news.setBorder(new EmptyBorder(8, 10, 8, 10));
        news.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(news);
        sp.setBorder(BorderFactory.createTitledBorder("What's new"));
        root.add(sp, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(230, 100));
        play = new JButton("PLAY");
        play.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        play.setBackground(ACCENT_DARK); play.setForeground(Color.WHITE); play.setFocusPainted(false);
        play.putClientProperty("JButton.buttonType", "roundRect");
        play.setAlignmentX(Component.CENTER_ALIGNMENT);
        play.setMaximumSize(new Dimension(230, 78));
        play.addActionListener(e -> launch());
        side.add(play);
        side.add(Box.createVerticalStrut(12));
        side.add(label("Version " + VERSION));
        side.add(label("Java " + System.getProperty("java.version")));
        side.add(label("Memory limit " + cfg.ramMb + " MB"));
        side.add(Box.createVerticalStrut(12));
        JButton folder = new JButton("Open game folder");
        folder.setAlignmentX(Component.CENTER_ALIGNMENT);
        folder.addActionListener(e -> openFolder());
        side.add(folder);
        root.add(side, BorderLayout.EAST);
        return root;
    }

    private static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JComponent dataTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JTextArea intro = new JTextArea("The game ships with SAMPLE weather so it always starts. Download the real season from NASA POWER "
                + "(Daily API, community AG: precipitation, maximum temperature, root-zone soil wetness). You need an internet connection once; "
                + "after that the game works offline. Files are stored in " + Paths.dataDir());
        intro.setEditable(false); intro.setLineWrap(true); intro.setWrapStyleWord(true); intro.setOpaque(false);
        root.add(intro, BorderLayout.NORTH);

        JTextArea log = new JTextArea();
        log.setEditable(false); log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        List<DataUpdater.Target> targets;
        try { targets = DataUpdater.targets(); } catch (IOException e) { targets = List.of(); log.append("Could not read targets: " + e.getMessage() + "\n"); }
        JLabel[] statusLabels = new JLabel[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            statusLabels[i] = new JLabel(targets.get(i).label() + ":  " + DataUpdater.status(targets.get(i)));
            statusLabels[i].setBorder(new EmptyBorder(2, 0, 2, 0));
            list.add(statusLabels[i]);
        }
        top.add(list, BorderLayout.CENTER);
        JButton update = new JButton("Update NASA data");
        update.setBackground(ACCENT_DARK); update.setForeground(Color.WHITE); update.setFocusPainted(false);
        update.putClientProperty("JButton.buttonType", "roundRect");
        JProgressBar bar = new JProgressBar();
        bar.setVisible(false); bar.setIndeterminate(true);
        JPanel east = new JPanel(new BorderLayout(4, 4));
        east.add(update, BorderLayout.NORTH);
        east.add(bar, BorderLayout.SOUTH);
        top.add(east, BorderLayout.EAST);
        root.add(top, BorderLayout.CENTER);
        JScrollPane lsp = new JScrollPane(log);
        lsp.setPreferredSize(new Dimension(100, 220));
        lsp.setBorder(BorderFactory.createTitledBorder("Download log"));
        root.add(lsp, BorderLayout.SOUTH);

        final List<DataUpdater.Target> tg = targets;
        update.addActionListener(e -> {
            update.setEnabled(false); bar.setVisible(true);
            new SwingWorker<Integer, String>() {
                @Override protected Integer doInBackground() throws Exception { return DataUpdater.updateAll(this::publish); }
                @Override protected void process(List<String> chunks) { for (String c : chunks) log.append(c + "\n"); }
                @Override protected void done() {
                    update.setEnabled(true); bar.setVisible(false);
                    try { log.append("Done: " + get() + " season file(s) updated.\n"); }
                    catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        log.append("FAILED: " + c.getMessage() + "\nCheck your internet connection and try again.\n");
                    }
                    for (int i = 0; i < tg.size(); i++) statusLabels[i].setText(tg.get(i).label() + ":  " + DataUpdater.status(tg.get(i)));
                }
            }.execute();
        });
        return root;
    }

    private JComponent settingsTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6); c.anchor = GridBagConstraints.WEST; c.gridx = 0; c.gridy = 0;

        String[] res = {"1280x720", "1366x768", "1600x900", "1920x1080", "2560x1440"};
        JComboBox<String> resBox = new JComboBox<>(res);
        resBox.setEditable(true);
        resBox.setSelectedItem(settings.windowWidth + "x" + settings.windowHeight);
        p.add(new JLabel("Window size"), c); c.gridx = 1; p.add(resBox, c);

        c.gridx = 0; c.gridy++;
        JComboBox<String> ramBox = new JComboBox<>(new String[]{"512 MB", "1 GB", "2 GB", "4 GB", "8 GB"});
        int[] ramMb = {512, 1024, 2048, 4096, 8192};
        for (int i = 0; i < ramMb.length; i++) if (ramMb[i] == cfg.ramMb) ramBox.setSelectedIndex(i);
        p.add(new JLabel("Memory (RAM) limit"), c); c.gridx = 1; p.add(ramBox, c);

        c.gridx = 0; c.gridy++;
        JCheckBox fs = new JCheckBox("Start in fullscreen", settings.fullscreen);
        p.add(fs, c); c.gridx = 1;
        JCheckBox vsync = new JCheckBox("VSync", settings.vsync);
        p.add(vsync, c);

        c.gridx = 0; c.gridy++;
        JCheckBox close = new JCheckBox("Close the launcher when the game starts", cfg.closeOnLaunch);
        c.gridwidth = 2; p.add(close, c); c.gridwidth = 1;

        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        p.add(new JLabel("In-game options (controls, volume, render distance...) are in the game's Options menu."), c);

        c.gridy++;
        JButton save = new JButton("Save settings");
        p.add(save, c);
        c.gridy++; c.weighty = 1;
        p.add(Box.createVerticalGlue(), c);

        save.addActionListener(e -> {
            try {
                String[] wh = String.valueOf(resBox.getSelectedItem()).toLowerCase().trim().split("x");
                settings.windowWidth = Math.max(640, Integer.parseInt(wh[0].trim()));
                settings.windowHeight = Math.max(480, Integer.parseInt(wh[1].trim()));
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(frame, "Window size must look like 1280x720", "Hold the Field", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cfg.ramMb = ramMb[Math.max(0, ramBox.getSelectedIndex())];
            cfg.closeOnLaunch = close.isSelected();
            settings.fullscreen = fs.isSelected();
            settings.vsync = vsync.isSelected();
            settings.save(); cfg.save();
            JOptionPane.showMessageDialog(frame, "Saved.", "Hold the Field", JOptionPane.INFORMATION_MESSAGE);
        });
        return p;
    }

    private JComponent consoleTab() {
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        console.setBackground(new Color(0x0A111C)); console.setForeground(new Color(0xB9D0E8));
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        p.add(new JScrollPane(console), BorderLayout.CENTER);
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> console.setText(""));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(clear);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JComponent statusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 10, 4, 10));
        p.add(new JLabel("Data: NASA POWER  |  Game folder: " + Paths.home()), BorderLayout.WEST);
        return p;
    }

    // ------------------------------------------------------------------ actions

    private void launch() {
        if (game.running()) return;
        play.setEnabled(false);
        play.setText("RUNNING...");
        try {
            game.launch(cfg, line -> SwingUtilities.invokeLater(() -> { console.append(line + "\n"); console.setCaretPosition(console.getDocument().getLength()); }),
                    code -> SwingUtilities.invokeLater(() -> {
                        console.append("Game exited with code " + code + "\n");
                        play.setEnabled(true); play.setText("PLAY");
                        if (code != 0) JOptionPane.showMessageDialog(frame, "The game closed with an error (code " + code + ").\nSee the Console tab for details.", "Hold the Field", JOptionPane.WARNING_MESSAGE);
                        frame.setVisible(true);
                    }));
            if (cfg.closeOnLaunch) frame.setVisible(false);
        } catch (IOException | RuntimeException e) {
            console.append("Could not start the game: " + e.getMessage() + "\n");
            play.setEnabled(true); play.setText("PLAY");
        }
    }

    private void openFolder() {
        try { Desktop.getDesktop().open(Paths.home().toFile()); }
        catch (Exception e) { JOptionPane.showMessageDialog(frame, "Game folder:\n" + Paths.home(), "Hold the Field", JOptionPane.INFORMATION_MESSAGE); }
    }

    private static String readNews() {
        try (InputStream in = Launcher.class.getResourceAsStream("/news.txt")) {
            return in == null ? "Welcome to Hold the Field." : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { return "Welcome to Hold the Field."; }
    }
}
