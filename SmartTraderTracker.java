
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;


public class SmartTraderTracker extends JFrame {
    private JTextField nameField, emailField, dobField, phoneField, countryField, accountIdField, searchField;
    private JButton uploadButton, sortByProfitBtn, undoBtn, searchButton, exportCsvButton, logoutButton, exportPdfButton, saveButton, loadButton, statsButton;
    private JTable tradeTable;
    private DefaultTableModel tableModel;
    private JLabel summaryLabel, dateTimeLabel, profileSummaryLabel;

    private LinkedList<Trade> tradeList = new LinkedList<>();
    private Stack<Trade> undoStack = new Stack<>();
    private String loggedInUser = "";
    private UserProfile currentProfile;

    public SmartTraderTracker() {
        try { UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel()); } catch (Exception ignored) {}
        loginPrompt();
        if (loggedInUser == null || loggedInUser.isEmpty()) System.exit(0);
        setTitle("Smart Trader Tracker - Logged in as: " + loggedInUser);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 850);
        setLayout(new BorderLayout());
        buildUI();
        loadUserProfile();
    }

    private void buildUI() {
        JPanel inputPanel = new JPanel(new GridLayout(11, 2));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Trader Profile"));

        nameField = new JTextField(loggedInUser); nameField.setEditable(false);
        emailField = new JTextField(); setHint(emailField, "example@gmail.com");
        dobField = new JTextField(); setHint(dobField, "DD-MM-YYYY");
        phoneField = new JTextField(); setHint(phoneField, "1234567890");
        countryField = new JTextField(); setHint(countryField, "Country Name");
        accountIdField = new JTextField(); setHint(accountIdField, "12345678");

        inputPanel.add(new JLabel("Trader Name:")); inputPanel.add(nameField);
        inputPanel.add(new JLabel("Email (e.g. user@gmail.com):")); inputPanel.add(emailField);
        inputPanel.add(new JLabel("Date of Birth (dd-mm-yyyy):")); inputPanel.add(dobField);
        inputPanel.add(new JLabel("Phone Number (10 digits):")); inputPanel.add(phoneField);
        inputPanel.add(new JLabel("Country:")); inputPanel.add(countryField);
        inputPanel.add(new JLabel("Account ID (8 characters):")); inputPanel.add(accountIdField);

        uploadButton = new JButton("Upload Trade CSV");
        exportPdfButton = new JButton("Export PDF Report");
        exportCsvButton = new JButton("Export as CSV");
        logoutButton = new JButton("Logout");

        uploadButton.addActionListener(e -> { if (!validateFormBeforeUpload()) return; uploadCSV(); });
        exportPdfButton.addActionListener(e -> { if (!validateFormBeforeUpload()) return; exportPDF(); });
        exportCsvButton.addActionListener(e -> { if (!validateFormBeforeUpload()) return; exportCSV(); });
        logoutButton.addActionListener(e -> logout());

        inputPanel.add(uploadButton); inputPanel.add(exportPdfButton);
        inputPanel.add(exportCsvButton); inputPanel.add(logoutButton);
        add(inputPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"#", "Symbol", "Type", "Entry", "Exit", "Profit", "Date", "Note"}, 0);
        tradeTable = new JTable(tableModel);
        add(new JScrollPane(tradeTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(6, 1));
        searchField = new JTextField(15);
        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchTrades());
        JPanel searchPanel = new JPanel();
        searchPanel.add(new JLabel("Search Symbol/Type:"));
        searchPanel.add(searchField); searchPanel.add(searchButton);
        bottomPanel.add(searchPanel);

        sortByProfitBtn = new JButton("Sort by Profit");
        undoBtn = new JButton("Undo Last Trade");
        saveButton = new JButton("Save Trades");
        loadButton = new JButton("Load Trades");
        sortByProfitBtn.addActionListener(e -> sortTradesByProfit());
        undoBtn.addActionListener(e -> undoLastTrade());
        saveButton.addActionListener(e -> { if (!validateFormBeforeUpload()) return; saveTrades(); });
        loadButton.addActionListener(e -> loadTrades());
        JPanel controlPanel = new JPanel();
        controlPanel.add(sortByProfitBtn); controlPanel.add(undoBtn);
        controlPanel.add(saveButton); controlPanel.add(loadButton);
        bottomPanel.add(controlPanel);

        statsButton = new JButton("Show Stats");
        statsButton.addActionListener(e -> showStatistics());
        JPanel statsPanel = new JPanel(); statsPanel.add(statsButton);
        bottomPanel.add(statsPanel);

        summaryLabel = new JLabel("Summary: ");
        dateTimeLabel = new JLabel("Date/Time: ");
        profileSummaryLabel = new JLabel("Trader Info: ");
        JPanel infoPanel = new JPanel(new GridLayout(1, 2));
        infoPanel.add(summaryLabel); infoPanel.add(dateTimeLabel);
        bottomPanel.add(infoPanel);
        bottomPanel.add(profileSummaryLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        new javax.swing.Timer(1000, e -> updateDateTime()).start();
    }

    private void setHint(JTextField field, String hint) {
        field.setForeground(Color.GRAY);
        field.setText(hint);
        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(hint)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setForeground(Color.GRAY);
                    field.setText(hint);
                }
            }
        });
    }

    private boolean validateFormBeforeUpload() {
    String email = emailField.getText().trim();
    String phone = phoneField.getText().trim();
    String dob = dobField.getText().trim();
    String accountId = accountIdField.getText().trim();

    if (email.equals("example@gmail.com") || !email.endsWith("@gmail.com")) {
        showError("Please enter a valid email ending with @gmail.com", "Invalid Email");
        return false;
    }

    if (phone.equals("1234567890") || !phone.matches("\\d{10}")) {
        showError("Phone number must be exactly 10 digits.", "Invalid Phone");
        return false;
    }

    if (dob.equals("DD-MM-YYYY") || !dob.matches("^\\d{2}-\\d{2}-\\d{4}$")) {
        showError("DOB must be in DD-MM-YYYY format.", "Invalid DOB");
        return false;
    }

    if (accountId.equals("12345678") || accountId.length() != 8) {
        showError("Account ID must be exactly 8 characters.", "Invalid Account ID");
        return false;
    }

    currentProfile = new UserProfile(
        email,
        dob,
        phone,
        countryField.getText().trim(),
        accountId
    );
    saveUserProfile();
    return true;
}

