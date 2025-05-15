package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserController {

    // Main UI Components
    @FXML private Label assignmentNameLabel;
    @FXML private TextArea assignmentInstructionsArea;
    @FXML private TextArea userAnswerArea;
    @FXML private ListView<Assignment> assignmentListView;
    @FXML private Label gradeLabel;

    // Data fields
    private Long loggedInUserId;
    private Assignment currentAssignment;
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();

    public void setLoggedInUserId(Long userId) {
        this.loggedInUserId = userId;
        refreshAssignmentList();
    }

    @FXML
    void initialize() {
        assignmentListView.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name);
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    currentAssignment = getItem();
                    if (currentAssignment != null) {
                        displayAssignmentDetails(currentAssignment);
                        loadUserGrade(currentAssignment.id);
                    } else {
                        clearAssignmentDetails();
                        gradeLabel.setText("Grade: -");
                    }
                }
            }
        });
    }

    void refreshAssignmentList() {
        assignments.clear();
        try (Connection c = MainDataSource.getConnection()) {
            Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM assignments");
            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load assignments.", e.toString());
        }
        assignmentListView.setItems(assignments);
    }

    void displayAssignmentDetails(Assignment assignment) {
        assignmentNameLabel.setText(assignment.name);
        assignmentInstructionsArea.setText(assignment.instructions);
        userAnswerArea.clear();
    }

    void clearAssignmentDetails() {
        assignmentNameLabel.setText("");
        assignmentInstructionsArea.setText("");
        userAnswerArea.clear();
    }

    void loadUserGrade(Long assignmentId) {
        if (loggedInUserId != null && assignmentId != null) {
            try (Connection c = MainDataSource.getConnection()) {
                String query = "SELECT grade FROM grades WHERE user_id = ? AND assignment_id = ?";
                PreparedStatement stmt = c.prepareStatement(query);
                stmt.setLong(1, loggedInUserId);
                stmt.setLong(2, assignmentId);
                ResultSet rs = stmt.executeQuery();
                gradeLabel.setText(rs.next() ? "Grade: " + rs.getDouble("grade") : "Grade: -");
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to load grade", e.toString());
                gradeLabel.setText("Grade: Error");
            }
        } else {
            gradeLabel.setText("Grade: -");
        }
    }

    @FXML
    void onTestButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment first.");
            return;
        }

        Stage stage = new Stage();
        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();
        ArrayList<String> headers = new ArrayList<>();

        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(userAnswerArea.getText())) {

            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                final int columnIndex = i - 1;
                String headerText = metaData.getColumnLabel(i);
                headers.add(headerText);
                TableColumn<ArrayList<String>, String> column = new TableColumn<>(headerText);
                column.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().get(columnIndex)));
                tableView.getColumns().add(column);
            }

            while (rs.next()) {
                ArrayList<String> row = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.add(rs.getString(i) != null ? rs.getString(i) : "");
                }
                data.add(row);
            }

            tableView.setItems(data);
            StackPane root = new StackPane(tableView);
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Query Results");
            stage.show();

        } catch (SQLException e) {
            showAlert("Database Error", "Query Failed", e.getMessage());
        }
    }

    @FXML
    void onSubmitButtonClick(ActionEvent event) {
        if (currentAssignment == null) {
            showAlert("Warning", "No Assignment Selected", "Please select an assignment first.");
            return;
        }

        String userAnswer = userAnswerArea.getText();
        int newGrade = calculateGrade(userAnswer, currentAssignment.answerKey);

        try (Connection c = MainDataSource.getConnection()) {
            // Gunakan query yang hanya update jika nilai baru lebih tinggi
            String query = "INSERT INTO grades (user_id, assignment_id, grade) " +
                    "VALUES (?, ?, ?) " +
                    "ON CONFLICT (user_id, assignment_id) DO UPDATE SET " +
                    "grade = CASE WHEN EXCLUDED.grade > grades.grade THEN EXCLUDED.grade ELSE grades.grade END";

            PreparedStatement stmt = c.prepareStatement(query);
            stmt.setLong(1, loggedInUserId);
            stmt.setLong(2, currentAssignment.id);
            stmt.setInt(3, newGrade);
            stmt.executeUpdate();

            // Tampilkan nilai yang baru didapatkan dan nilai terbaik
            gradeLabel.setText("Grade: " + newGrade);
            showAlert("Submission Successful", null, "Your grade for this submission: " + newGrade);
            loadUserGrade(currentAssignment.id); // Muat ulang nilai terbaik dari database untuk label (opsional, tapi baik untuk sinkronisasi)
        } catch (SQLException e) {
            showAlert("Database Error", "Submission Failed", e.getMessage());
        }
    }

    @FXML
    void onSeeAverageClick() {
        try {
            URL fxmlUrl = getClass().getResource("/com/example/bdsqltester/average-view.fxml");
            if (fxmlUrl == null) {
                throw new IOException("FXML file not found at: /com/example/bdsqltester/average-view.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setControllerFactory(param -> {
                if (param == AverageViewController.class) {
                    AverageViewController controller = new AverageViewController();
                    controller.setUserId(loggedInUserId);
                    return controller;
                }
                try {
                    return param.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("Assignment Scores");

            AverageViewController controller = loader.getController();
            controller.loadData();

            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to load view",
                    "Could not load average-view.fxml\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    private int calculateGrade(String userAnswer, String correctAnswer) {
        List<String> userResults = executeQuery(userAnswer);
        List<String> correctResults = executeQuery(correctAnswer);

        // Exact match - 100%
        if (userResults.equals(correctResults)) return 100;

        // Convert to sets for unordered comparison (50% score)
        Set<String> userSet = new HashSet<>(userResults);
        Set<String> correctSet = new HashSet<>(correctResults);

        if (userSet.equals(correctSet)) return 50;

        return 0;
    }

    private List<String> executeQuery(String query) {
        List<String> results = new ArrayList<>();
        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    if (i > 1) row.append(",");
                    row.append(rs.getString(i) != null ? rs.getString(i) : "");
                }
                results.add(row.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class AssignmentScore {
        private final String assignmentName;
        private final String score;

        public AssignmentScore(String assignmentName, String score) {
            this.assignmentName = assignmentName;
            this.score = score;
        }

        public String getAssignmentName() { return assignmentName; }
        public String getScore() { return score; }
    }
}