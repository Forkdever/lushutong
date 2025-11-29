package com.example.lushutong

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.*

data class TravelPlan(
    @SerializedName("planId") val planId: String,
    @SerializedName("title") val title: String,
    @SerializedName("creatorId") val creatorId: Int,
    @SerializedName("collaborators") val collaborators: List<Collaborator>?,
    @SerializedName("status") val status: String,
    @SerializedName("content") val content: Content,
    @SerializedName("versionHistory") val versionHistory: List<VersionHistory>?,
    @SerializedName("tags") val tags: List<String>?,
    @SerializedName("createdAt") val createdAt: Date,
    @SerializedName("updatedAt") val updatedAt: Date
) : Serializable

data class Collaborator(
    @SerializedName("userId") val userId: Int,
    @SerializedName("role") val role: String
) : Serializable

data class Content(
    @SerializedName("destination") val destination: String,
    @SerializedName("startDate") val startDate: Date,
    @SerializedName("endDate") val endDate: Date,
    @SerializedName("days") val days: List<Day>,
    @SerializedName("transport") val transport: String?,
    @SerializedName("notes") val notes: String?
) : Serializable

data class Day(
    @SerializedName("day") val day: Int,
    @SerializedName("activities") val activities: List<Activity>
) : Serializable

data class Activity(
    @SerializedName("time") val time: String,
    @SerializedName("location_name") val location_name: String
) : Serializable

data class VersionHistory(
    @SerializedName("editorId") val editorId: Int,
    @SerializedName("editTime") val editTime: Date,
    @SerializedName("changeLog") val changeLog: String
) : Serializable