private void showError(String message, String title) {
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
}


    private String getNoteForTrade(Trade t) {
        if (t.profit > 100) return "Great trade!";
        else if (t.profit > 0) return "Good job.";
        else if (t.profit < -100) return "High loss. Review setup.";
        else return "Be cautious.";
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            this.dispose();
            new SmartTraderTracker(); // restarts the app
        }
    }

    private PdfPCell createCell(String text, com.itextpdf.text.Font font, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        return cell;
    }

    private void saveUserProfile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(loggedInUser + "_profile.dat"))) {
            out.writeObject(currentProfile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save profile: " + e.getMessage());
        }
    }

   private void loadUserProfile() {
    File profileFile = new File(loggedInUser + "_profile.dat");

    if (!profileFile.exists()) return;

    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(profileFile))) {
        currentProfile = (UserProfile) in.readObject();

        // Fill form with saved data
        emailField.setText(currentProfile.email);
        dobField.setText(currentProfile.dob);
        phoneField.setText(currentProfile.phone);
        countryField.setText(currentProfile.country);
        accountIdField.setText(currentProfile.accountId);

    } catch (IOException | ClassNotFoundException e) {
        showError("Failed to load profile: " + e.getMessage(), "Load Error");
    }
}

   private void updateSummary() {
    double totalProfit = 0;
    int winCount = 0;

    for (Trade t : tradeList) {
        totalProfit += t.profit;
        if (t.profit > 0) winCount++;
    }

    int totalTrades = tradeList.size();
    double winRate = totalTrades > 0 ? (100.0 * winCount / totalTrades) : 0;

    summaryLabel.setText(String.format(
        "Summary: Trades: %d | Win Rate: %.2f%% | Net Profit: %.2f",
        totalTrades, winRate, totalProfit
    ));

    profileSummaryLabel.setText(String.format(
        "Trader Info: Email=%s, DOB=%s, Phone=%s, Country=%s, AccountID=%s",
        emailField.getText().trim(), dobField.getText().trim(),
        phoneField.getText().trim(), countryField.getText().trim(),
        accountIdField.getText().trim()
    ));
}


    private void updateDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateTimeLabel.setText("Date/Time: " + sdf.format(new Date()));
    }

    private void showStatistics() {
    if (tradeList.isEmpty()) {
        JOptionPane.showMessageDialog(this, "No trade data available.");
        return;
    }

    double totalProfit = 0, totalWin = 0, totalLoss = 0;
    double maxProfit = Double.NEGATIVE_INFINITY;
    double maxLoss = Double.POSITIVE_INFINITY;
    int winCount = 0, lossCount = 0;

    for (Trade t : tradeList) {
        totalProfit += t.profit;

        if (t.profit > 0) {
            winCount++;
            totalWin += t.profit;
        } else if (t.profit < 0) {
            lossCount++;
            totalLoss += t.profit;
        }

        maxProfit = Math.max(maxProfit, t.profit);
        maxLoss = Math.min(maxLoss, t.profit);
    }

    int totalTrades = tradeList.size();
    double avgWin = winCount > 0 ? totalWin / winCount : 0;
    double avgLoss = lossCount > 0 ? totalLoss / lossCount : 0;
    double winRate = (100.0 * winCount) / totalTrades;

    String statsMessage = String.format(
        "📊 Advanced Trade Statistics:\n" +
        "------------------------------\n" +
        "Total Trades      : %d\n" +
        "Win Trades        : %d\n" +
        "Loss Trades       : %d\n" +
        "Win Rate          : %.2f%%\n" +
        "Total Net Profit  : %.2f\n" +
        "Max Profit Trade  : %.2f\n" +
        "Max Loss Trade    : %.2f\n" +
        "Avg Profit (Wins) : %.2f\n" +
        "Avg Loss (Losses) : %.2f",
        totalTrades, winCount, lossCount, winRate, totalProfit,
        maxProfit, maxLoss, avgWin, avgLoss
    );

    JOptionPane.showMessageDialog(this, statsMessage, "Advanced Stats", JOptionPane.INFORMATION_MESSAGE);
}


    private void sortTradesByProfit() {
        tradeList.sort(Comparator.comparingDouble(t -> -t.profit));
        reloadTable();
    }

    private void undoLastTrade() {
        if (!undoStack.isEmpty()) {
            Trade removed = undoStack.pop();
            tradeList.remove(removed);
            reloadTable();
        }
    }

    private void saveTrades() {
    try (ObjectOutputStream out = new ObjectOutputStream(
            new FileOutputStream(loggedInUser + "_trades.dat"))) {
        
        out.writeObject(tradeList);
        JOptionPane.showMessageDialog(this, "Trades saved.");

    } catch (IOException e) {
        JOptionPane.showMessageDialog(this, "Save error: " + e.getMessage());
    }
    }


    private void loadTrades() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(loggedInUser + "_trades.dat"))) {
            tradeList = (LinkedList<Trade>) in.readObject();
            reloadTable();
        } catch (IOException | ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this, "Load error: " + e.getMessage());
        }
    }

    private void reloadTable() {
        tableModel.setRowCount(0);
        int count = 1;
        for (Trade t : tradeList) {
            tableModel.addRow(new Object[]{count++, t.symbol, t.type, t.entry, t.exit, t.profit, t.date, getNoteForTrade(t)});
        }
        updateSummary();
    }

    private void exportCSV() {
    try (PrintWriter writer = new PrintWriter(new FileWriter(loggedInUser + "_trades_export.csv"))) {
        writer.println("Symbol,Type,Entry,Exit,Profit,Date,Note");
        for (Trade t : tradeList) {
            writer.printf("%s,%s,%.2f,%.2f,%.2f,%s,%s%n",
                    t.symbol, t.type, t.entry, t.exit, t.profit, t.date, getNoteForTrade(t));
        }

        JOptionPane.showMessageDialog(this, "Exported successfully.");

    } catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
    }
}


    private void searchTrades() {
        String query = searchField.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        int count = 1;
        for (Trade t : tradeList) {
            if (t.symbol.toLowerCase().contains(query) || t.type.toLowerCase().contains(query)) {
                tableModel.addRow(new Object[]{count++, t.symbol, t.type, t.entry, t.exit, t.profit, t.date, getNoteForTrade(t)});
            }
        }
    }

    private void exportPDF() {
    try {
        Document doc = new Document();
        PdfWriter.getInstance(doc, new FileOutputStream(loggedInUser + "_report.pdf"));
        doc.open();

        com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
        com.itextpdf.text.Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.DARK_GRAY);
        com.itextpdf.text.Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        Paragraph title = new Paragraph("📄 Smart Trader Report - " + loggedInUser, titleFont);
        title.setSpacingAfter(10);
        doc.add(title);
        doc.add(new Paragraph("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), textFont));
        doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("👤 Trader Info:", sectionFont));
        doc.add(new Paragraph("Email      : " + emailField.getText(), textFont));
        doc.add(new Paragraph("DOB        : " + dobField.getText(), textFont));
        doc.add(new Paragraph("Phone      : " + phoneField.getText(), textFont));
        doc.add(new Paragraph("Country    : " + countryField.getText(), textFont));
        doc.add(new Paragraph("Account ID : " + accountIdField.getText(), textFont));
        doc.add(Chunk.NEWLINE);

        double totalProfit = 0, maxProfit = Double.NEGATIVE_INFINITY, maxLoss = Double.POSITIVE_INFINITY;
        int winCount = 0, lossCount = 0;
        double totalWin = 0, totalLoss = 0;

        for (Trade t : tradeList) {
            totalProfit += t.profit;
            if (t.profit > 0) {
                winCount++;
                totalWin += t.profit;
            } else if (t.profit < 0) {
                lossCount++;
                totalLoss += t.profit;
            }
            maxProfit = Math.max(maxProfit, t.profit);
            maxLoss = Math.min(maxLoss, t.profit);
        }

        int totalTrades = tradeList.size();
        double avgWin = winCount > 0 ? totalWin / winCount : 0;
        double avgLoss = lossCount > 0 ? totalLoss / lossCount : 0;
        double avgOverall = totalTrades > 0 ? totalProfit / totalTrades : 0;
        double winRate = totalTrades > 0 ? (100.0 * winCount / totalTrades) : 0;

        doc.add(new Paragraph("📊 Summary Stats:", sectionFont));
        doc.add(new Paragraph("Total Trades        : " + totalTrades, textFont));
        doc.add(new Paragraph("Winning Trades      : " + winCount, textFont));
        doc.add(new Paragraph("Losing Trades       : " + lossCount, textFont));
        doc.add(new Paragraph("Win Rate (%)        : " + String.format("%.2f", winRate), textFont));
        doc.add(new Paragraph("Total Profit        : " + String.format("%.2f", totalProfit), textFont));
        doc.add(new Paragraph("Total Winning Amt   : " + String.format("%.2f", totalWin), textFont));
        doc.add(new Paragraph("Total Losing Amt    : " + String.format("%.2f", totalLoss), textFont));
        doc.add(new Paragraph("Max Profit Trade    : " + String.format("%.2f", maxProfit), textFont));
        doc.add(new Paragraph("Max Loss Trade      : " + String.format("%.2f", maxLoss), textFont));
        doc.add(new Paragraph("Avg Profit (Wins)   : " + String.format("%.2f", avgWin), textFont));
        doc.add(new Paragraph("Avg Loss (Losses)   : " + String.format("%.2f", avgLoss), textFont));
        doc.add(new Paragraph("Avg Profit/Trade    : " + String.format("%.2f", avgOverall), textFont));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);

        String[] headers = {"#", "Symbol", "Type", "Entry", "Exit", "Profit", "Date", "Note"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, sectionFont));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setPadding(5);
            table.addCell(cell);
        }

        int count = 1;
        for (Trade t : tradeList) {
            BaseColor rowColor = t.profit > 0 ? new BaseColor(200, 255, 200) :
                                 (t.profit < 0 ? new BaseColor(255, 200, 200) : BaseColor.WHITE);

            table.addCell(createCell(String.valueOf(count++), textFont, rowColor));
            table.addCell(createCell(t.symbol, textFont, rowColor));
            table.addCell(createCell(t.type, textFont, rowColor));
            table.addCell(createCell(String.valueOf(t.entry), textFont, rowColor));
            table.addCell(createCell(String.valueOf(t.exit), textFont, rowColor));
            table.addCell(createCell(String.valueOf(t.profit), textFont, rowColor));
            table.addCell(createCell(t.date, textFont, rowColor));
            table.addCell(createCell(getNoteForTrade(t), textFont, rowColor));
        }

        doc.add(table);
        doc.close();
        JOptionPane.showMessageDialog(this, "🎉 PDF exported with color and modern layout!");

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "❌ Export failed: " + e.getMessage());
    }
}


    private void uploadCSV() {
    JFileChooser fileChooser = new JFileChooser();
    
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            tableModel.setRowCount(0);
            tradeList.clear();
            undoStack.clear();

            br.readLine(); // Skip header
            String line;
            int count = 1;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");

                if (parts.length == 6) {
                    Trade t = new Trade(
                        parts[0],
                        parts[1],
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]),
                        parts[5]
                    );

                    tradeList.add(t);
                    undoStack.push(t);

                    tableModel.addRow(new Object[]{
                        count++, t.symbol, t.type, t.entry, t.exit, t.profit, t.date, getNoteForTrade(t)
                    });
                }
            }

            updateSummary();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error reading file: " + ex.getMessage());
        }
    }
}

    private void loginPrompt() {
    JPanel loginPanel = new JPanel(new GridLayout(2, 2));
    JTextField usernameField = new JTextField();
    JPasswordField passwordField = new JPasswordField();

    loginPanel.add(new JLabel("Trader Name:"));
    loginPanel.add(usernameField);
    loginPanel.add(new JLabel("Password:"));
    loginPanel.add(passwordField);

    // Show login prompt
    int option = JOptionPane.showConfirmDialog(null, loginPanel, "Login", JOptionPane.OK_CANCEL_OPTION);
    if (option == JOptionPane.OK_OPTION) {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        File userFile = new File(user + "_login.dat");

        if (userFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                String storedPass = reader.readLine();
                if (storedPass.equals(pass)) {
                    loggedInUser = user;
                } else {
                    JOptionPane.showMessageDialog(null, "Incorrect password.");
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error reading login file.");
            }
        } else {
            int create = JOptionPane.showConfirmDialog(null, "New user. Create account?", "Create", JOptionPane.YES_NO_OPTION);
            if (create == JOptionPane.YES_OPTION) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(userFile))) {
                    writer.println(pass);
                    loggedInUser = user;
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, "Error saving new user.");
                }
            }
        }
    }
}


    public static void main(String[] args) {
    while (true) {
        SmartTraderTracker app = new SmartTraderTracker();
        if (app.loggedInUser != null && !app.loggedInUser.isEmpty()) {
            app.setVisible(true);
            break;
        }
    }
    }


    static class Trade implements Serializable {
        String symbol, type, date;
        double entry, exit, profit;
        Trade(String symbol, String type, double entry, double exit, double profit, String date) {
            this.symbol = symbol;
            this.type = type;
            this.entry = entry;
            this.exit = exit;
            this.profit = profit;
            this.date = date;
        }
    }

    static class UserProfile implements Serializable {
        String email, dob, phone, country, accountId;
        UserProfile(String email, String dob, String phone, String country, String accountId) {
            this.email = email;
            this.dob = dob;
            this.phone = phone;
            this.country = country;
            this.accountId = accountId;
        }
    }
}
