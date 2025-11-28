package com.example.lushutong

import java.io.Serializable

// 帖子数据模型（必须实现Serializable用于页面间传递）
data class Post(
    val id: Int,
    val userId: Int,
    val username: String,
    val userAvatar: String,
    val publishTime: String,
    val location: String,
    val title: String,
    val content: String,
    val imageUrls: List<String>,
    val likeCount: Int,
    val commentCount: Int,
    val collectCount: Int,
    val shareCount: Int
) : Serializable