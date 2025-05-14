package com.example.bdsqltester.scenes.admin;

import com.example.bdsqltester.datasources.GradingDataSource;
import com.example.bdsqltester.datasources.MainDataSource;
import com.example.bdsqltester.dtos.Assignment;
import com.example.bdsqltester.dtos.Grade;
import com.example.bdsqltester.dtos.User;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;

public class AdminController {

    @FXML
    private TextArea answerKeyField;

    @FXML
    private ListView<Assignment> assignmentList;

    @FXML
    private TextField idField;

    @FXML
    private TextArea instructionsField;

    @FXML
    private TextField nameField;

    @FXML
    private Button deleteButton;

    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupIdField();
        setupAssignmentList();
        setupDeleteButton();
        refreshAssignmentList();
    }

    private void setupIdField() {
        idField.setEditable(false);
        idField.setMouseTransparent(true);
        idField.setFocusTraversable(false);
    }

    private void setupAssignmentList() {
        assignmentList.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name);
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (selected) {
                    onAssignmentSelected(getItem());
                }
            }
        });
    }

    private void setupDeleteButton() {
        deleteButton.setOnAction(this::onDeleteAssignmentClick);
        deleteButton.setDisable(true);
    }

    void refreshAssignmentList() {
        assignments.clear();
        try (Connection c = MainDataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM assignments ORDER BY name")) {

            while (rs.next()) {
                assignments.add(new Assignment(rs));
            }
        } catch (Exception e) {
            showAlert("Error", "Database Error", "Failed to load assignments: " + e.getMessage());
        }

        assignmentList.setItems(assignments);
        reselectCurrentAssignment();
    }

    private void reselectCurrentAssignment() {
        try {
            if (!idField.getText().isEmpty()) {
                long id = Long.parseLong(idField.getText());
                assignments.stream()
                        .filter(assignment -> assignment.id == id)
                        .findFirst()
                        .ifPresent(assignment -> assignmentList.getSelectionModel().select(assignment));
            }
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
    }

    void onAssignmentSelected(Assignment assignment) {
        if (assignment == null) return;

        idField.setText(String.valueOf(assignment.id));
        nameField.setText(assignment.name);
        instructionsField.setText(assignment.instructions);
        answerKeyField.setText(assignment.answerKey);
        deleteButton.setDisable(false);
    }

    @FXML
    void onNewAssignmentClick(ActionEvent event) {
        clearForm();
    }

    @FXML
    void onSaveClick(ActionEvent event) {
        if (!validateInput()) return;

        try (Connection c = MainDataSource.getConnection()) {
            if (idField.getText().isEmpty()) {
                createNewAssignment(c);
            } else {
                updateExistingAssignment(c);
            }
            refreshAssignmentList();
        } catch (Exception e) {
            showAlert("Error", "Database Error", "Failed to save assignment: " + e.getMessage());
        }
    }

    private boolean validateInput() {
        if (nameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Missing Name", "Assignment name cannot be empty");
            return false;
        }
        return true;
    }

    private void createNewAssignment(Connection c) throws SQLException {
        String sql = "INSERT INTO assignments (name, instructions, answer_key) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nameField.getText());
            stmt.setString(2, instructionsField.getText());
            stmt.setString(3, answerKeyField.getText());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    idField.setText(String.valueOf(rs.getLong(1)));
                }
            }
        }
    }

    private void updateExistingAssignment(Connection c) throws SQLException {
        String sql = "UPDATE assignments SET name = ?, instructions = ?, answer_key = ? WHERE id = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, nameField.getText());
            stmt.setString(2, instructionsField.getText());
            stmt.setString(3, answerKeyField.getText());
            stmt.setLong(4, Long.parseLong(idField.getText()));
            stmt.executeUpdate();
        }
    }

    @FXML
    void onShowGradesClick(ActionEvent event) {
        if (idField.getText().isEmpty()) {
            showAlert("Error", "No Assignment Selected", "Please select an assignment to view grades.");
            return;
        }

        Stage gradeStage = new Stage();
        gradeStage.setTitle("Grades for Assignment #" + idField.getText());

        TableView<Grade> gradeTable = createGradeTable();
        gradeTable.setItems(fetchGradeFromDatabase());

        StackPane root = new StackPane(gradeTable);
        gradeStage.setScene(new Scene(root, 600, 400));
        gradeStage.show();
    }

    private TableView<Grade> createGradeTable() {
        TableView<Grade> table = new TableView<>();

        TableColumn<Grade, Long> userIdCol = new TableColumn<>("User ID");
        userIdCol.setCellValueFactory(new PropertyValueFactory<>("userId"));

        TableColumn<Grade, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        TableColumn<Grade, Double> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(new PropertyValueFactory<>("score"));

        table.getColumns().addAll(userIdCol, usernameCol, gradeCol);
        return table;
    }

    private ObservableList<Grade> fetchGradeFromDatabase() {
        ObservableList<Grade> gradeList = FXCollections.observableArrayList();
        String sql = "SELECT g.user_id, u.username, g.grade " +
                "FROM grades g JOIN users u ON g.user_id = u.id " +
                "WHERE g.assignment_id = ?";

        try (Connection c = MainDataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(idField.getText()));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade grade = new Grade();
                    grade.setUserId(rs.getLong("user_id"));
                    grade.setScore(rs.getDouble("grade"));
                    grade.setUsername(rs.getString("username"));
                    grade.setAssignmentId(Long.parseLong(idField.getText()));
                    gradeList.add(grade);
                }
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load grades", e.getMessage());
        }
        return gradeList;
    }

    @FXML
    void onShowCompletionClick(ActionEvent event) {
        if (idField.getText().isEmpty()) {
            showAlert("Error", "No Assignment Selected", "Please select an assignment to view completion status.");
            return;
        }

        Stage completionStage = new Stage();
        completionStage.setTitle("Completion Status for Assignment #" + idField.getText());

        TabPane tabPane = new TabPane();

        Tab statsTab = new Tab("Statistics");
        statsTab.setContent(createCompletionStatsView());

        Tab missingTab = new Tab("Missing Students");
        missingTab.setContent(createMissingStudentsView());

        tabPane.getTabs().addAll(statsTab, missingTab);

        completionStage.setScene(new Scene(tabPane, 600, 400));
        completionStage.show();
    }

    private StackPane createCompletionStatsView() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        try (Connection c = MainDataSource.getConnection()) {
            String totalStudentsSql = "SELECT COUNT(*) FROM users WHERE role = 'student'";
            int totalStudents = 0;
            try (Statement stmt = c.createStatement();
                 ResultSet rs = stmt.executeQuery(totalStudentsSql)) {
                if (rs.next()) {
                    totalStudents = rs.getInt(1);
                }
            }

            String completedSql = "SELECT COUNT(DISTINCT user_id) FROM grades WHERE assignment_id = ?";
            int completedStudents = 0;
            try (PreparedStatement stmt = c.prepareStatement(completedSql)) {
                stmt.setLong(1, Long.parseLong(idField.getText()));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        completedStudents = rs.getInt(1);
                    }
                }
            }

            double percentage = totalStudents > 0 ? (completedStudents * 100.0) / totalStudents : 0;

            Label totalLabel = new Label("Total Students: " + totalStudents);
            Label completedLabel = new Label("Completed: " + completedStudents);
            Label missingLabel = new Label("Not Completed: " + (totalStudents - completedStudents));
            Label percentageLabel = new Label(String.format("Completion Rate: %.1f%%", percentage));

            percentageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

            vbox.getChildren().addAll(totalLabel, completedLabel, missingLabel, percentageLabel);

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load completion stats", e.getMessage());
            vbox.getChildren().add(new Label("Error loading data"));
        }

        return new StackPane(vbox);
    }

    private StackPane createMissingStudentsView() {
        TableView<User> table = new TableView<>();

        TableColumn<User, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<User, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        table.getColumns().addAll(idCol, usernameCol);

        try (Connection c = MainDataSource.getConnection()) {
            String sql = "SELECT u.id, u.username FROM users u " +
                    "WHERE u.role = 'student' AND u.id NOT IN " +
                    "(SELECT g.user_id FROM grades g WHERE g.assignment_id = ?) " +
                    "ORDER BY u.username";

            ObservableList<User> missingStudents = FXCollections.observableArrayList();

            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idField.getText()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        User user = new User();
                        user.setId(rs.getLong("id"));
                        user.setUsername(rs.getString("username"));
                        missingStudents.add(user);
                    }
                }
            }

            table.setItems(missingStudents);

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load missing students", e.getMessage());
        }

        return new StackPane(table);
    }

    @FXML
    void onShowAverageGradesClick(ActionEvent event) {
        Stage averageGradeStage = new Stage();
        averageGradeStage.setTitle("Average Grade per Task");

        TableView<TaskAverageGrade> averageGradeTable = createAverageGradeTable();
        averageGradeTable.setItems(fetchAverageGradesFromDatabase());

        StackPane root = new StackPane(averageGradeTable);
        averageGradeStage.setScene(new Scene(root, 600, 400));
        averageGradeStage.show();
    }

    private TableView<TaskAverageGrade> createAverageGradeTable() {
        TableView<TaskAverageGrade> table = new TableView<>();

        TableColumn<TaskAverageGrade, String> assignmentNameCol = new TableColumn<>("Assignment");
        assignmentNameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAssignmentName()));

        TableColumn<TaskAverageGrade, Double> averageGradeCol = new TableColumn<>("Avg Grade");
        averageGradeCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().getAverageGrade()).asObject());

        TableColumn<TaskAverageGrade, Integer> completedCol = new TableColumn<>("Completed");
        completedCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getCompletedCount()).asObject());

        TableColumn<TaskAverageGrade, Integer> totalCol = new TableColumn<>("Total Students");
        totalCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getTotalStudents()).asObject());

        table.getColumns().addAll(assignmentNameCol, averageGradeCol, completedCol, totalCol);
        return table;
    }

    private ObservableList<TaskAverageGrade> fetchAverageGradesFromDatabase() {
        ObservableList<TaskAverageGrade> averageGradeList = FXCollections.observableArrayList();
        String sql = "SELECT a.id, a.name AS assignment_name, AVG(g.grade) AS average_grade, " +
                "COUNT(DISTINCT g.user_id) AS completed_count, " +
                "(SELECT COUNT(*) FROM users WHERE role = 'student') AS total_students " +
                "FROM assignments a " +
                "LEFT JOIN grades g ON a.id = g.assignment_id " +
                "GROUP BY a.id, a.name " +
                "ORDER BY a.name";

        try (Connection c = MainDataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                TaskAverageGrade averageGrade = new TaskAverageGrade();
                averageGrade.setAssignmentName(rs.getString("assignment_name"));
                averageGrade.setAverageGrade(rs.getDouble("average_grade"));
                averageGrade.setCompletedCount(rs.getInt("completed_count"));
                averageGrade.setTotalStudents(rs.getInt("total_students"));
                averageGradeList.add(averageGrade);
            }

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load average grades", e.getMessage());
        }
        return averageGradeList;
    }

    @FXML
    void onTestButtonClick(ActionEvent event) {
        if (answerKeyField.getText().trim().isEmpty()) {
            showAlert("Error", "Empty Query", "Please enter an SQL query to test");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Query Results - " + nameField.getText());

        TableView<ArrayList<String>> tableView = new TableView<>();
        ObservableList<ArrayList<String>> data = FXCollections.observableArrayList();

        try (Connection conn = GradingDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(answerKeyField.getText())) {

            ResultSetMetaData metaData = rs.getMetaData();
            createTableColumns(tableView, metaData);
            populateTableData(data, rs, metaData);

            if (data.isEmpty()) {
                showAlert("Information", "No Results", "Query executed successfully but returned no data.");
                return;
            }

            tableView.setItems(data);
            stage.setScene(new Scene(new StackPane(tableView), 800, 600));
            stage.show();

        } catch (SQLException e) {
            showAlert("Database Error", "Query Failed", "SQL Error: " + e.getMessage());
        }
    }

    private void createTableColumns(TableView<ArrayList<String>> tableView, ResultSetMetaData metaData) throws SQLException {
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            final int colIdx = i - 1;
            String header = metaData.getColumnLabel(i);

            TableColumn<ArrayList<String>, String> col = new TableColumn<>(header);
            col.setCellValueFactory(cellData ->
                    new SimpleStringProperty(
                            cellData.getValue().size() > colIdx ?
                                    cellData.getValue().get(colIdx) : ""
                    )
            );
            col.setPrefWidth(120);
            tableView.getColumns().add(col);
        }
    }

    private void populateTableData(ObservableList<ArrayList<String>> data, ResultSet rs, ResultSetMetaData metaData) throws SQLException {
        while (rs.next()) {
            ArrayList<String> row = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                row.add(rs.getString(i) != null ? rs.getString(i) : "");
            }
            data.add(row);
        }
    }

    @FXML
    void onDeleteClick(ActionEvent event) {
        onDeleteAssignmentClick(event);
    }

    @FXML
    void onDeleteAssignmentClick(ActionEvent event) {
        if (idField.getText().isEmpty()) {
            showAlert("Error", "No Selection", "Please select an assignment to delete");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete '" + nameField.getText() + "'?",
                ButtonType.YES, ButtonType.NO);

        confirmation.setHeaderText("Confirm Deletion");
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                deleteAssignment();
            }
        });
    }

    private void deleteAssignment() {
        try (Connection c = MainDataSource.getConnection()) {
            deleteGrades(c);

            if (deleteAssignment(c) > 0) {
                showAlert("Success", "Deleted", "Assignment deleted successfully");
                clearForm();
                refreshAssignmentList();
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Delete Failed", "Could not delete: " + e.getMessage());
        }
    }

    private void deleteGrades(Connection c) throws SQLException {
        String sql = "DELETE FROM grades WHERE assignment_id = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, Long.parseLong(idField.getText()));
            stmt.executeUpdate();
        }
    }

    private int deleteAssignment(Connection c) throws SQLException {
        String sql = "DELETE FROM assignments WHERE id = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, Long.parseLong(idField.getText()));
            return stmt.executeUpdate();
        }
    }

    private void clearForm() {
        idField.clear();
        nameField.clear();
        instructionsField.clear();
        answerKeyField.clear();
        deleteButton.setDisable(true);
        assignmentList.getSelectionModel().clearSelection();
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class TaskAverageGrade {
        private final SimpleStringProperty assignmentName;
        private final SimpleDoubleProperty averageGrade;
        private final SimpleIntegerProperty completedCount;
        private final SimpleIntegerProperty totalStudents;

        public TaskAverageGrade() {
            this.assignmentName = new SimpleStringProperty();
            this.averageGrade = new SimpleDoubleProperty();
            this.completedCount = new SimpleIntegerProperty();
            this.totalStudents = new SimpleIntegerProperty();
        }

        public String getAssignmentName() {
            return assignmentName.get();
        }

        public SimpleStringProperty assignmentNameProperty() {
            return assignmentName;
        }

        public void setAssignmentName(String assignmentName) {
            this.assignmentName.set(assignmentName);
        }

        public double getAverageGrade() {
            return averageGrade.get();
        }

        public SimpleDoubleProperty averageGradeProperty() {
            return averageGrade;
        }

        public void setAverageGrade(double averageGrade) {
            this.averageGrade.set(averageGrade);
        }

        public int getCompletedCount() {
            return completedCount.get();
        }

        public SimpleIntegerProperty completedCountProperty() {
            return completedCount;
        }

        public void setCompletedCount(int completedCount) {
            this.completedCount.set(completedCount);
        }

        public int getTotalStudents() {
            return totalStudents.get();
        }

        public SimpleIntegerProperty totalStudentsProperty() {
            return totalStudents;
        }

        public void setTotalStudents(int totalStudents) {
            this.totalStudents.set(totalStudents);
        }
    }
}