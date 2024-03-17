import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

public class Eth_G {
    // Declare the Collisions variable
    private static int Collisions = 0;
    // Declare the collisionCountLabel variable
    private static JLabel collisionCountLabel;
    private static JLabel attemptLabel;
    private static JLabel addressCountLabel;
    private static JTextArea resultTextArea;
    private static JButton pauseButton;
    private static JCheckBox verbosityCheckBox;

    private static Set<String> loadExistingAccounts(String filePath) throws IOException {
        Set<String> existingAccounts = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                existingAccounts.add(line.trim());
            }
        }
        System.out.println("Addresses Loaded: " + existingAccounts.size());
        return existingAccounts;
    }

    private static void updateCollisionCountLabel() {
        SwingUtilities.invokeLater(() -> {
            collisionCountLabel.setText("Collisions: " + Collisions);
        });
    }

    private static void generateEthereumAccount(Set<String> existingAccounts, AtomicInteger attemptCounter, AtomicBoolean isPaused, AtomicBoolean isVerbose, Object lock) {
        Random random = new Random();
        byte[] privateKeyBytes = new byte[32];
        random.nextBytes(privateKeyBytes);
        ECKeyPair ecKeyPair = ECKeyPair.create(privateKeyBytes);
        Credentials credentials = Credentials.create(ecKeyPair);
        String ethereumAddress = credentials.getAddress();

        synchronized (lock) {
            while (isPaused.get()) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            int attempt = attemptCounter.getAndIncrement();
            String result = "Address: " + ethereumAddress + " | Private Key: " + credentials.getEcKeyPair().getPrivateKey().toString(16);
            if (existingAccounts.contains(ethereumAddress)) {
                Collisions += 1;
                updateCollisionCountLabel(); // Update the collision count label
                System.out.println(result);
                resultTextArea.append(result + "\n");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter("Found.txt", true))) {
                    writer.write(result + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (isVerbose.get()) {
                System.out.println("#" + attempt + ": " + result);
                resultTextArea.append("#" + attempt + ": " + result + "\n");
            }
            attemptLabel.setText("Attempt count: " + formatWithDots(attempt));
            resultTextArea.setCaretPosition(resultTextArea.getDocument().getLength());
        }
    }

    private static String formatWithDots(int number) {
        DecimalFormat df = new DecimalFormat("#,###");
        return df.format(number).replace(",", ".");
    }

    private static void worker(Set<String> existingAccounts, AtomicInteger attemptCounter, AtomicBoolean isPaused, AtomicBoolean isVerbose, Object lock) {
        while (true) {
            generateEthereumAccount(existingAccounts, attemptCounter, isPaused, isVerbose, lock);
        }
    }

    public static void main(String[] args) throws IOException {
        JFrame frame = new JFrame("ETH/BSC Collider");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        frame.getContentPane().add(panel);

        JLabel titleLabel = new JLabel("ETH/BSC Collider");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(10, 10, 10, 10);
        panel.add(titleLabel, constraints);

        JButton chooseFileButton = new JButton("Load File");
        chooseFileButton.setBackground(Color.GRAY);
        chooseFileButton.setForeground(Color.YELLOW);
        chooseFileButton.setFont(new Font("Arial", Font.BOLD, 14));
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        panel.add(chooseFileButton, constraints);

        JLabel numProcessesLabel = new JLabel("Number of Processes:");
        numProcessesLabel.setFont(new Font("Arial", Font.BOLD, 14));
        constraints.gridx = 0;
        constraints.gridy = 2;
        panel.add(numProcessesLabel, constraints);

        JTextField numProcessesField = new JTextField("12", 5);
        numProcessesField.setFont(new Font("Arial", Font.PLAIN, 14));
        constraints.gridx = 1;
        constraints.gridy = 2;
        panel.add(numProcessesField, constraints);

        frame.pack();
        frame.setVisible(true);

        chooseFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                frame.dispose();

                int numProcesses = Integer.parseInt(numProcessesField.getText());
                System.out.println("Processes:" + numProcesses);

                try {
                    Set<String> existingAccounts = loadExistingAccounts(filePath);
                    AtomicInteger attemptCounter = new AtomicInteger(1);
                    AtomicBoolean isPaused = new AtomicBoolean(false);
                    AtomicBoolean isVerbose = new AtomicBoolean(false);
                    Object lock = new Object();

                    ExecutorService executor = Executors.newFixedThreadPool(numProcesses);
                    for (int i = 0; i < numProcesses; i++) {
                        executor.execute(() -> worker(existingAccounts, attemptCounter, isPaused, isVerbose, lock));
                    }
                    executor.shutdown();

                    JFrame attemptFrame = new JFrame("ETH/BSC Collider by SayajinPT");
                    attemptFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    attemptFrame.setLayout(new BorderLayout());
                    attemptFrame.setBackground(Color.BLACK);

                    JPanel topPanel = new JPanel(new GridLayout(3, 1));
                    topPanel.setBackground(Color.BLACK);

                    // Label to display the chosen file name
                    JLabel fileNameLabel = new JLabel("File: " + filePath);
                    fileNameLabel.setForeground(Color.YELLOW);
                    fileNameLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
                    topPanel.add(fileNameLabel);

                    // Label to display the number of processes
                    JLabel processesLabel = new JLabel("Processes: " + numProcesses);
                    processesLabel.setForeground(Color.YELLOW);
                    processesLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
                    topPanel.add(processesLabel);

                    // Label to display the addresses loaded value
                    addressCountLabel = new JLabel("Addresses loaded: " + existingAccounts.size());
                    addressCountLabel.setForeground(Color.YELLOW);
                    addressCountLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
                    topPanel.add(addressCountLabel);

                    attemptFrame.add(topPanel, BorderLayout.NORTH);
                    JPanel centerPanel = new JPanel(new BorderLayout());
                    centerPanel.setBackground(Color.BLACK);
                    attemptLabel = new JLabel("Attempt count: 1");
                    attemptLabel.setForeground(Color.ORANGE);
                    attemptLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
                    centerPanel.add(attemptLabel, BorderLayout.NORTH);

                    resultTextArea = new JTextArea(20, 40);
                    resultTextArea.setEditable(true);
                    resultTextArea.setBackground(Color.DARK_GRAY);
                    resultTextArea.setForeground(Color.YELLOW);
                    resultTextArea.setFont(new Font("Arial", Font.PLAIN, 12));
                    JScrollPane scrollPane = new JScrollPane(resultTextArea, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                    centerPanel.add(scrollPane, BorderLayout.CENTER);
                    attemptFrame.add(centerPanel, BorderLayout.CENTER);

                    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                    bottomPanel.setBackground(Color.DARK_GRAY);
                    bottomPanel.setForeground(Color.YELLOW);

                    // Label to display the collisions
                    collisionCountLabel = new JLabel("Collisions: " + Collisions);
                    collisionCountLabel.setForeground(Color.YELLOW);
                    collisionCountLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
                    bottomPanel.add(collisionCountLabel);


                    pauseButton = new JButton("Pause");
                    pauseButton.setBackground(Color.ORANGE);
                    pauseButton.setForeground(Color.BLACK);

                    pauseButton.addActionListener(event -> {
                        if (!isPaused.get()) {
                            pauseButton.setText("Resume");
                            isPaused.set(true);
                        } else {
                            pauseButton.setText("Pause");
                            isPaused.set(false);
                            synchronized (lock) {
                                lock.notifyAll();
                            }
                        }
                    });
                    bottomPanel.add(pauseButton);

                    verbosityCheckBox = new JCheckBox("Verbosity");
                    verbosityCheckBox.setBackground(Color.ORANGE);
                    verbosityCheckBox.setForeground(Color.BLACK);

                    verbosityCheckBox.addActionListener(event -> {
                        isVerbose.set(verbosityCheckBox.isSelected());
                    });
                    bottomPanel.add(verbosityCheckBox);
                    attemptFrame.add(bottomPanel, BorderLayout.SOUTH);

                    attemptFrame.pack();
                    attemptFrame.setVisible(true);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}


