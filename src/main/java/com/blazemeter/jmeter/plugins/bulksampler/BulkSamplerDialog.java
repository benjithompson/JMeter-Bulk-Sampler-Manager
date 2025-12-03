/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazemeter.jmeter.plugins.bulksampler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreeNode;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.CollectionProperty;

/**
 * Dialog for configuring bulk sampler and header operations.
 * Uses a tabbed interface for different operation types.
 */
public class BulkSamplerDialog extends JDialog {

    /**
     * Enum representing the possible actions that can be performed on samplers.
     */
    public enum ActionType {
        DELETE("Delete", "Permanently remove matching samplers from the test plan"),
        DISABLE("Disable", "Disable matching samplers (they will not execute during test runs)"),
        ENABLE("Enable", "Enable matching samplers (previously disabled samplers will execute)");

        private final String displayName;
        private final String description;

        ActionType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Enum representing which tab/operation mode is active.
     */
    public enum OperationMode {
        SAMPLERS,
        HTTP_HEADERS
    }

    // Sampler tab components
    private JTextField uriPatternField;
    private JComboBox<ActionType> actionComboBox;
    private JCheckBox useRegexCheckBox;
    private JCheckBox caseSensitiveCheckBox;
    private JCheckBox invertMatchCheckBox;
    private JList<String> previewList;
    private DefaultListModel<String> previewListModel;
    private JLabel matchCountLabel;
    private JLabel patternErrorLabel;
    private JLabel patternExampleLabel;
    private JLabel actionDescriptionLabel;

    // HTTP Headers tab components
    private JTextField headerPatternField;
    private JCheckBox headerUseRegexCheckBox;
    private JCheckBox headerCaseSensitiveCheckBox;
    private JCheckBox headerInvertMatchCheckBox;
    private JList<String> headerPreviewList;
    private DefaultListModel<String> headerPreviewListModel;
    private JLabel headerMatchCountLabel;
    private JLabel headerPatternErrorLabel;
    private JLabel headerPatternExampleLabel;

    private JTabbedPane tabbedPane;
    private boolean confirmed = false;
    private Timer updateTimer;
    private Timer headerUpdateTimer;
    
    // Scope - the selected nodes to limit operations to (empty = entire test plan)
    private List<JMeterTreeNode> scopeNodes;
    private JLabel scopeLabel;

    private static final String EXAMPLE_SIMPLE = "Example: api/users or login (matches if URI contains text)";
    private static final String EXAMPLE_REGEX = "Example: .*\\/api\\/.*  or  ^https://.*\\.com  (regex pattern)";
    private static final String HEADER_EXAMPLE_SIMPLE = "Example: Authorization or Content-Type (matches header name)";
    private static final String HEADER_EXAMPLE_REGEX = "Example: X-.*  or  ^Accept.*  (regex pattern for header name)";

    /**
     * Creates a new BulkSamplerDialog.
     *
     * @param parent The parent frame
     * @param selectedNodes The currently selected nodes (null or empty for entire test plan)
     */
    public BulkSamplerDialog(Frame parent, List<JMeterTreeNode> selectedNodes) {
        super(parent, "Bulk Edit Manager", true);
        this.scopeNodes = selectedNodes != null ? new ArrayList<>(selectedNodes) : new ArrayList<>();
        initComponents();
        pack();
        setMinimumSize(new Dimension(650, 580));
        setLocationRelativeTo(parent);
        setupEscapeKey();
    }

