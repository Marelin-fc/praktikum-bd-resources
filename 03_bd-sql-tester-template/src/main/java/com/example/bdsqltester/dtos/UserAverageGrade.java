package com.example.bdsqltester.dtos;

public class UserAverageGrade {
    private Long userId;
    private String username;
    private Double averageGrade;

    public UserAverageGrade() {
        // Konstruktor default
    }

    public UserAverageGrade(Long userId, String username, Double averageGrade) {
        this.userId = userId;
        this.username = username;
        this.averageGrade = averageGrade;
    }

    // Getter untuk userId
    public Long getUserId() {
        return userId;
    }

    // Setter untuk userId
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    // Getter untuk username
    public String getUsername() {
        return username;
    }

    // Setter untuk username
    public void setUsername(String username) {
        this.username = username;
    }

    // Getter untuk averageGrade
    public Double getAverageGrade() {
        return averageGrade;
    }

    // Setter untuk averageGrade
    public void setAverageGrade(Double averageGrade) {
        this.averageGrade = averageGrade;
    }

    @Override
    public String toString() {
        return "UserAverageGrade{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", averageGrade=" + averageGrade +
                '}';
    }
}