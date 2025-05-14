package com.example.bdsqltester.scenes.user;

import com.example.bdsqltester.datasources.MainDataSource;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AverageViewController {
    @FXML
    private TableView<UserController.AssignmentScore> assignmentTable;
    @FXML
    private Label averageScoreLabel;
    @FXML
    private Button refreshButton;

    private Long userId;

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @FXML
    public void initialize() {
        assignmentTable.getColumns().get(0).setCellValueFactory(new PropertyValueFactory<>("assignmentName"));
        assignmentTable.getColumns().get(1).setCellValueFactory(new PropertyValueFactory<>("score"));
        refreshButton.setOnAction(event -> loadData());
    }

    public void loadData() {
        ObservableList<UserController.AssignmentScore> scores = FXCollections.observableArrayList();

        try (Connection c = MainDataSource.getConnection()) {
            String query = "SELECT a.name, g.grade FROM grades g " +
                    "JOIN assignments a ON g.assignment_id = a.id " +
                    "WHERE g.user_id = ?";
            PreparedStatement stmt = c.prepareStatement(query);
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            double total = 0;

            while (rs.next()) {
                String name = rs.getString("name");
                double grade = rs.getDouble("grade");
                scores.add(new UserController.AssignmentScore(name, String.valueOf(grade)));
                total += grade;
                count++;
            }

            assignmentTable.setItems(scores);
            averageScoreLabel.setText(count > 0 ?
                    String.format("Average Score: %.2f", total / count) :
                    "Average Score: N/A");

        } catch (Exception t) {
            new Alert(Alert.AlertType.ERROR, t.getMessage(), ButtonType.OK).showAndWait();
        }
    }
}