    /**
     * Sets up the escape key to close the dialog.
     */
    private void setupEscapeKey() {
        getRootPane().registerKeyboardAction(
            e -> {
                confirmed = false;
                dispose();
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    /**
     * Initializes the dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Scope info panel at top
        JPanel scopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        scopePanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        JLabel scopeTitleLabel = new JLabel("Scope:");
        scopeTitleLabel.setFont(scopeTitleLabel.getFont().deriveFont(Font.BOLD));
        scopePanel.add(scopeTitleLabel);
        
        String scopeText;
        if (scopeNodes == null || scopeNodes.isEmpty()) {
            scopeText = "Entire Test Plan";
        } else if (scopeNodes.size() == 1) {
            JMeterTreeNode node = scopeNodes.get(0);
            if (node.getTestElement().getClass().getSimpleName().equals("TestPlan")) {
                scopeText = "Entire Test Plan";
            } else {
                scopeText = node.getName() + " (and children)";
            }
        } else {
            // Multiple nodes selected
            String nodeNames = scopeNodes.stream()
                .map(JMeterTreeNode::getName)
                .collect(Collectors.joining(", "));
            if (nodeNames.length() > 60) {
                nodeNames = nodeNames.substring(0, 57) + "...";
            }
            scopeText = scopeNodes.size() + " selected: " + nodeNames;
        }
        scopeLabel = new JLabel(scopeText);
        scopeLabel.setForeground(new Color(0, 100, 0)); // Dark green
        scopePanel.add(scopeLabel);
        
        add(scopePanel, BorderLayout.NORTH);

        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Samplers", createSamplersTab());
        tabbedPane.addTab("HTTP Headers", createHeadersTab());
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton previewButton = new JButton("Refresh Preview");
        JButton okButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");

        previewButton.addActionListener(e -> {
            if (tabbedPane.getSelectedIndex() == 0) {
                updateSamplerPreview();
            } else {
                updateHeaderPreview();
            }
        });

        okButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(previewButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Set default button
        getRootPane().setDefaultButton(okButton);

        // Setup listeners
        setupSamplerListeners();
        setupHeaderListeners();
    }

    /**
     * Creates the Samplers tab panel.
     */
    private JPanel createSamplersTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Configuration panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Sampler Configuration",
            TitledBorder.LEFT, TitledBorder.TOP));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URI Pattern
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        configPanel.add(new JLabel("URI Pattern:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        uriPatternField = new JTextField(30);
        uriPatternField.setToolTipText("Enter a pattern to match sampler URIs");
        configPanel.add(uriPatternField, gbc);

        // Pattern example label
        gbc.gridy = 1;
        gbc.gridx = 1;
        patternExampleLabel = new JLabel(EXAMPLE_SIMPLE);
        patternExampleLabel.setFont(patternExampleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        patternExampleLabel.setForeground(Color.GRAY);
        configPanel.add(patternExampleLabel, gbc);

        // Pattern error label
        gbc.gridy = 2;
        gbc.gridx = 1;
        patternErrorLabel = new JLabel(" ");
        patternErrorLabel.setForeground(Color.RED);
        patternErrorLabel.setFont(patternErrorLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(patternErrorLabel, gbc);

        // Action Type
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Action:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        actionComboBox = new JComboBox<>(ActionType.values());
        actionComboBox.setSelectedItem(ActionType.DISABLE);
        configPanel.add(actionComboBox, gbc);

        // Action description label
        gbc.gridy = 4;
        gbc.gridx = 1;
        actionDescriptionLabel = new JLabel(ActionType.DISABLE.getDescription());
        actionDescriptionLabel.setFont(actionDescriptionLabel.getFont().deriveFont(Font.ITALIC, 11f));
        actionDescriptionLabel.setForeground(Color.GRAY);
        configPanel.add(actionDescriptionLabel, gbc);

        // Options panel
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        useRegexCheckBox = new JCheckBox("Use Regular Expression");
        caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        invertMatchCheckBox = new JCheckBox("Invert Match");
        invertMatchCheckBox.setToolTipText("Apply action to samplers that do NOT match the pattern");
        optionsPanel.add(useRegexCheckBox);
        optionsPanel.add(caseSensitiveCheckBox);
        optionsPanel.add(invertMatchCheckBox);
        configPanel.add(optionsPanel, gbc);

        panel.add(configPanel, BorderLayout.NORTH);

        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Matching Samplers Preview",
            TitledBorder.LEFT, TitledBorder.TOP));

        previewListModel = new DefaultListModel<>();
        previewList = new JList<>(previewListModel);
        previewList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewList.setVisibleRowCount(8);
        JScrollPane scrollPane = new JScrollPane(previewList);
        scrollPane.setPreferredSize(new Dimension(550, 180));
        previewPanel.add(scrollPane, BorderLayout.CENTER);

        matchCountLabel = new JLabel("Enter a pattern to see matching samplers");
        matchCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        previewPanel.add(matchCountLabel, BorderLayout.SOUTH);

        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the HTTP Headers tab panel.
     */
    private JPanel createHeadersTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Configuration panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Header Configuration",
            TitledBorder.LEFT, TitledBorder.TOP));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Header Name Pattern
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Header Name Pattern:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        headerPatternField = new JTextField(30);
        headerPatternField.setToolTipText("Enter a pattern to match HTTP header names");
        configPanel.add(headerPatternField, gbc);

        // Pattern example label
        gbc.gridy = 1;
        gbc.gridx = 1;
        headerPatternExampleLabel = new JLabel(HEADER_EXAMPLE_SIMPLE);
        headerPatternExampleLabel.setFont(headerPatternExampleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        headerPatternExampleLabel.setForeground(Color.GRAY);
        configPanel.add(headerPatternExampleLabel, gbc);

        // Pattern error label
        gbc.gridy = 2;
        gbc.gridx = 1;
        headerPatternErrorLabel = new JLabel(" ");
        headerPatternErrorLabel.setForeground(Color.RED);
        headerPatternErrorLabel.setFont(headerPatternErrorLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(headerPatternErrorLabel, gbc);

        // Action info
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        JLabel actionInfoLabel = new JLabel("Action: Delete matching header rows from all HTTP Header Managers");
        actionInfoLabel.setFont(actionInfoLabel.getFont().deriveFont(Font.ITALIC, 11f));
        actionInfoLabel.setForeground(Color.GRAY);
        configPanel.add(actionInfoLabel, gbc);

        // Options panel
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerUseRegexCheckBox = new JCheckBox("Use Regular Expression");
        headerCaseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        headerInvertMatchCheckBox = new JCheckBox("Invert Match");
        headerInvertMatchCheckBox.setToolTipText("Delete headers that do NOT match the pattern");
        optionsPanel.add(headerUseRegexCheckBox);
        optionsPanel.add(headerCaseSensitiveCheckBox);
        optionsPanel.add(headerInvertMatchCheckBox);
        configPanel.add(optionsPanel, gbc);

        panel.add(configPanel, BorderLayout.NORTH);

        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Matching Headers Preview",
            TitledBorder.LEFT, TitledBorder.TOP));

        headerPreviewListModel = new DefaultListModel<>();
        headerPreviewList = new JList<>(headerPreviewListModel);
        headerPreviewList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headerPreviewList.setVisibleRowCount(8);
        JScrollPane scrollPane = new JScrollPane(headerPreviewList);
        scrollPane.setPreferredSize(new Dimension(550, 180));
        previewPanel.add(scrollPane, BorderLayout.CENTER);

        headerMatchCountLabel = new JLabel("Enter a pattern to see matching headers");
        headerMatchCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        previewPanel.add(headerMatchCountLabel, BorderLayout.SOUTH);

        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Sets up listeners for the Samplers tab.
     */
    private void setupSamplerListeners() {
        updateTimer = new Timer(300, e -> updateSamplerPreview());
        updateTimer.setRepeats(false);

        uriPatternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleSamplerUpdate(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleSamplerUpdate(); }
            @Override
            public void changedUpdate(DocumentEvent e) { scheduleSamplerUpdate(); }
        });

