package com.example.plan;

import com.google.gson.annotations.SerializedName;
import java.util.Date;
import java.util.List;

// 主模型类：旅行方案
public class TravelPlan {
    @SerializedName("_id")
    private String id; // MongoDB自动生成的ID，无需手动设置

    @SerializedName("plan_id")
    private String planId;

    private String title;

    @SerializedName("creator_id")
    private int creatorId;

    @SerializedName("collaborators")
    private List<Collaborator> collaborators;

    private String status;

    private Content content;

    @SerializedName("version_history")
    private List<VersionHistory> versionHistory;

    private List<String> tags;

    @SerializedName("created_at")
    private Date createdAt;

    @SerializedName("updated_at")
    private Date updatedAt;

    // 构造函数（不含_id，由MongoDB自动生成）
    public TravelPlan(String planId, String title, int creatorId, List<Collaborator> collaborators,
                      String status, Content content, List<VersionHistory> versionHistory,
                      List<String> tags, Date createdAt, Date updatedAt) {
        this.planId = planId;
        this.title = title;
        this.creatorId = creatorId;
        this.collaborators = collaborators;
        this.status = status;
        this.content = content;
        this.versionHistory = versionHistory;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    public String getId() {
        return id;
    }

    // 补充 planId 的 getter 方法
    public String getPlanId() {
        return planId;
    }

    // 补充 title 的 getter 方法
    public String getTitle() {
        return title;
    }

    // 补充 creatorId 的 getter 方法
    public int getCreatorId() {
        return creatorId;
    }

    // Getter和Setter方法（省略，需自行生成）
    public void setId(String id) {
        this.id = id;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public List<Collaborator> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(List<Collaborator> collaborators) {
        this.collaborators = collaborators;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public List<VersionHistory> getVersionHistory() {
        return versionHistory;
    }

    public void setVersionHistory(List<VersionHistory> versionHistory) {
        this.versionHistory = versionHistory;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}

// 协作者子模型
class Collaborator {
    @SerializedName("user_id")
    private int userId;
    private String role;

    public Collaborator(int userId, String role) {
        this.userId = userId;
        this.role = role;
    }
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

}

// 方案内容子模型


// 每日行程子模型


// 活动子模型


// 交通子模型
class Transport {
    private int time;
    private String currency;

    public Transport(int time, String currency) {
        this.time = time;
        this.currency = currency;
    }

    // Getter和Setter
    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

// 版本历史子模型
class VersionHistory {
    @SerializedName("editor_id")
    public int editorId;

    @SerializedName("edit_time")
    public Date editTime;

    @SerializedName("change_log")
    public String changeLog;

    public VersionHistory(int editorId, Date editTime, String changeLog) {
        this.editorId = editorId;
        this.editTime = editTime;
        this.changeLog = changeLog;
    }

    // Getter和Setter
    public int getEditorId() {
        return editorId;
    }

    public void setEditorId(int editorId) {
        this.editorId = editorId;
    }

    public Date getEditTime() {
        return editTime;
    }

    public void setEditTime(Date editTime) {
        this.editTime = editTime;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }
}
