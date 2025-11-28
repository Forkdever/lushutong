package com.example.lushutong

data class Comment(
    val userId: Int,
    val username: String,
    val userAvatar: String,
    val content: String,
    val time: String,
    val likeCount: Int
)