        useRegexCheckBox.addActionListener(e -> {
            patternExampleLabel.setText(useRegexCheckBox.isSelected() ? EXAMPLE_REGEX : EXAMPLE_SIMPLE);
            updateSamplerPreview();
        });
        caseSensitiveCheckBox.addActionListener(e -> updateSamplerPreview());
        invertMatchCheckBox.addActionListener(e -> updateSamplerPreview());

        actionComboBox.addActionListener(e -> {
            ActionType selected = (ActionType) actionComboBox.getSelectedItem();
            if (selected != null) {
                actionDescriptionLabel.setText(selected.getDescription());
            }
        });
    }

    /**
     * Sets up listeners for the HTTP Headers tab.
     */
    private void setupHeaderListeners() {
        headerUpdateTimer = new Timer(300, e -> updateHeaderPreview());
        headerUpdateTimer.setRepeats(false);

        headerPatternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleHeaderUpdate(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleHeaderUpdate(); }
            @Override
            public void changedUpdate(DocumentEvent e) { scheduleHeaderUpdate(); }
        });

        headerUseRegexCheckBox.addActionListener(e -> {
            headerPatternExampleLabel.setText(headerUseRegexCheckBox.isSelected() ? HEADER_EXAMPLE_REGEX : HEADER_EXAMPLE_SIMPLE);
            updateHeaderPreview();
        });
        headerCaseSensitiveCheckBox.addActionListener(e -> updateHeaderPreview());
        headerInvertMatchCheckBox.addActionListener(e -> updateHeaderPreview());
    }

    private void scheduleSamplerUpdate() {
        if (updateTimer.isRunning()) {
            updateTimer.restart();
        } else {
            updateTimer.start();
        }
    }

    private void scheduleHeaderUpdate() {
        if (headerUpdateTimer.isRunning()) {
            headerUpdateTimer.restart();
        } else {
            headerUpdateTimer.start();
        }
    }

    /**
     * Updates the sampler preview list.
     */
    private void updateSamplerPreview() {
        previewListModel.clear();
        patternErrorLabel.setText(" ");

        String pattern = uriPatternField.getText().trim();
        if (pattern.isEmpty()) {
            matchCountLabel.setText("Enter a pattern to see matching samplers");
            return;
        }

        if (useRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                patternErrorLabel.setText("Invalid regex: " + e.getDescription());
                matchCountLabel.setText("Fix the pattern error above");
                return;
            }
        }

        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getTreeModel() == null) {
            matchCountLabel.setText("Unable to access test plan");
            return;
        }

        List<String> matches = findMatchingSamplerNames(guiPackage, pattern, 
            useRegexCheckBox.isSelected(), caseSensitiveCheckBox.isSelected(),
            invertMatchCheckBox.isSelected());

        for (String match : matches) {
            previewListModel.addElement(match);
        }

        if (matches.isEmpty()) {
            matchCountLabel.setText("No samplers match the pattern");
        } else {
            matchCountLabel.setText(String.format("Found %d matching sampler(s)", matches.size()));
        }
    }

    /**
     * Updates the header preview list.
     */
    private void updateHeaderPreview() {
        headerPreviewListModel.clear();
        headerPatternErrorLabel.setText(" ");

        String pattern = headerPatternField.getText().trim();
        if (pattern.isEmpty()) {
            headerMatchCountLabel.setText("Enter a pattern to see matching headers");
            return;
        }

        if (headerUseRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                headerPatternErrorLabel.setText("Invalid regex: " + e.getDescription());
                headerMatchCountLabel.setText("Fix the pattern error above");
                return;
            }
        }

        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getTreeModel() == null) {
            headerMatchCountLabel.setText("Unable to access test plan");
            return;
        }

        List<String> matches = findMatchingHeaderNames(guiPackage, pattern, 
            headerUseRegexCheckBox.isSelected(), headerCaseSensitiveCheckBox.isSelected(),
            headerInvertMatchCheckBox.isSelected());

        for (String match : matches) {
            headerPreviewListModel.addElement(match);
        }

        if (matches.isEmpty()) {
            headerMatchCountLabel.setText("No headers match the pattern");
        } else {
            headerMatchCountLabel.setText(String.format("Found %d matching header(s)", matches.size()));
        }
    }

    /**
     * Finds sampler names matching the pattern.
     */
    private List<String> findMatchingSamplerNames(GuiPackage guiPackage, String uriPattern,
            boolean useRegex, boolean caseSensitive, boolean invertMatch) {
        
        List<String> results = new ArrayList<>();
        JMeterTreeNode rootNode = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        
        Pattern pattern = null;
        if (useRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(uriPattern, flags);
        }

        // If no scope nodes, search entire test plan
        if (scopeNodes == null || scopeNodes.isEmpty()) {
            findMatchingSamplersRecursive(rootNode, uriPattern, pattern, caseSensitive, invertMatch, results);
        } else {
            // Search within each selected scope node
            for (JMeterTreeNode scopeNode : scopeNodes) {
                findMatchingSamplersRecursive(scopeNode, uriPattern, pattern, caseSensitive, invertMatch, results);
            }
        }
        return results;
    }

    private void findMatchingSamplersRecursive(JMeterTreeNode node, String uriPattern,
            Pattern pattern, boolean caseSensitive, boolean invertMatch, List<String> results) {
        
        TestElement element = node.getTestElement();
        
        if (element instanceof Sampler) {
            String uri = extractUri(element);
            if (uri != null) {
                boolean matches = matchesPattern(uri, uriPattern, pattern, caseSensitive);
                if (invertMatch) {
                    matches = !matches;
                }
                if (matches) {
                    String status = element.isEnabled() ? "" : " [DISABLED]";
                    results.add(element.getName() + " → " + uri + status);
                }
            }
        }

        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findMatchingSamplersRecursive(child, uriPattern, pattern, caseSensitive, invertMatch, results);
        }
    }

    /**
     * Finds header names matching the pattern.
     */
    private List<String> findMatchingHeaderNames(GuiPackage guiPackage, String headerPattern,
            boolean useRegex, boolean caseSensitive, boolean invertMatch) {
        
        List<String> results = new ArrayList<>();
        JMeterTreeNode rootNode = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        
        Pattern pattern = null;
        if (useRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(headerPattern, flags);
        }

        // If no scope nodes, search entire test plan
        if (scopeNodes == null || scopeNodes.isEmpty()) {
            findMatchingHeadersRecursive(rootNode, headerPattern, pattern, caseSensitive, invertMatch, results);
        } else {
            // Search within each selected scope node
            for (JMeterTreeNode scopeNode : scopeNodes) {
                findMatchingHeadersRecursive(scopeNode, headerPattern, pattern, caseSensitive, invertMatch, results);
            }
        }
        return results;
    }

    private void findMatchingHeadersRecursive(JMeterTreeNode node, String headerPattern,
            Pattern pattern, boolean caseSensitive, boolean invertMatch, List<String> results) {
        
        TestElement element = node.getTestElement();
        
        if (element instanceof HeaderManager headerManager) {
            CollectionProperty headers = headerManager.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                JMeterProperty prop = headers.get(i);
                if (prop.getObjectValue() instanceof Header header) {
                    String headerName = header.getName();
                    boolean matches = matchesPattern(headerName, headerPattern, pattern, caseSensitive);
                    if (invertMatch) {
                        matches = !matches;
                    }
                    if (matches) {
                        String managerName = headerManager.getName();
                        String headerValue = header.getValue();
                        // Truncate long values
                        if (headerValue.length() > 50) {
                            headerValue = headerValue.substring(0, 47) + "...";
                        }
                        results.add(String.format("[%s] %s: %s", managerName, headerName, headerValue));
                    }
                }
            }
        }

        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findMatchingHeadersRecursive(child, headerPattern, pattern, caseSensitive, invertMatch, results);
        }
    }

    private String extractUri(TestElement element) {
        StringBuilder searchableText = new StringBuilder();
        
        String name = element.getName();
        if (name != null) {
            searchableText.append(name);
        }
        
        if (element instanceof HTTPSamplerBase httpSampler) {
            String path = httpSampler.getPath();
            String domain = httpSampler.getDomain();
            
            if (domain != null && !domain.isEmpty()) {
                String protocol = httpSampler.getProtocol();
                if (protocol == null || protocol.isEmpty()) {
                    protocol = "http";
                }
                int port = httpSampler.getPort();
                StringBuilder uri = new StringBuilder();
                uri.append(protocol).append("://").append(domain);
                if (port > 0 && port != 80 && port != 443) {
                    uri.append(":").append(port);
                }
                if (path != null && !path.isEmpty()) {
                    if (!path.startsWith("/")) {
                        uri.append("/");
                    }
                    uri.append(path);
                }
                String constructedUri = uri.toString();
                if (!constructedUri.equals(name)) {
                    searchableText.append(" ").append(constructedUri);
                }
            } else if (path != null && !path.isEmpty() && !path.equals(name)) {
                searchableText.append(" ").append(path);
            }
        }
        
        return searchableText.toString();
    }

    private boolean matchesPattern(String text, String patternStr, Pattern pattern, boolean caseSensitive) {
        if (text == null) {
            return false;
        }
        
        if (pattern != null) {
            return pattern.matcher(text).find();
        } else {
            if (caseSensitive) {
                return text.contains(patternStr);
            } else {
                return text.toLowerCase().contains(patternStr.toLowerCase());
            }
        }
    }

    /**
     * Validates the input before applying.
     */
    private boolean validateInput() {
        if (tabbedPane.getSelectedIndex() == 0) {
            return validateSamplerInput();
        } else {
            return validateHeaderInput();
        }
    }

    private boolean validateSamplerInput() {
        String pattern = uriPatternField.getText().trim();
        
        if (pattern.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a URI pattern.",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            uriPatternField.requestFocus();
            return false;
        }

        if (useRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid regular expression: " + e.getMessage(),
                    "Pattern Error",
                    JOptionPane.ERROR_MESSAGE);
                uriPatternField.requestFocus();
                return false;
            }
        }

        if (actionComboBox.getSelectedItem() == ActionType.DELETE) {
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to DELETE matching samplers?\n" +
                "This action cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            return result == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private boolean validateHeaderInput() {
        String pattern = headerPatternField.getText().trim();
        
        if (pattern.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a header name pattern.",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            headerPatternField.requestFocus();
            return false;
        }

        if (headerUseRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid regular expression: " + e.getMessage(),
                    "Pattern Error",
                    JOptionPane.ERROR_MESSAGE);
                headerPatternField.requestFocus();
                return false;
            }
        }

        int result = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to DELETE matching header rows?\n" +
            "This action cannot be undone.",
            "Confirm Delete Headers",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    // ==================== Public Getters ====================

    public boolean isConfirmed() {
        return confirmed;
    }

    public OperationMode getOperationMode() {
        return tabbedPane.getSelectedIndex() == 0 ? OperationMode.SAMPLERS : OperationMode.HTTP_HEADERS;
    }

    // Sampler getters
    public String getUriPattern() {
        return uriPatternField.getText().trim();
    }

    public ActionType getSelectedAction() {
        return (ActionType) actionComboBox.getSelectedItem();
    }

    public boolean isUseRegex() {
        return useRegexCheckBox.isSelected();
    }

    public boolean isCaseSensitive() {
        return caseSensitiveCheckBox.isSelected();
    }

    public boolean isInvertMatch() {
        return invertMatchCheckBox.isSelected();
    }

    // Header getters
    public String getHeaderPattern() {
        return headerPatternField.getText().trim();
    }

    public boolean isHeaderUseRegex() {
        return headerUseRegexCheckBox.isSelected();
    }

    public boolean isHeaderCaseSensitive() {
        return headerCaseSensitiveCheckBox.isSelected();
    }

    public boolean isHeaderInvertMatch() {
        return headerInvertMatchCheckBox.isSelected();
    }

    // Scope getter
    public List<JMeterTreeNode> getScopeNodes() {
        return scopeNodes;
    }
}
